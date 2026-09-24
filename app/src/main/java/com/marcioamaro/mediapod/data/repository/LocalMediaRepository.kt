package com.marcioamaro.mediapod.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.marcioamaro.mediapod.data.model.LocalAudioTrack
import com.marcioamaro.mediapod.data.model.LocalVideoTrack
import com.marcioamaro.mediapod.data.model.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class LocalMediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Thread-safe cache for online album artwork: "artist - title" or "artist - album" -> artworkUrl
    private val artworkCache = ConcurrentHashMap<String, String>()

    suspend fun getAllAudioTracks(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalAudioTrack>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.DURATION} >= 10000" // Min 10s to skip notifications
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Música $id"
                    val artist = cursor.getString(artistCol) ?: "Artista Desconhecido"
                    val album = cursor.getString(albumCol) ?: "Álbum Desconhecido"
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val albumId = cursor.getLong(albumIdCol)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val folderName = try {
                        File(path).parentFile?.name ?: "Músicas"
                    } catch (_: Exception) {
                        "Músicas"
                    }

                    val embeddedArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    tracks.add(
                        LocalAudioTrack(
                            id = id,
                            title = title.trim(),
                            artist = if (artist.equals("<unknown>", true)) "Artista Desconhecido" else artist.trim(),
                            album = if (album.equals("<unknown>", true)) "Álbum Desconhecido" else album.trim(),
                            durationMs = duration,
                            contentUri = contentUri,
                            filePath = path,
                            folderName = folderName,
                            albumId = albumId,
                            albumArtUrl = embeddedArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        MediaLibraryRepository.getInstance(context).registerFiles(LibraryKind.AUDIO, tracks.map { track ->
            val file = File(track.filePath)
            MediaFileReference(track.libraryKey(), if (file.isFile) file.length() else -1, file.lastModified()) {
                requireNotNull(contentResolver.openInputStream(track.contentUri))
            }
        })
        tracks.sortedBy { it.title.trim().lowercase() }
    }

    suspend fun getAudioFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        val tracks = getAllAudioTracks()
        tracks.groupBy { File(it.filePath).parent.orEmpty() }
            .map { (_, items) ->
                val folderName = items.first().folderName
                val parentPath = items.firstOrNull()?.filePath?.let { File(it).parent ?: "" } ?: ""
                MediaFolder(
                    name = folderName,
                    path = parentPath,
                    itemCount = items.size,
                    isVideo = false
                )
            }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun getTracksByFolder(folderName: String, folderPath: String? = null): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
        getAllAudioTracks().filter { if (folderPath != null) File(it.filePath).parent == folderPath else it.folderName.equals(folderName, ignoreCase = true) }
            .sortedBy { it.title.trim().lowercase() }
    }

    suspend fun getAllVideoTracks(): List<LocalVideoTrack> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideoTrack>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.RESOLUTION else MediaStore.Video.Media._ID
        )

        val sortOrder = "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"

        try {
            contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val resCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Vídeo $id"
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val resolution = if (resCol >= 0) cursor.getString(resCol) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val folderName = try {
                        File(path).parentFile?.name ?: "Vídeos"
                    } catch (_: Exception) {
                        "Vídeos"
                    }

                    videos.add(
                        LocalVideoTrack(
                            id = id,
                            title = title.trim(),
                            durationMs = duration,
                            contentUri = contentUri,
                            filePath = path,
                            folderName = folderName,
                            resolution = resolution
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        MediaLibraryRepository.getInstance(context).registerFiles(LibraryKind.VIDEO, videos.map { track ->
            val file = File(track.filePath)
            MediaFileReference(track.libraryKey(), if (file.isFile) file.length() else -1, file.lastModified()) {
                requireNotNull(contentResolver.openInputStream(track.contentUri))
            }
        })
        videos.sortedBy { it.title.trim().lowercase() }
    }

    suspend fun getVideoFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        val videos = getAllVideoTracks()
        videos.groupBy { File(it.filePath).parent.orEmpty() }
            .map { (_, items) ->
                val folderName = items.first().folderName
                val parentPath = items.firstOrNull()?.filePath?.let { File(it).parent ?: "" } ?: ""
                MediaFolder(
                    name = folderName,
                    path = parentPath,
                    itemCount = items.size,
                    isVideo = true
                )
            }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun getVideosByFolder(folderName: String, folderPath: String? = null): List<LocalVideoTrack> = withContext(Dispatchers.IO) {
        getAllVideoTracks().filter { if (folderPath != null) File(it.filePath).parent == folderPath else it.folderName.equals(folderName, ignoreCase = true) }
            .sortedBy { it.title.trim().lowercase() }
    }

    /**
     * Attempts to find high-resolution album art from the web using iTunes Search API
     */
    suspend fun fetchOnlineAlbumArt(artist: String, title: String, album: String): String? = withContext(Dispatchers.IO) {
        if (!com.marcioamaro.mediapod.util.DataUsagePolicy(context).remoteArtwork) return@withContext null
        val cacheKey = "${artist.lowercase().trim()} - ${title.lowercase().trim()}"
        artworkCache[cacheKey]?.let { return@withContext it }

        // Clean query terms (remove "(Ao Vivo)", "[Official Video]", etc.)
        val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
        val cleanArtist = if (artist.contains("unknown", true) || artist.contains("desconhecido", true)) "" else artist.trim()
        val query = if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle" else cleanTitle

        if (query.length < 3) return@withContext null

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GlobalRadioPod/5.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val artwork100 = first.optString("artworkUrl100")
                    if (artwork100.isNotBlank()) {
                        // Request high quality 600x600 artwork
                        val highRes = artwork100.replace("100x100bb", "600x600bb")
                        artworkCache[cacheKey] = highRes
                        return@withContext highRes
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }
}
