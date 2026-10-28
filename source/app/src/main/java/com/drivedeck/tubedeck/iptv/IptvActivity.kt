@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drivedeck.tubedeck.iptv

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.player.BackgroundAudioController
import com.drivedeck.tubedeck.player.PlaybackSessionRegistry
import com.drivedeck.tubedeck.ui.components.tvInputHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Isolated and lazy-loaded IPTV add-on; YouTube routes and preferences are untouched. */
class IptvActivity : ComponentActivity() {
    private var channelActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IptvStudio(
                onClose = { finish() },
                onChannelActive = { channelActive = it }
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (channelActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }
}

private fun asColor(value: Long): Color = Color(value.toULong())
private fun saveCatalog(context: android.content.Context, channels: List<IptvChannel>) {
    val contents = JSONArray()
    for (channel in channels.take(8000)) {
        contents.put(JSONObject()
            .put("name", channel.name).put("url", channel.url)
            .put("group", channel.group).put("logo", channel.logo).put("id", channel.tvgId))
    }
    val file = File(context.filesDir, "iptv_user_playlist.json")
    val temp = File(context.filesDir, "iptv_user_playlist.json.tmp")
    temp.writeText(contents.toString())
    if (!temp.renameTo(file)) {
        temp.delete()
        error("Could not save playlist")
    }
}
private fun restoreCatalog(context: android.content.Context): List<IptvChannel> = runCatching {
    val file = File(context.filesDir, "iptv_user_playlist.json")
    if (!file.exists() || file.length() > 6_000_000) return@runCatching emptyList()
    val root = JSONArray(file.readText())
    (0 until minOf(root.length(), 8000)).mapNotNull { index ->
        val o = root.optJSONObject(index) ?: return@mapNotNull null
        val url = o.optString("url")
        if (IptvCatalog.resolveStream("", url) == null) return@mapNotNull null
        IptvChannel(o.optString("name"), url, o.optString("group"), o.optString("logo"), o.optString("id"))
    }
}.getOrElse { emptyList() }

@Composable
private fun IptvStudio(onClose: () -> Unit, onChannelActive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mytube_iptv_appearance_v1", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var themeName by rememberSaveable { mutableStateOf(prefs.getString("theme", "Ocean Glass").orEmpty()) }
    var wallpaperName by rememberSaveable { mutableStateOf(prefs.getString("wallpaper", "Waves").orEmpty()) }
    var photoUri by rememberSaveable { mutableStateOf(prefs.getString("photo", "").orEmpty()) }
    var dim by rememberSaveable { mutableFloatStateOf(prefs.getFloat("dim", 0.32f)) }
    var fontScale by rememberSaveable { mutableFloatStateOf(prefs.getFloat("font", 1f)) }
    var playlist by remember { mutableStateOf(restoreCatalog(context)) }
    var selected by remember { mutableStateOf<IptvChannel?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("All") }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Add your authorized M3U playlist or IPTV provider. No channels are bundled.") }
    val palette = IptvPalettes.byName(themeName)
    val accent = asColor(palette.accent)
    val foreground = asColor(palette.foreground)
    fun useChannels(channels: List<IptvChannel>, persist: Boolean) {
        playlist = channels
        selected = null
        group = "All"
        message = if (channels.isEmpty()) "The source contained no supported live channels." else
            channels.size.toString() + " live channels loaded."
        if (persist) {
            runCatching { saveCatalog(context, channels) }
                .onFailure { message = "Loaded channels, but local save failed: " + (it.message ?: "") }
        }
    }
    fun fetchSource(url: String, xtream: Boolean) {
        if (busy) return
        busy = true
        message = "Loading your provider…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = IptvCatalog.downloadText(url)
                    if (xtream) IptvCatalog.parseXtreamLive(text, provider, username, password)
                    else IptvCatalog.parseM3u(text, url)
                }
            }
            busy = false
            result.onSuccess { useChannels(it, !xtream) }
                .onFailure { message = "Unable to import source: " + (it.message ?: "Unknown error") }
        }
    }

    val playlistFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(16_384)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 4_000_000) { "File exceeds 4 MB" }
                                output.write(buffer, 0, count)
                            }
                            IptvCatalog.parseM3u(output.toString(Charsets.UTF_8.name()))
                        } ?: error("Could not open playlist file")
                    }
                }
                busy = false
                result.onSuccess { useChannels(it, true) }
                    .onFailure { message = "File import failed: " + (it.message ?: "") }
            }
        }
    }
    val wallpaperFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            photoUri = uri.toString()
            wallpaperName = "My photo"
            prefs.edit().putString("photo", photoUri).putString("wallpaper", wallpaperName).apply()
        }
    }

    val filtered = remember(playlist, query, group) {
        playlist.filter { c ->
            (group == "All" || c.group == group) &&
                (query.isBlank() || c.name.contains(query, true) || c.group.contains(query, true))
        }
    }
    val groups = remember(playlist) { listOf("All") + playlist.map { it.group }.distinct().sorted() }

    Box(Modifier.fillMaxSize().background(asColor(palette.background))) {
        IptvWallpaper(palette, wallpaperName, photoUri, dim)
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val wide = maxWidth > 700.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("‹ MyTube", color = accent) }
                    Spacer(Modifier.weight(1f))
                    Text("IPTV Extreme", color = foreground, fontSize = (19 * fontScale).sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSources = !showSources; showAppearance = false }) {
                        Text("Sources", color = accent)
                    }
                    TextButton(onClick = { showAppearance = !showAppearance; showSources = false }) {
                        Text("Themes", color = accent)
                    }
                }
                if (showAppearance) {
                    IptvAppearance(
                        palette, themeName, wallpaperName, fontScale, dim, foreground,
                        onTheme = {
                            themeName = it.name
                            wallpaperName = it.wallpaper
                            prefs.edit().putString("theme", it.name).putString("wallpaper", wallpaperName).apply()
                        },
                        onWallpaper = {
                            wallpaperName = it
                            prefs.edit().putString("wallpaper", it).apply()
                        },
                        onPhoto = { wallpaperFile.launch(arrayOf("image/*")) },
                        onFont = { fontScale = it; prefs.edit().putFloat("font", it).apply() },
                        onDim = { dim = it; prefs.edit().putFloat("dim", it).apply() }
                    )
                } else if (showSources) {
                    IptvSources(
                        source, provider, username, password, busy, foreground, accent,
                        onSource = { source = it }, onProvider = { provider = it },
                        onUser = { username = it }, onPassword = { password = it },
                        onFile = { playlistFile.launch(arrayOf("*/*")) },
                        onM3u = { fetchSource(source, false) },
                        onXtream = {
                            runCatching { IptvCatalog.xtreamApi(provider, username, password) }
                                .onSuccess { fetchSource(it, true) }
                                .onFailure { message = it.message ?: "Invalid provider details" }
                        },
                        onClear = {
                            useChannels(emptyList(), true)
                            selected = null
                        }
                    )
                }
                Text(message, color = foreground.copy(alpha = .82f), fontSize = 12.sp,
                    maxLines = 2, modifier = Modifier.padding(vertical = 5.dp))

                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1.2f)) {
                            IptvVideo(selected, palette, onChannelActive)
                            Text(selected?.name ?: "Choose a channel to begin", color = foreground,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            IptvChannelBrowser(query, group, groups, filtered, foreground, accent,
                                fontScale, { query = it }, { group = it }, { selected = it })
                        }
                    }
                } else {
                    IptvVideo(selected, palette, onChannelActive)
                    Spacer(Modifier.height(6.dp))
                    IptvChannelBrowser(query, group, groups, filtered, foreground, accent,
                        fontScale, { query = it }, { group = it }, { selected = it })
                }
            }
        }
    }
}

