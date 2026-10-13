package com.dissolution.editor

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

private val Ink = Color(0xFF101312)
private val Panel = Color(0xFF1A201D)
private val Line = Color(0xFF35403A)
private val Paper = Color(0xFFEAE9DD)
private val Muted = Color(0xFF9BA59D)
private val Acid = Color(0xFFC3F36B)

@Composable
private fun ResponsivePanes(
    modifier: Modifier = Modifier,
    wide: Boolean,
    editor: @Composable ColumnScope.() -> Unit,
    assistant: @Composable ColumnScope.() -> Unit,
) {
    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.45f).fillMaxHeight(), content = editor)
            Column(Modifier.weight(1f).fillMaxHeight(), content = assistant)
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.35f).fillMaxWidth().verticalScroll(rememberScrollState()), content = editor)
            Column(Modifier.weight(1f).fillMaxWidth(), content = assistant)
        }
    }
}

class MainActivity : ComponentActivity() {
    private var onVideoPicked: (Uri) -> Unit = {}
    private lateinit var videoPicker: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onVideoPicked(uri)
            }
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    DissolutionApp(
                        importVideo = { videoPicker.launch(arrayOf("video/*")) },
                        openFadCam = { openFadCam() },
                    )
                }
            }
        }
    }

    private fun openFadCam() {
        val intent = packageManager.getLaunchIntentForPackage("com.fadcam")
        if (intent == null) {
            Toast.makeText(this, "Install FadCam to record a source clip", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(intent)
        }
    }

    @Composable
    private fun DissolutionApp(importVideo: () -> Unit, openFadCam: () -> Unit) {
        val context = LocalContext.current
        var pickedVideo by remember { mutableStateOf<File?>(null) }
        var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
        var sourceDurationMs by remember { mutableLongStateOf(0L) }
        var importing by remember { mutableStateOf(false) }
        var trimRange by remember { mutableStateOf(0f..0f) }
        var resolution by remember { mutableStateOf("source") }
        var exportFrameRate by remember { mutableIntStateOf(30) }
        var bitrateMbps by remember { mutableIntStateOf(25) }
        var useHevc by remember { mutableStateOf(false) }
        val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
        var prompt by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var exportState by remember { mutableStateOf("READY") }
        var activeTransformer by remember { mutableStateOf<Transformer?>(null) }
        val scope = rememberCoroutineScope()
        var aiSettings by remember { mutableStateOf(AiSettingsStore.load(context)) }
        var showAiSettings by remember { mutableStateOf(false) }
        var endpointInput by remember { mutableStateOf(aiSettings.baseUrl) }
        var modelInput by remember { mutableStateOf(aiSettings.model) }
        var apiKeyInput by remember { mutableStateOf(aiSettings.apiKey) }

        val outputSize = outputSize(videoInfo, resolution)
        val outputMimeType = VideoCapabilities.mimeType(useHevc)
        val requestedBitrate = bitrateMbps * 1_000_000
        val outputWidth = outputSize?.first ?: videoInfo?.width ?: 0
        val outputHeight = outputSize?.second ?: videoInfo?.height ?: 0
        val probeConfig = videoInfo?.let {
            EncoderProbeConfig(outputMimeType, outputWidth, outputHeight, it.frameRate, requestedBitrate)
        }
        var supportedFrameRates by remember { mutableStateOf(emptySet<Int>()) }
        var checkedProbeConfig by remember { mutableStateOf<EncoderProbeConfig?>(null) }
        LaunchedEffect(videoInfo, outputWidth, outputHeight, requestedBitrate, outputMimeType) {
            checkedProbeConfig = null
            delay(180)
            val info = videoInfo
            supportedFrameRates = if (info == null) {
                emptySet()
            } else {
                withContext(Dispatchers.Default) {
                    listOf(30, 60, 120).filter { rate ->
                        rate <= info.frameRate && VideoCapabilities.supports(
                            outputMimeType, outputWidth, outputHeight, rate, requestedBitrate,
                        )
                    }.toSet()
                }
            }
            checkedProbeConfig = probeConfig
        }
        val canExportProfile = probeConfig != null && probeConfig == checkedProbeConfig && exportFrameRate in supportedFrameRates

        onVideoPicked = { uri ->
            importing = true
            scope.launch(Dispatchers.IO) {
                val copy = File(cacheDir, "source_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use(input::copyTo) }
                val info = runCatching { VideoCapabilities.inspect(copy) }.getOrNull()
                launch(Dispatchers.Main) {
                    pickedVideo = copy
                    videoInfo = info
                    sourceDurationMs = info?.durationMs ?: 0L
                    trimRange = 0f..(info?.durationMs?.toFloat() ?: 0f)
                    exportFrameRate = minOf(info?.frameRate ?: 30, 30)
                    resolution = "source"
                    importing = false
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("DISSOLUTION", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("AI VIDEO WORKSPACE  /  01", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = {
                    endpointInput = aiSettings.baseUrl
                    modelInput = aiSettings.model
                    apiKeyInput = aiSettings.apiKey
                    showAiSettings = true
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "BossAI settings", tint = Paper)
                }
                Text("LOCAL PROJECT", color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("IMPORT CLIP", Icons.Default.Add, importVideo, Modifier.weight(1f))
                ActionButton("FADCam", Icons.Default.VideoCameraBack, openFadCam, Modifier.weight(1f), secondary = true)
            }
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(Modifier.weight(1f)) {
                ResponsivePanes(
                    modifier = Modifier.fillMaxSize(),
                    wide = maxWidth >= 840.dp,
                    editor = {
                    Text("01 / PREVIEW", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    VideoPreview(
                        pickedVideo,
                        importing,
                        Modifier.fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .aspectRatio(
                                videoInfo?.let { it.width.toFloat() / it.height } ?: (16f / 9f),
                                matchHeightConstraintsFirst = true,
                            ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = Acid, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(pickedVideo?.name ?: "No clip loaded", color = Paper, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(videoInfo?.let { "${it.width}x${it.height} ${it.frameRate}fps" } ?: exportState, color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(10.dp))
                    TimelineStrip(
                        durationMs = sourceDurationMs,
                        trimRange = trimRange,
                        enabled = videoInfo != null,
                        onTrimChange = { trimRange = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("OUTPUT PROFILE", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("source" to "SOURCE", "1080p" to "1080P", "4k" to "4K").forEach { (value, label) ->
                            FilterChip(
                                selected = resolution == value,
                                onClick = { resolution = value },
                                enabled = videoInfo?.let { profileAvailable(it, value) } ?: false,
                                label = { Text(label, fontSize = 10.sp) },
                            )
                        }
                        FilterChip(
                            selected = useHevc,
                            onClick = { useHevc = !useHevc },
                            enabled = videoInfo != null,
                            label = { Text(if (useHevc) "HEVC" else "H.264", fontSize = 10.sp) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        (listOfNotNull(videoInfo?.frameRate) + listOf(30, 60, 120))
                            .distinct()
                            .filter { rate -> rate <= (videoInfo?.frameRate ?: 0) }
                            .sorted()
                            .forEach { rate ->
                            FilterChip(
                                selected = exportFrameRate == rate,
                                onClick = { exportFrameRate = rate },
                                enabled = rate in supportedFrameRates,
                                label = { Text("${rate}fps", fontSize = 10.sp) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${bitrateMbps} Mbps", color = Paper, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = bitrateMbps.toFloat(),
                            onValueChange = { bitrateMbps = it.roundToInt().coerceIn(1, 150) },
                            valueRange = 1f..150f,
                            steps = 148,
                            enabled = videoInfo != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ActionButton("EXPORT ${if (useHevc) "HEVC" else "H.264"}", Icons.Default.PlayArrow, {
                        val source = pickedVideo
                        if (source == null) {
                            exportState = "IMPORT A CLIP"
                        } else if (sourceDurationMs <= 0L) {
                            exportState = "INVALID SOURCE"
                        } else if (!canExportProfile) {
                            exportState = "PROFILE UNSUPPORTED"
                        } else {
                            busy = true
                            exportState = "ENCODING"
                            activeTransformer = MediaExport.export(
                                context = context,
                                source = source,
                                startMs = trimRange.start.toLong(),
                                endMs = trimRange.endInclusive.toLong(),
                                outputSize = outputSize,
                                videoMimeType = outputMimeType,
                                sourceFrameRate = videoInfo?.frameRate ?: 30,
                                targetFrameRate = exportFrameRate,
                                targetBitrate = requestedBitrate,
                            ) { result ->
                                scope.launch {
                                    val message = result.fold(
                                        onSuccess = { exported ->
                                            runCatching { MediaExport.publishToMovies(context, exported) }
                                                .fold(
                                                    onSuccess = { "Saved to Movies/Dissolution Editor" },
                                                    onFailure = { it.message ?: "Could not publish video to Movies" },
                                                )
                                        },
                                        onFailure = { it.message ?: "Video export failed" },
                                    )
                                    busy = false
                                    activeTransformer = null
                                    exportState = if (result.isSuccess && message.startsWith("Saved")) "EXPORTED" else "EXPORT FAILED"
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, Modifier.fillMaxWidth(), secondary = true, enabled = !busy && canExportProfile)
                    AnimatedVisibility(visible = activeTransformer != null) {
                        Row(
                            Modifier.fillMaxWidth().animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LinearProgressIndicator(Modifier.weight(1f), color = Acid)
                            Text("RENDERING", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            IconButton(onClick = {
                                activeTransformer?.cancel()
                                exportState = "CANCELLING"
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel export", tint = Paper)
                            }
                        }
                    }
                    },
                    assistant = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Acid, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("02 / ASSISTANT", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                Text("Ask for an edit, or ask me to find a clip. Video downloads open in Seal for your review.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                        items(messages) { (user, text) ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
                            ) {
                                Column(
                                    Modifier.widthIn(max = 320.dp)
                                        .background(if (user) Color(0xFF303B33) else Color(0xFF222A25), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 11.dp, vertical = 8.dp),
                                ) {
                                    Text(if (user) "YOU" else "BOSS AI", color = if (user) Muted else Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Spacer(Modifier.height(3.dp))
                                    Text(text, color = Paper, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Paper),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Acid),
                            decorationBox = { inner ->
                                Box {
                                    if (prompt.isEmpty()) Text("Describe a cut or paste a link", color = Muted, fontSize = 11.sp)
                                    inner()
                                }
                            },
                        )
                        Button(
                            enabled = prompt.isNotBlank() && !busy,
                            onClick = {
                                val text = prompt.trim()
                                prompt = ""
                                messages.add(true to text)
                                busy = true
                                scope.launch {
                                    val history = buildList {
                                        add(ChatMessage("system", "You are BossAI, a practical video editing assistant. Use set_trim to update the selected clip trim, set_export_profile to select a device-supported export profile, and download_video only when the user asks to download a public web video. Seal asks the user to confirm every download."))
                                        addAll(messages.map { (fromUser, content) -> ChatMessage(if (fromUser) "user" else "assistant", content) })
                                    }
                                    val reply = runCatching { askBossAi(aiSettings, history) }
                                    val assistant = reply.getOrNull()
                                    val function = assistant?.toolCalls?.firstOrNull()?.function
                                    when (function?.name) {
                                        "download_video" -> {
                                            val url = runCatching { JSONObject(function.arguments).getString("url") }.getOrNull()
                                            val parsedUrl = url?.let(Uri::parse)
                                            if (parsedUrl == null || parsedUrl.scheme !in setOf("http", "https") || parsedUrl.host.isNullOrBlank()) {
                                                messages.add(false to "BossAI returned an invalid video URL.")
                                            } else {
                                                val handoff = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    setPackage("com.junkfood.seal")
                                                    putExtra(Intent.EXTRA_TEXT, url)
                                                }
                                                runCatching { context.startActivity(handoff) }
                                                    .onSuccess { messages.add(false to "Opened Seal for your download review.") }
                                                    .onFailure { messages.add(false to "Could not open Seal. Install Seal and try again.") }
                                            }
                                        }
                                        "set_trim" -> {
                                            runCatching {
                                                val args = JSONObject(function.arguments)
                                                val durationSeconds = sourceDurationMs / 1000.0
                                                val startSeconds = args.getDouble("start_seconds").coerceIn(0.0, durationSeconds)
                                                val endSeconds = args.getDouble("end_seconds").coerceIn(0.0, durationSeconds)
                                                require(endSeconds > startSeconds) { "Trim end must be after trim start" }
                                                trimRange = (startSeconds * 1000).toFloat()..(endSeconds * 1000).toFloat()
                                            }.onSuccess { messages.add(false to "Trim range updated on the timeline.") }
                                                .onFailure { messages.add(false to "Could not set trim: ${it.message}") }
                                        }
                                        "set_export_profile" -> {
                                            runCatching {
                                                val args = JSONObject(function.arguments)
                                                val info = requireNotNull(videoInfo) { "Import a clip first" }
                                                val height = args.optInt("height", 0)
                                                val requestedResolution = when (height) {
                                                    1080 -> "1080p"
                                                    2160 -> "4k"
                                                    else -> "source"
                                                }
                                                val requestedFrameRate = args.optInt("frame_rate", exportFrameRate)
                                                val requestedBitrate = args.optInt("bitrate_mbps", bitrateMbps).coerceIn(1, 150) * 1_000_000
                                                val requestedHevc = args.optString("codec", if (useHevc) "hevc" else "h264") == "hevc"
                                                val size = outputSize(info, requestedResolution)
                                                require(requestedFrameRate <= info.frameRate) { "Requested FPS exceeds the source FPS" }
                                                val supported = withContext(Dispatchers.Default) {
                                                    VideoCapabilities.supports(
                                                        VideoCapabilities.mimeType(requestedHevc),
                                                        size?.first ?: info.width,
                                                        size?.second ?: info.height,
                                                        requestedFrameRate,
                                                        requestedBitrate,
                                                    )
                                                }
                                                require(supported) { "This device does not support that resolution, FPS, codec, and bitrate combination" }
                                                resolution = requestedResolution
                                                exportFrameRate = requestedFrameRate
                                                bitrateMbps = requestedBitrate / 1_000_000
                                                useHevc = requestedHevc
                                            }.onSuccess { messages.add(false to "Export profile applied. Review it before exporting.") }
                                                .onFailure { messages.add(false to "Profile not applied: ${it.message}") }
                                        }
                                        else -> messages.add(
                                            false to (assistant?.content
                                                ?: reply.exceptionOrNull()?.message?.let { "BossAI request failed: $it" }
                                                ?: "BossAI returned no response."),
                                        )
                                    }
                                    busy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt", modifier = Modifier.size(17.dp))
                        }
                    }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("AI DOWNLOADS REQUIRE YOUR CONFIRMATION IN SEAL  ·  IMPORTED CLIPS STAY ON DEVICE", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        if (showAiSettings) {
            AlertDialog(
                onDismissRequest = { showAiSettings = false },
                title = { Text("BossAI endpoint") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = { endpointInput = it },
                            label = { Text("HTTPS base URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        OutlinedTextField(value = modelInput, onValueChange = { modelInput = it }, label = { Text("Model") }, singleLine = true)
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching {
                            AiSettingsStore.save(context, AiSettings(endpointInput, modelInput, apiKeyInput))
                            aiSettings = AiSettingsStore.load(context)
                            showAiSettings = false
                        }.onFailure { Toast.makeText(context, it.message ?: "Invalid BossAI settings", Toast.LENGTH_LONG).show() }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showAiSettings = false }) { Text("Cancel") } },
                containerColor = Panel,
                titleContentColor = Paper,
                textContentColor = Paper,
            )
        }
    }

    private suspend fun askBossAi(settings: AiSettings, history: List<ChatMessage>): AssistantMessage {
        require(settings.baseUrl.isNotBlank() && settings.model.isNotBlank() && settings.apiKey.isNotBlank()) {
            "Open BossAI settings and enter an HTTPS base URL, model, and API key"
        }
        val response = BossAiApi.create(settings.baseUrl).complete(
            authorization = "Bearer ${settings.apiKey}",
            request = ChatRequest(
                model = settings.model,
                messages = history,
                tools = BossAiApi.editorTools(),
            ),
        )
        return response.choices.firstOrNull()?.message ?: error("BossAI returned no response")
    }

    private fun outputSize(info: VideoInfo?, profile: String): Pair<Int, Int>? {
        val video = info ?: return null
        if (profile == "source") return null
        val requestedLongEdge = when (profile) {
            "1080p" -> 1920
            "4k" -> 3840
            else -> return null
        }
        val sourceLongEdge = maxOf(video.width, video.height)
        val sourceShortEdge = minOf(video.width, video.height)
        val outputLongEdge = minOf(requestedLongEdge, sourceLongEdge)
        val requestedShortEdge = (outputLongEdge.toDouble() * sourceShortEdge / sourceLongEdge)
            .roundToInt().coerceAtLeast(2) / 2 * 2
        return if (video.width >= video.height) outputLongEdge to requestedShortEdge
        else requestedShortEdge to outputLongEdge
    }

    private fun profileAvailable(info: VideoInfo, profile: String): Boolean = when (profile) {
        "source" -> true
        "1080p" -> maxOf(info.width, info.height) >= 1920
        "4k" -> maxOf(info.width, info.height) >= 3840
        else -> false
    }

    @Composable
    private fun VideoPreview(file: File?, importing: Boolean, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val player = remember(file) {
            ExoPlayer.Builder(context).build().apply {
                file?.let { setMediaItem(MediaItem.fromUri(Uri.fromFile(it))); prepare() }
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        Box(modifier.fillMaxWidth().background(Color(0xFF080A09), RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (file != null) {
                AndroidView(
                    factory = { PlayerView(it).apply { useController = true } },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Line, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (importing) "IMPORTING CLIP" else "NO SOURCE", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    @Composable
    private fun TimelineStrip(
        durationMs: Long,
        trimRange: ClosedFloatingPointRange<Float>,
        enabled: Boolean,
        onTrimChange: (ClosedFloatingPointRange<Float>) -> Unit,
    ) {
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(trimRange.start.toLong()), color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("TRIM RANGE", color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(formatTime(trimRange.endInclusive.toLong()), color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            RangeSlider(
                value = trimRange,
                onValueChange = onTrimChange,
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                enabled = enabled,
            )
        }
    }

    private fun formatTime(timeMs: Long): String = "%02d:%02d.%1d".format(
        timeMs / 60_000,
        timeMs / 1_000 % 60,
        timeMs / 100 % 10,
    )

    @Composable
    private fun ActionButton(
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        secondary: Boolean = false,
        enabled: Boolean = true,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(7.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (secondary) Panel else Acid,
                contentColor = if (secondary) Paper else Ink,
                disabledContainerColor = Panel,
                disabledContentColor = Muted,
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}