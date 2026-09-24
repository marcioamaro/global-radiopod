package com.marcioamaro.mediapod.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.data.model.*
import com.marcioamaro.mediapod.data.repository.*
import com.marcioamaro.mediapod.ui.IpodScreenDestination
import com.marcioamaro.mediapod.ui.RadioViewModel
import com.marcioamaro.mediapod.ui.components.*

@Composable
fun PersonalLibraryScreen(
    viewModel: RadioViewModel,
    background: Color,
    primary: Color,
    secondary: Color,
    highlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val context = LocalContext.current
    val podcasts = viewModel.podcastRepo
    val media = remember(context) { MediaLibraryRepository.getInstance(context) }
    val bookmarks = remember(context) { EpisodeBookmarks(context) }
    val notes by bookmarks.items.collectAsState()
    val recents by podcasts.recentEpisodesFlow.collectAsState()
    val subscriptions by podcasts.subscriptionsFlow.collectAsState()
    val played by podcasts.playedEpisodeIds.collectAsState()
    val library by media.state.collectAsState()
    var audio by remember { mutableStateOf<List<LocalAudioTrack>>(emptyList()) }
    var video by remember { mutableStateOf<List<LocalVideoTrack>>(emptyList()) }
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<EpisodeBookmark?>(null) }
    var loading by remember { mutableStateOf(true) }
    val preferences = remember { context.getSharedPreferences("podcast_preferences", android.content.Context.MODE_PRIVATE) }
    val downloads = remember { com.marcioamaro.mediapod.data.download.PodcastDownloadManager.getInstance(context) }
    val downloadStates by downloads.statusMap.collectAsState()
    val favorites by podcasts.favoriteShowsFlow.collectAsState()
    val recentShows by podcasts.recentShowsFlow.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(runCatching { LibrarySuggestions.Mode.valueOf(preferences.getString("smart_mode", "UNPLAYED")!!) }.getOrDefault(LibrarySuggestions.Mode.UNPLAYED)) }
    var maxMinutes by remember { mutableIntStateOf(preferences.getInt("smart_minutes", 20).coerceIn(5, 120)) }
    var limit by remember { mutableIntStateOf(preferences.getInt("smart_limit", 20).coerceIn(1, 100)) }
    var discovery by remember { mutableStateOf(preferences.getBoolean("discovery_enabled", false)) }
    var useHistory by remember { mutableStateOf(preferences.getBoolean("discovery_history", false)) }
    var interests by remember { mutableStateOf(preferences.getString("discovery_interests", "").orEmpty()) }
    val downloaded = remember(downloadStates) { downloads.getDownloadedEpisodes() }
    val smart = LibrarySuggestions.playlist((if (mode == LibrarySuggestions.Mode.RECENT_FAVORITES) recents else uiState.podcastEpisodes + recents) + downloaded,
        mode, played, downloaded.map { it.id }.toSet(), favorites.map { it.id }.toSet(), maxMinutes, limit)
    val suggestions = remember(discovery, interests, useHistory, recentShows, favorites, subscriptions) {
        LibrarySuggestions.discover(podcasts.getCuratedShows(), discovery, interests, recentShows, useHistory,
            (favorites + subscriptions).map { it.id }.toSet())
    }
    LaunchedEffect(Unit) {
        val local = LocalMediaRepository(context)
        audio = local.getAllAudioTracks()
        video = local.getAllVideoTracks()
        loading = false
    }
    fun playEpisode(episode: PodcastEpisode, position: Long? = null, queue: List<PodcastEpisode> = emptyList()) {
        position?.let { podcasts.savePlaybackPosition(episode.id, it) }
        val show = podcasts.searchCatalog("").firstOrNull { it.id == episode.showId }
            ?: PodcastShow(episode.showId, episode.showTitle, "", "", "", "")
        viewModel.playPodcastEpisode(episode, show, queue)
        viewModel.navigateTo(IpodScreenDestination.PODCAST_NOW_PLAYING)
    }
    LazyColumn(Modifier.fillMaxSize().background(background).border(1.dp, highlight.copy(alpha = 0.45f), RoundedCornerShape(6.dp)).padding(8.dp)) {
        item {
            Text("MINHA BIBLIOTECA", color = primary, fontFamily = fontFamily, fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold, fontSize = (13f * fontScale).sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(context.getString(com.marcioamaro.mediapod.R.string.library_continue), context.getString(com.marcioamaro.mediapod.R.string.library_bookmarks), context.getString(com.marcioamaro.mediapod.R.string.library_subscriptions), context.getString(com.marcioamaro.mediapod.R.string.library_smart), context.getString(com.marcioamaro.mediapod.R.string.library_discover)).forEachIndexed { index, label ->
                val selected = tab == index
                Text(label.uppercase(), color = if (selected) background else primary, fontFamily = fontFamily, fontWeight = if (selected || isBold) FontWeight.Bold else FontWeight.Normal, fontSize = (10f * fontScale).sp,
                    modifier = Modifier.heightIn(min = 44.dp).background(if (selected) highlight else primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp)).border(1.dp, if (selected) highlight else secondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).clickable { tab = index }.padding(horizontal = 10.dp, vertical = 12.dp))
            }
            }
        }
        if (tab == 0) {
            val episodes = recents.filter { it.id !in played && podcasts.getSavedPlaybackPosition(it.id) > 0 }
            val audioByKey = audio.associateBy { it.libraryKey() }
            val videoByKey = video.associateBy { it.libraryKey() }
            item { Text(if (loading) context.getString(com.marcioamaro.mediapod.R.string.library_loading) else context.getString(com.marcioamaro.mediapod.R.string.library_resume_help)) }
            items(episodes, key = { "podcast:${it.id}" }) { episode ->
                TextButton(onClick = { playEpisode(episode) }) { Text("Podcast · ${episode.title}") }
            }
            items(library.recents.filter { media.position(it) > 0 }, key = { it }) { key ->
                audioByKey[key]?.let { track ->
                    TextButton(onClick = { viewModel.playLocalAudio(track, audio); viewModel.navigateTo(IpodScreenDestination.MP3_NOW_PLAYING) }) { Text("${context.getString(com.marcioamaro.mediapod.R.string.menu_mp3)} · ${track.title}") }
                }
                videoByKey[key]?.let { track ->
                    TextButton(onClick = { viewModel.playLocalVideo(track); viewModel.navigateTo(IpodScreenDestination.VIDEO_PLAYER) }) { Text("${context.getString(com.marcioamaro.mediapod.R.string.menu_video)} · ${track.title}") }
                }
            }
        } else if (tab == 1) {
            if (notes.isEmpty()) item { Text(context.getString(com.marcioamaro.mediapod.R.string.bookmark_empty)) }
            items(notes, key = { it.id }) { note ->
                TextButton(onClick = { playEpisode(note.episode, note.positionMs) }) { Text("${note.episode.title} · ${note.positionMs / 1000}s\n${note.note}") }
                Row {
                    TextButton(onClick = { editing = note }) { Text(context.getString(com.marcioamaro.mediapod.R.string.feature_edit)) }
                    TextButton(onClick = { bookmarks.delete(note.id) }) { Text(context.getString(com.marcioamaro.mediapod.R.string.feature_delete)) }
                }
            }
        } else if (tab == 2) {
            item { OpmlControls(podcasts) }
            items(subscriptions, key = { it.id }) { show ->
                TextButton(onClick = { if (viewModel.selectPodcastShow(show)) viewModel.navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST) }) { Text(show.title) }
                TextButton(onClick = { podcasts.unsubscribe(show.id) }) { Text(context.getString(com.marcioamaro.mediapod.R.string.subscription_remove)) }
            }
        } else if (tab == 3) {
            item {
                Text(context.getString(com.marcioamaro.mediapod.R.string.smart_scope))
                LibrarySuggestions.Mode.entries.zip(listOf(context.getString(com.marcioamaro.mediapod.R.string.smart_unplayed), context.getString(com.marcioamaro.mediapod.R.string.smart_downloaded), context.getString(com.marcioamaro.mediapod.R.string.smart_favorites), context.getString(com.marcioamaro.mediapod.R.string.smart_short))).forEach { (value, label) ->
                    TextButton(onClick = { mode = value; preferences.edit().putString("smart_mode", value.name).apply() }) { Text(if (mode == value) "• $label" else label) }
                }
                Text(context.getString(com.marcioamaro.mediapod.R.string.smart_duration, maxMinutes))
                Slider(value = maxMinutes.toFloat(), onValueChange = { maxMinutes = it.toInt() }, valueRange = 5f..120f,
                    onValueChangeFinished = { preferences.edit().putInt("smart_minutes", maxMinutes).apply() })
                Text(context.getString(com.marcioamaro.mediapod.R.string.smart_limit, limit))
                Slider(value = limit.toFloat(), onValueChange = { limit = it.toInt() }, valueRange = 1f..100f,
                    onValueChangeFinished = { preferences.edit().putInt("smart_limit", limit).apply() })
                TextButton(enabled = smart.isNotEmpty(), onClick = { playEpisode(smart.first(), queue = smart) }) { Text(context.getString(com.marcioamaro.mediapod.R.string.smart_play)) }
            }
            items(smart, key = { it.id }) { episode -> TextButton(onClick = { playEpisode(episode, queue = smart) }) { Text(episode.title) } }
        } else {
            item {
                PreferenceToggle(context.getString(com.marcioamaro.mediapod.R.string.discovery_enable), discovery) { discovery = it; preferences.edit().putBoolean("discovery_enabled", it).apply() }
                PreferenceToggle(context.getString(com.marcioamaro.mediapod.R.string.discovery_history), useHistory) { useHistory = it; preferences.edit().putBoolean("discovery_history", it).apply() }
                OutlinedTextField(value = interests, onValueChange = { interests = it.take(300); preferences.edit().putString("discovery_interests", interests).apply() }, label = { Text(context.getString(com.marcioamaro.mediapod.R.string.discovery_interests)) })
                Text(context.getString(com.marcioamaro.mediapod.R.string.discovery_privacy))
                TextButton(onClick = {
                    discovery = false; useHistory = false; interests = ""
                    preferences.edit().remove("discovery_enabled").remove("discovery_history").remove("discovery_interests").apply()
                }) { Text(context.getString(com.marcioamaro.mediapod.R.string.discovery_clear)) }
            }
            items(suggestions, key = { it.id }) { show ->
                TextButton(onClick = { if (viewModel.selectPodcastShow(show)) viewModel.navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST) }) { Text(show.title) }
            }
        }
    }
    editing?.let { note -> BookmarkEditor(note.positionMs, note.note, { editing = null }) { time, text ->
        bookmarks.save(note.episode, time, text, note.id); editing = null
    } }
}