@Composable
private fun IptvVideo(channel: IptvChannel?, palette: IptvPalette, onChannelActive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    var playbackError by remember { mutableStateOf("") }
    var ownedSession by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = "This stream could not play: " + error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            ownedSession?.let { PlaybackSessionRegistry.closeOwned(it, player) }
            player.release()
            onChannelActive(false)
        }
    }
    LaunchedEffect(channel?.url) {
        if (channel == null) {
            player.stop()
            onChannelActive(false)
        } else {
            BackgroundAudioController.stop(context.applicationContext)
            PlaybackSessionRegistry.stopForWeb()
            val session = PlaybackSessionRegistry.open()
            PlaybackSessionRegistry.claim(session, player) { player.stop() }
            ownedSession = session
            playbackError = ""
            val path = Uri.parse(channel.url).path.orEmpty().lowercase()
            val mime = when {
                channel.url.startsWith("rtsp:", true) -> MimeTypes.APPLICATION_RTSP
                path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            val item = MediaItem.Builder().setUri(channel.url)
                .apply { if (mime != null) setMimeType(mime) }
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
            onChannelActive(true)
        }
    }
    Column {
        Box(
            Modifier.fillMaxWidth().heightIn(max = 370.dp).aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp)).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (channel != null) {
                AndroidView(factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        this.player = player
                        keepScreenOn = true
                    }
                }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
            } else {
                Text("IPTV · LIVE TV", color = Color.LightGray, fontSize = 19.sp)
            }
        }
        if (playbackError.isNotBlank()) {
            Text(playbackError, color = Color(0xFFFFB0A6), fontSize = 12.sp, modifier = Modifier.padding(5.dp))
        }
    }
}

