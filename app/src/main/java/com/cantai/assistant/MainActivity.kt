package com.cantai.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var voice: VoiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        voice = VoiceController(this)
        setContent { CantaiApp(voice) }
    }

    override fun onStop() {
        super.onStop()
        voice.stopListening()
        voice.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.release()
    }
}

@Composable
fun CantaiApp(voice: VoiceController) {
    val context = LocalContext.current
    val chat = remember { ChatState() }
    var voiceOutput by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var showRationale by remember { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micDenied = !granted
        if (granted) voice.startListening()
    }

    LaunchedEffect(voice) {
        voice.onFinalResult = { text -> chat.send(text) }
    }

    fun submitTyped() {
        if (input.isNotBlank()) {
            chat.send(input)
            input = ""
        }
    }

    fun onMic() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        when {
            voice.listening -> voice.stopListening()
            granted -> {
                micDenied = false
                voice.startListening()
            }
            else -> showRationale = true
        }
    }

    val coreState = when {
        voice.listening -> CoreState.Listening
        voice.speaking -> CoreState.Speaking
        chat.thinking -> CoreState.Thinking
        voice.errorMessage != null || micDenied -> CoreState.Error
        else -> CoreState.Idle
    }

    Box(Modifier.fillMaxSize().cantaiBackground()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Header(
                link = chat.link,
                voiceOutput = voiceOutput,
                onToggleVoice = {
                    voiceOutput = !voiceOutput
                    if (voiceOutput) voice.speak("Voice output on.") else voice.stopSpeaking()
                },
                onNewChat = { chat.clear() }
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AiCore(state = coreState, level = voice.level, coreSize = 170.dp)
            }

            val notice = if (micDenied) {
                "Microphone is off for Cantai. Turn it on in Settings to use voice."
            } else {
                voice.errorMessage
            }
            if (notice != null) {
                Notice(
                    text = notice,
                    actionLabel = if (micDenied) "SETTINGS" else null,
                    onAction = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    },
                    onDismiss = {
                        micDenied = false
                        voice.clearError()
                    }
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                LaunchedEffect(chat.messages.size) {
                    if (chat.messages.isNotEmpty()) {
                        listState.animateScrollToItem(chat.messages.lastIndex)
                    }
                }
                if (chat.messages.isEmpty()) {
                    Text(
                        "Tap the mic or type a message.",
                        color = Cantai.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chat.messages, key = { it.id }) { MessageBubble(it) }
                }
            }

            if (voice.listening) {
                Text(
                    text = if (voice.partial.isBlank()) "Listening…" else voice.partial,
                    color = Cantai.Yellow,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                onSend = { submitTyped() },
                listening = voice.listening,
                onMic = { onMic() }
            )
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            containerColor = Cantai.Black,
            titleContentColor = Cantai.Cyan,
            textContentColor = Cantai.White,
            title = { Text("Microphone access") },
            text = {
                Text(
                    "Cantai uses the microphone only while you're talking to it, to turn " +
                        "your speech into text. Recognition is done by your phone's speech " +
                        "service, so it depends on your device and language support."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("ALLOW", color = Cantai.Turquoise) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("NOT NOW", color = Cantai.White)
                }
            }
        )
    }
}

@Composable
fun Header(
    link: Link,
    voiceOutput: Boolean,
    onToggleVoice: () -> Unit,
    onNewChat: () -> Unit
) {
    val dot = if (link == Link.Online) Cantai.Turquoise else Cantai.Yellow
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GeminiMark(markSize = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "CANTAI",
                color = Cantai.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(
                    link.label,
                    color = Cantai.White,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }
        Chip(if (voiceOutput) "VOICE ON" else "VOICE OFF", voiceOutput, onToggleVoice)
        Spacer(Modifier.width(8.dp))
        Chip("NEW", false, onNewChat)
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) Cantai.Turquoise else Cantai.Black.copy(alpha = 0.35f))
            .border(1.dp, if (active) Cantai.Turquoise else Cantai.Cyan, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Cantai.Black else Cantai.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun Notice(
    text: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(Cantai.Black.copy(alpha = 0.55f))
            .border(1.dp, Cantai.Yellow, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Cantai.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = Cantai.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        TextButton(onClick = onDismiss) {
            Text("OK", color = Cantai.Turquoise, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MessageBubble(m: ChatMessage) {
    val isUser = m.sender == Sender.User
    val shape = RoundedCornerShape(18.dp)
    val bg = when (m.sender) {
        Sender.User -> Cantai.Turquoise
        Sender.Cantai -> Cantai.Black.copy(alpha = 0.40f)
        Sender.System -> Cantai.Black.copy(alpha = 0.55f)
    }
    val border = when (m.sender) {
        Sender.User -> Color.Transparent
        Sender.Cantai -> Cantai.Cyan
        Sender.System -> Cantai.Yellow
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bg)
                .border(1.dp, border, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                m.text,
                color = if (isUser) Cantai.Black else Cantai.White,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    listening: Boolean,
    onMic: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("Message Cantai", color = Cantai.White.copy(alpha = 0.6f))
            },
            shape = RoundedCornerShape(26.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Cantai.White,
                unfocusedTextColor = Cantai.White,
                cursorColor = Cantai.Turquoise,
                focusedBorderColor = Cantai.Turquoise,
                unfocusedBorderColor = Cantai.Cyan,
                focusedContainerColor = Cantai.Black.copy(alpha = 0.40f),
                unfocusedContainerColor = Cantai.Black.copy(alpha = 0.40f)
            )
        )
        Spacer(Modifier.width(8.dp))
        SendButton(enabled = value.isNotBlank(), onClick = onSend)
        Spacer(Modifier.width(8.dp))
        MicButton(listening = listening, onClick = onMic)
    }
}

@Composable
fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) Cantai.Turquoise else Cantai.Black.copy(alpha = 0.35f))
            .border(
                1.dp,
                if (enabled) Cantai.Turquoise else Cantai.Cyan.copy(alpha = 0.5f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "SEND",
            color = if (enabled) Cantai.Black else Cantai.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (listening) Cantai.Yellow else Cantai.Cyan)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (listening) "Stop listening" else "Start voice input"
            },
        contentAlignment = Alignment.Center
    ) {
        MicIcon(Cantai.Black, Modifier.size(28.dp))
    }
}

@Composable
fun MicIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, h * 0.14f),
            size = Size(w * 0.28f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.14f)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.26f, h * 0.28f),
            size = Size(w * 0.48f, h * 0.40f),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.68f),
            end = Offset(w * 0.5f, h * 0.84f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.38f, h * 0.84f),
            end = Offset(w * 0.62f, h * 0.84f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
} 
