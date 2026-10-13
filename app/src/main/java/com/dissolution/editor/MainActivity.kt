package com.dissolution.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private val Ink = Color(0xFF101312)
private val Panel = Color(0xFF1A201D)
private val Line = Color(0xFF35403A)
private val Paper = Color(0xFFEAE9DD)
private val Muted = Color(0xFF9BA59D)
private val Acid = Color(0xFFC3F36B)

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
        var pickedVideo by remember { mutableStateOf<File?>(null) }
        var importing by remember { mutableStateOf(false) }
        val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
        var prompt by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var exportState by remember { mutableStateOf("READY") }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        onVideoPicked = { uri ->
            importing = true
            scope.launch(Dispatchers.IO) {
                val copy = File(cacheDir, "source_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use(input::copyTo) }
                launch(Dispatchers.Main) {
                    pickedVideo = copy
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
                Text("LOCAL PROJECT", color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("IMPORT CLIP", Icons.Default.Add, importVideo, Modifier.weight(1f))
                ActionButton("FADCam", Icons.Default.VideoCameraBack, openFadCam, Modifier.weight(1f), secondary = true)
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.35f).fillMaxWidth()) {
                    Text("01 / PREVIEW", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    VideoPreview(pickedVideo, importing, Modifier.weight(1f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = Acid, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(pickedVideo?.name ?: "No clip loaded", color = Paper, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(exportState, color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(10.dp))
                    TimelineStrip(hasClip = pickedVideo != null)
                    Spacer(Modifier.height(10.dp))
                    ActionButton("EXPORT H.264", Icons.Default.PlayArrow, {
                        val source = pickedVideo
                        if (source == null) {
                            exportState = "IMPORT A CLIP"
                        } else {
                            busy = true
                            exportState = "ENCODING"
                            scope.launch(Dispatchers.IO) {
                                val outputDir = File(getExternalFilesDir("Movies"), "exports").apply { mkdirs() }
                                val output = File(outputDir, "dissolution_${System.currentTimeMillis()}.mp4")
                                val command = "-y -i ${source.absolutePath} -c:v libx264 -preset ultrafast -c:a aac ${output.absolutePath}"
                                val session = FFmpegKit.execute(command)
                                launch(Dispatchers.Main) {
                                    busy = false
                                    exportState = if (ReturnCode.isSuccess(session.returnCode)) "EXPORTED" else "EXPORT FAILED"
                                    Toast.makeText(context, if (ReturnCode.isSuccess(session.returnCode)) "Saved to ${output.name}" else "FFmpeg export failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }, Modifier.fillMaxWidth(), secondary = true, enabled = !busy)
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
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
                            Text(if (user) "YOU  /  $text" else "BOSS AI  /  $text", color = if (user) Paper else Acid, fontSize = 12.sp, lineHeight = 18.sp)
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
                                        add(ChatMessage("system", "You are BossAI, a practical video editing assistant. Use download_video only when the user asks to download a video from a public web URL. Never claim a download succeeded; Seal will ask the user to review and confirm it."))
                                        addAll(messages.map { (fromUser, content) -> ChatMessage(if (fromUser) "user" else "assistant", content) })
                                    }
                                    val reply = runCatching { askBossAi(history) }
                                    val result = reply.getOrElse { "BossAI is not configured or the request failed: ${it.message ?: "unknown error"}" }
                                    val url = result.substringAfter("SEAL_URL:", "").trim().takeIf(String::isNotEmpty)
                                    if (url != null) {
                                        val handoff = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            setPackage("com.junkfood.seal")
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }
                                        runCatching { context.startActivity(handoff) }
                                            .onSuccess { messages.add(false to "Opened Seal for your download review.") }
                                            .onFailure { messages.add(false to "Could not open Seal. Install Seal and try again.") }
                                    } else {
                                        messages.add(false to result)
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
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("AI DOWNLOADS REQUIRE YOUR CONFIRMATION IN SEAL  ·  IMPORTED CLIPS STAY ON DEVICE", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }

    private suspend fun askBossAi(history: List<ChatMessage>): String {
        require(BuildConfig.BOSSAI_BASE_URL.isNotBlank()) { "Set bossAiBaseUrl, bossAiModel and bossAiApiKey Gradle properties" }
        require(BuildConfig.BOSSAI_MODEL.isNotBlank() && BuildConfig.BOSSAI_API_KEY.isNotBlank()) { "Set bossAiBaseUrl, bossAiModel and bossAiApiKey Gradle properties" }
        val response = BossAiApi.create(BuildConfig.BOSSAI_BASE_URL).complete(
            authorization = "Bearer ${BuildConfig.BOSSAI_API_KEY}",
            request = ChatRequest(
                model = BuildConfig.BOSSAI_MODEL,
                messages = history,
                tools = listOf(BossAiApi.downloadTool()),
            ),
        )
        val message = response.choices.firstOrNull()?.message ?: error("BossAI returned no response")
        val downloadCall = message.toolCalls?.firstOrNull { it.function.name == "download_video" }
        if (downloadCall != null) {
            val url = JSONObject(downloadCall.function.arguments).optString("url")
            val parsed = Uri.parse(url)
            require(parsed.scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()) { "BossAI returned an invalid video URL" }
            return "SEAL_URL:$url"
        }
        return message.content ?: "I couldn't form a response. Try asking in a different way."
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
    private fun TimelineStrip(hasClip: Boolean) {
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("00:00", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("TIMELINE", color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("00:15", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(34.dp).background(Color(0xFF252E28), RoundedCornerShape(4.dp))) {
                if (hasClip) Box(Modifier.fillMaxWidth(0.72f).fillMaxSize().padding(2.dp).background(Color(0xFF627B4A), RoundedCornerShape(3.dp)))
                Box(Modifier.width(2.dp).fillMaxSize().background(Acid))
            }
        }
    }

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