@Composable
private fun IptvChannelBrowser(
    query: String, group: String, groups: List<String>, channels: List<IptvChannel>,
    foreground: Color, accent: Color, fontScale: Float,
    onQuery: (String) -> Unit, onGroup: (String) -> Unit, onSelect: (IptvChannel) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    OutlinedTextField(value = query, onValueChange = onQuery, label = { Text("Find channels") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = foreground, unfocusedTextColor = foreground,
            focusedBorderColor = accent, focusedLabelColor = accent))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menu = true }) {
                Text("Category: " + group + " ▾", color = accent, maxLines = 1)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                groups.take(120).forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = { onGroup(item); menu = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(channels.size.toString() + " channels", color = foreground, fontSize = 12.sp)
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(channels) { channel ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = .12f))
                    .clickable { onSelect(channel) }
                    .tvInputHighlight(RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel.logo.isNotBlank()) {
                    AsyncImage(model = channel.logo, contentDescription = null,
                        modifier = Modifier.size(34.dp).padding(end = 5.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(channel.name, color = foreground, fontSize = (14f * fontScale).sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(channel.group, color = foreground.copy(alpha = .68f), fontSize = 11.sp,
                        maxLines = 1)
                }
                Text("▶", color = accent)
            }
        }
    }
}

@Composable
private fun IptvSources(
    source: String, provider: String, username: String, password: String, busy: Boolean,
    foreground: Color, accent: Color, onSource: (String) -> Unit, onProvider: (String) -> Unit,
    onUser: (String) -> Unit, onPassword: (String) -> Unit, onFile: () -> Unit,
    onM3u: () -> Unit, onXtream: () -> Unit, onClear: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .33f), RoundedCornerShape(16.dp))
            .padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("Your IPTV sources", color = foreground, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = source, onValueChange = onSource, label = { Text("M3U playlist URL") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onM3u, enabled = source.isNotBlank() && !busy) { Text("Load M3U URL") }
            OutlinedButton(onClick = onFile, enabled = !busy) { Text("Import file") }
        }
        OutlinedTextField(value = provider, onValueChange = onProvider,
            label = { Text("Xtream provider server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = username, onValueChange = onUser,
                label = { Text("Username") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = password, onValueChange = onPassword,
                label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onXtream, enabled = provider.isNotBlank() && username.isNotBlank() &&
                password.isNotBlank() && !busy) { Text("Connect Xtream") }
            TextButton(onClick = onClear) { Text("Clear saved M3U", color = accent) }
        }
        Text("Only connect sources you are authorized to use. HTTPS is preferred; HTTP streams are unencrypted. Provider login is not stored.",
            color = foreground.copy(alpha = .8f), fontSize = 11.sp)
    }
}

