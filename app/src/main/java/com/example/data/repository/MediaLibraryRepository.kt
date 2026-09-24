package com.example.data.repository

import android.content.Context
import com.example.data.model.LocalAudioTrack
import com.example.data.model.LocalVideoTrack
import com.example.data.model.MediaFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LibraryKind { AUDIO, VIDEO }
data class MediaPlaylist(val id: String, val name: String, val kind: LibraryKind, val keys: List<String> = emptyList())
data class MediaLibraryState(
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
    val playlists: List<MediaPlaylist> = emptyList()
)

fun LocalAudioTrack.libraryKey() = "AUDIO:" + filePath.ifBlank { contentUri.toString() }
fun LocalVideoTrack.libraryKey() = "VIDEO:" + filePath.ifBlank { contentUri.toString() }

/** Saves references, never media files or temporary MediaStore numeric IDs. */
class MediaLibraryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("media_library", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(decode(prefs.getString("library", "{}") ?: "{}"))
    val state = _state.asStateFlow()
    fun position(key: String): Long = prefs.getLong("position_$key", 0L)
    fun savePosition(key: String, position: Long, duration: Long) {
        val next = if (duration > 0 && position >= duration - 1000) 0L else position.coerceAtLeast(0)
        if (kotlin.math.abs(position(key) - next) >= 5000 || next == 0L) prefs.edit().putLong("position_$key", next).apply()
    }
    private val identityLock = Mutex()
    internal suspend fun <T> withStorageLock(block: suspend () -> T): T = identityLock.withLock { block() }
    private val inventory = java.util.concurrent.ConcurrentHashMap<String, MediaFileReference>()
    private val digestCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Pair<Long, Long>, String>>()
    private val captureRequests = Channel<Unit>(Channel.CONFLATED)
    private val identityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init { identityScope.launch { for (request in captureRequests) reconcileFiles() } }

    suspend fun registerFiles(kind: LibraryKind, files: List<MediaFileReference>) {
        inventory.keys.filter { it.startsWith("${kind.name}:") }.forEach(inventory::remove)
        files.forEach { inventory[it.key] = it }
        reconcileFiles()
    }

    private fun identityData(): JSONObject = runCatching {
        JSONObject(prefs.getString("identities", "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    private suspend fun reconcileFiles() = withContext(Dispatchers.IO) { identityLock.withLock {
        val current = _state.value
        val referenced = current.favorites + current.recents + current.playlists.flatMap { it.keys }
        val files = inventory.toMap()
        val identities = identityData()
        fun hash(file: MediaFileReference): String? = runCatching {
            val stamp = file.size to file.modified
            digestCache[file.key]?.takeIf { it.first == stamp }?.second ?: MediaFileIdentity.digest(file).also {
                digestCache[file.key] = stamp to it
            }
        }.getOrNull()
        referenced.forEach { key -> files[key]?.let { file ->
            hash(file)?.let { value -> identities.put(key, JSONObject().put("size", file.size).put("sha256", value)) }
        } }
        val replacements = mutableMapOf<String, String>()
        referenced.filterNot(files::containsKey).forEach { missing ->
            val identity = identities.optJSONObject(missing) ?: return@forEach
            val size = identity.optLong("size", -1)
            if (size < 0) return@forEach
            val kind = missing.substringBefore(':')
            val matches = files.values.filter { it.key.startsWith("$kind:") && it.size == size }
                .filter { hash(it) == identity.optString("sha256") }
            if (matches.size == 1) replacements[missing] = matches.single().key
        }
        // Several old references resolving to the same file are ambiguous too.
        val unique = replacements.filterValues { candidate -> replacements.values.count { it == candidate } == 1 }
        if (unique.isNotEmpty()) replaceReferences(unique)
        unique.forEach { (old, new) -> identities.put(new, identities.getJSONObject(old)); identities.remove(old) }
        prefs.edit().putString("identities", identities.toString()).apply()
    } }

    @Synchronized private fun replaceReferences(replacements: Map<String, String>) {
        val positions = prefs.edit()
        replacements.forEach { (old, new) -> positions.putLong("position_$new", position(old)).remove("position_$old") }
        positions.apply()
        fun replace(key: String) = replacements[key] ?: key
        val current = _state.value
        save(current.copy(favorites = current.favorites.map(::replace).toSet(),
            recents = current.recents.map(::replace).distinct(),
            playlists = current.playlists.map { it.copy(keys = it.keys.map(::replace).distinct()) }))
    }

    fun reloadFromStorage() { _state.value = decode(prefs.getString("library", "{}") ?: "{}") }

    fun folders(kind: LibraryKind, physical: List<MediaFolder>, keys: Set<String>): List<MediaFolder> {
        val current = _state.value
        val video = kind == LibraryKind.VIDEO
        return listOf(
            MediaFolder(if (video) "Todos os vídeos" else "Todas as músicas", "library:all", keys.size, video),
            MediaFolder("Favoritos", "library:favorites", current.favorites.count { it in keys }, video),
            MediaFolder("Recentes", "library:recents", current.recents.count { it in keys }, video)
        ) + current.playlists.filter { it.kind == kind }.map {
            MediaFolder("Playlist · ${it.name}", "playlist:${it.id}", it.keys.count { key -> key in keys }, video)
        } + physical
    }

    fun <T> select(path: String, items: List<T>, key: (T) -> String): List<T> {
        val current = _state.value
        val ordered = when {
            path == "library:favorites" -> return items.filter { key(it) in current.favorites }
            path == "library:recents" -> current.recents
            path.startsWith("playlist:") -> current.playlists.firstOrNull { it.id == path.removePrefix("playlist:") }?.keys.orEmpty()
            else -> return items
        }
        val indexed = items.associateBy(key)
        return ordered.mapNotNull(indexed::get)
    }

    @Synchronized private fun save(next: MediaLibraryState) {
        prefs.edit().putString("library", encode(next).toString()).apply()
        _state.value = next
        captureRequests.trySend(Unit)
    }

    @Synchronized fun toggleFavorite(key: String) {
        val current = _state.value
        save(current.copy(favorites = if (key in current.favorites) current.favorites - key else current.favorites + key))
    }

    @Synchronized fun recordRecent(key: String) {
        save(_state.value.copy(recents = (listOf(key) + _state.value.recents.filterNot { it == key }).take(200)))
    }

    @Synchronized fun clearRecents(kind: LibraryKind) {
        save(_state.value.copy(recents = _state.value.recents.filterNot { it.startsWith("${kind.name}:") }))
    }

    @Synchronized fun createPlaylist(name: String, kind: LibraryKind, initialKey: String? = null): String {
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "Digite um nome para a playlist" }
        require(_state.value.playlists.none { it.kind == kind && it.name.equals(clean, true) }) { "Já existe uma playlist com esse nome" }
        require(initialKey == null || initialKey.startsWith("${kind.name}:"))
        val playlist = MediaPlaylist(UUID.randomUUID().toString(), clean, kind, listOfNotNull(initialKey))
        save(_state.value.copy(playlists = _state.value.playlists + playlist))
        return playlist.id
    }

    @Synchronized fun renamePlaylist(id: String, name: String) {
        val clean = name.trim().take(80)
        val target = _state.value.playlists.firstOrNull { it.id == id } ?: return
        require(clean.isNotBlank()) { "Digite um nome para a playlist" }
        require(_state.value.playlists.none { it.id != id && it.kind == target.kind && it.name.equals(clean, true) }) { "Nome já utilizado" }
        save(_state.value.copy(playlists = _state.value.playlists.map { if (it.id == id) it.copy(name = clean) else it }))
    }

    @Synchronized fun deletePlaylist(id: String) = save(_state.value.copy(playlists = _state.value.playlists.filterNot { it.id == id }))

    @Synchronized fun addToPlaylist(id: String, key: String) = editPlaylist(id) {
        require(key.startsWith("${it.kind.name}:"))
        it.copy(keys = (it.keys + key).distinct())
    }

    @Synchronized fun removeFromPlaylist(id: String, key: String) = editPlaylist(id) { it.copy(keys = it.keys - key) }

    @Synchronized fun moveInPlaylist(id: String, key: String, delta: Int) = editPlaylist(id) {
        val keys = it.keys.toMutableList()
        val from = keys.indexOf(key)
        if (from >= 0) {
            val to = (from + delta).coerceIn(0, keys.lastIndex)
            keys.removeAt(from)
            keys.add(to, key)
        }
        it.copy(keys = keys)
    }

    private fun editPlaylist(id: String, change: (MediaPlaylist) -> MediaPlaylist) {
        save(_state.value.copy(playlists = _state.value.playlists.map { if (it.id == id) change(it) else it }))
    }

    fun exportJson(): JSONObject = encode(_state.value).put("identities", identityData()).put("positions", JSONObject().apply {
        prefs.all.filterKeys { it.startsWith("position_") }.forEach { (key, value) -> if (value is Long) put(key.removePrefix("position_"), value) }
    })

    @Synchronized fun restoreJson(json: JSONObject) {
        json.optJSONObject("positions")?.let { positions ->
            val edit = prefs.edit()
            positions.keys().forEach { key -> edit.putLong("position_$key", positions.getLong(key).coerceAtLeast(0)) }
            edit.apply()
        }
        json.optJSONObject("identities")?.let { restored ->
            val merged = identityData()
            restored.keys().forEach { merged.put(it, restored.get(it)) }
            prefs.edit().putString("identities", merged.toString()).apply()
        }
        val restored = decode(json.toString())
        val current = _state.value
        // Restore is idempotent and retains unrelated existing collections.
        save(MediaLibraryState(current.favorites + restored.favorites,
            (restored.recents + current.recents).distinct().take(200),
            restored.playlists + current.playlists.filterNot { old -> restored.playlists.any { it.id == old.id } }))
    }

    companion object {
        private fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { key -> key.startsWith("AUDIO:") || key.startsWith("VIDEO:") } }.distinct()

        internal fun encode(state: MediaLibraryState) = JSONObject().apply {
            put("favorites", JSONArray(state.favorites.toList()))
            put("recents", JSONArray(state.recents))
            put("playlists", JSONArray().apply { state.playlists.forEach { list ->
                put(JSONObject().put("id", list.id).put("name", list.name).put("kind", list.kind.name).put("keys", JSONArray(list.keys)))
            } })
        }

        internal fun decode(json: String): MediaLibraryState = try {
            val data = JSONObject(json)
            val playlists = data.optJSONArray("playlists") ?: JSONArray()
            MediaLibraryState(strings(data.optJSONArray("favorites")).toSet(), strings(data.optJSONArray("recents")).take(200),
                (0 until playlists.length()).mapNotNull { i ->
                    val item = playlists.getJSONObject(i)
                    val kind = runCatching { LibraryKind.valueOf(item.getString("kind")) }.getOrNull()
                    val id = item.optString("id")
                    val name = item.optString("name").trim().take(80)
                    if (kind == null || id.isBlank() || name.isBlank()) null else
                        MediaPlaylist(id, name, kind, strings(item.optJSONArray("keys")).filter { it.startsWith("${kind.name}:") })
                }.distinctBy { it.id })
        } catch (_: Exception) { MediaLibraryState() }

        @Volatile private var instance: MediaLibraryRepository? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: MediaLibraryRepository(context.applicationContext).also { instance = it }
        }
    }
}