@Composable
private fun IptvAppearance(
    current: IptvPalette, theme: String, wallpaper: String, font: Float, dim: Float,
    foreground: Color, onTheme: (IptvPalette) -> Unit, onWallpaper: (String) -> Unit,
    onPhoto: () -> Unit, onFont: (Float) -> Unit, onDim: (Float) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .30f), RoundedCornerShape(14.dp))
            .padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("10 premium presets · live preview", color = foreground, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(IptvPalettes.all) { p ->
                Column(Modifier.width(112.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(asColor(p.background), asColor(p.accent))))
                    .border(if (theme == p.name) 2.dp else 1.dp,
                        if (theme == p.name) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onTheme(p) }.padding(10.dp)) {
                    Text(p.name, color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(if (theme == p.name) "✓ Selected" else "Select", color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Waves", "Aurora", "Glow", "Grid", "Spotlight", "None").forEach { name ->
                Text(name, color = if (wallpaper == name) asColor(current.accent) else foreground,
                    fontSize = 11.sp, modifier = Modifier.clickable { onWallpaper(name) }.padding(3.dp))
            }
        }
        TextButton(onClick = onPhoto) { Text("Choose my own photo wallpaper", color = asColor(current.accent)) }
        Text("Wallpaper darkness " + (dim * 100).toInt() + "%", color = foreground, fontSize = 11.sp)
        Slider(value = dim, onValueChange = onDim, valueRange = 0f..0.8f)
        Text("Text size " + (font * 100).toInt() + "%", color = foreground, fontSize = 11.sp)
        Slider(value = font, onValueChange = onFont, valueRange = 0.85f..1.6f)
    }
}

@Composable
private fun IptvWallpaper(theme: IptvPalette, pattern: String, photoUri: String, dim: Float) {
    Box(Modifier.fillMaxSize()) {
        if (pattern == "My photo" && photoUri.isNotBlank()) {
            AsyncImage(model = Uri.parse(photoUri), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val primary = asColor(theme.accent)
                val surface = asColor(theme.surface)
                drawRect(Brush.linearGradient(listOf(asColor(theme.background), surface)))
                when (pattern) {
                    "Waves" -> repeat(8) { n ->
                        drawCircle(primary.copy(alpha = .12f), size.minDimension * (0.22f + n * .12f),
                            center = androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * .9f),
                            style = Stroke(width = 3f))
                    }
                    "Aurora" -> repeat(5) { n ->
                        drawCircle(primary.copy(alpha = .055f), size.minDimension * (.23f + n * .11f),
                            center = androidx.compose.ui.geometry.Offset(size.width * .15f, size.height * .25f))
                    }
                    "Grid" -> {
                        val step = 54.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawLine(primary.copy(alpha = .12f), androidx.compose.ui.geometry.Offset(x, 0f),
                                androidx.compose.ui.geometry.Offset(x, size.height))
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(primary.copy(alpha = .12f), androidx.compose.ui.geometry.Offset(0f, y),
                                androidx.compose.ui.geometry.Offset(size.width, y))
                            y += step
                        }
                    }
                    "Glow", "Spotlight" -> repeat(7) { n ->
                        drawCircle(primary.copy(alpha = .035f), size.minDimension * (0.17f + .07f * n),
                            center = androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .2f))
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}
