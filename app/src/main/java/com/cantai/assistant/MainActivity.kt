package com.cantai.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val chat = remember { ChatState(ChatStore(context)) }
    var voiceOutput by remember { mutableStateOf(true) }
    val voiceOutputNow by rememberUpdatedState(voiceOutput)
    var input by remember { mutableStateOf("") }
    var showRationale by remember { mutableStateOf(false) }
    var micDenied by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micDenied = !granted
        if (granted) voice.startListening()
    }

    LaunchedEffect(voice, chat) {
        voice.onFinalResult = { text -> chat.send(text) }
        chat.onAssistantMessage = { text -> if (voiceOutputNow) voice.speak(text) }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
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

    val empty = chat.messages.isEmpty()
    val coreSize by animateDpAsState(
        if (empty) 190.dp else 84.dp,
        tween(400),
        label = "coreSize"
    )

    val notice: String? = when {
        micDenied -> "Microphone is off for Cantai. Turn it on in Settings to use voice."
        voice.errorMessage != null -> voice.errorMessage
        chat.saveFailed -> "Couldn't save this chat on your phone."
        else -> null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawer(
                conversations = chat.conversations,
                currentId = chat.currentId,
                onNew = {
                    voice.stopSpeaking()
                    chat.newChat()
                    input = ""
                    scope.launch { drawerState.close() }
                },
                onOpen = { id ->
                    voice.stopSpeaking()
                    chat.open(id)
                    input = ""
                    scope.launch { drawerState.close() }
                },
                onDelete = { id -> chat.delete(id) }
            )
        }
    ) {
        Box(Modifier.fillMaxSize().cantaiBackground()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Header(
                    link = chat.link,
                    voiceOutput = voiceOutput,
                    onMenu = { scope.launch { drawerState.open() } },
                    onToggleVoice = {
                        voiceOutput = !voiceOutput
                        if (voiceOutput) voice.speak("Voice output on.") else voice.stopSpeaking()
                    },
                    onNewChat = {
                        voice.stopSpeaking()
                        chat.newChat()
                        input = ""
                    }
                )

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AiCore(state = coreState, level = voice.level, coreSize = coreSize)
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
                            chat.dismissSaveError()
                        }
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (empty) {
                        Welcome(onPick = { input = it })
                    } else {
                        MessageList(chat)
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
    onMenu: () -> Unit,
    onToggleVoice: () -> Unit,
    onNewChat: () -> Unit
) {
    val dot = if (link == Link.Online) Cantai.Turquoise else Cantai.Yellow
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(onMenu, "Open chat history") {
            MenuIcon(Cantai.White, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        GeminiMark(markSize = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "CANTAI",
                color = Cantai.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(
                    link.label,
                    color = Cantai.White,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Chip(if (voiceOutput) "VOICE ON" else "VOICE OFF", voiceOutput, onToggleVoice)
        Spacer(Modifier.width(6.dp))
        IconCircle(onNewChat, "New chat") {
            PlusIcon(Cantai.White, Modifier.size(20.dp))
        }
    }
}

@Composable
fun IconCircle(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Cantai.Black.copy(alpha = 0.35f))
            .border(1.dp, Cantai.Cyan, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
fun Welcome(onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "How can I help?",
            color = Cantai.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap the mic or type a message.",
            color = Cantai.White.copy(alpha = 0.85f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(20.dp))
        Suggestion("Explain a topic simply") { onPick("Explain a topic simply: ") }
        Suggestion("Help me study for a test") { onPick("Help me study for a test on ") }
        Suggestion("Plan my day") { onPick("Help me plan my day. ") }
    }
}

@Composable
fun Suggestion(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Cantai.Black.copy(alpha = 0.35f))
            .border(1.dp, Cantai.Cyan.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, color = Cantai.White, fontSize = 14.sp)
    }
}

@Composable
fun MessageList(chat: ChatState) {
    val listState = rememberLazyListState()
    LaunchedEffect(chat.currentId, chat.messages.size) {
        if (chat.messages.isNotEmpty()) listState.scrollToItem(chat.messages.lastIndex)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(chat.messages, key = { it.id }) { MessageRow(it) }
    }
}

@Composable
fun MessageRow(m: ChatMessage) {
    when (m.sender) {
        Sender.User -> UserBubble(m.text)
        Sender.Cantai -> AssistantRow(m.text, isSystem = false)
        Sender.System -> AssistantRow(m.text, isSystem = true)
    }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Cantai.Turquoise)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = Cantai.Black, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
fun AssistantRow(text: String, isSystem: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Cantai.Black.copy(alpha = 0.45f))
                .border(1.dp, if (isSystem) Cantai.Yellow else Cantai.Cyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GeminiMark(markSize = 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        val bodyModifier = if (isSystem) {
            Modifier
                .weight(1f)
                .clip(shape)
                .background(Cantai.Black.copy(alpha = 0.55f))
                .border(1.dp, Cantai.Yellow, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        } else {
            Modifier.weight(1f).padding(top = 4.dp)
        }
        Column(bodyModifier) {
            SelectionContainer {
                Text(text, color = Cantai.White, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
fun HistoryDrawer(
    conversations: List<Conversation>,
    currentId: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var toDelete by remember { mutableStateOf<Conversation?>(null) }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = lerp(Cantai.Black, Cantai.Cyan, 0.14f)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeminiMark(markSize = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "CHATS",
                    color = Cantai.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            Chip("+ NEW CHAT", true, onNew)
            Spacer(Modifier.height(14.dp))
            if (conversations.isEmpty()) {
                Text(
                    "No saved chats yet. Send a message and it will appear here.",
                    color = Cantai.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { c ->
                        HistoryRow(
                            c = c,
                            selected = c.id == currentId,
                            onOpen = onOpen,
                            onDelete = { toDelete = c }
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Cantai.Black,
            titleContentColor = Cantai.Cyan,
            textContentColor = Cantai.White,
            title = { Text("Delete this chat?") },
            text = { Text("\"${c.title}\" will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(c.id)
                    toDelete = null
                }) { Text("DELETE", color = Cantai.Yellow) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("CANCEL", color = Cantai.White)
                }
            }
        )
    }
}

@Composable
fun HistoryRow(
    c: Conversation,
    selected: Boolean,
    onOpen: (String) -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val whenText = remember(c.updatedAt) {
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(c.updatedAt))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) Cantai.Turquoise.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) Cantai.Turquoise else Color.Transparent, shape)
            .clickable { onOpen(c.id) }
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                c.title,
                color = Cantai.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(whenText, color = Cantai.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        TextButton(onClick = onDelete) {
            Text("DELETE", color = Cantai.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        ActionButton(
            hasText = value.isNotBlank(),
            listening = listening,
            onSend = onSend,
            onMic = onMic
        )
    }
}

@Composable
fun ActionButton(
    hasText: Boolean,
    listening: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    val bg = when {
        hasText -> Cantai.Turquoise
        listening -> Cantai.Yellow
        else -> Cantai.Cyan
    }
    val description = when {
        hasText -> "Send message"
        listening -> "Stop listening"
        else -> "Start voice input"
    }
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = if (hasText) onSend else onMic)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (hasText) {
            SendIcon(Cantai.Black, Modifier.size(26.dp))
        } else {
            MicIcon(Cantai.Black, Modifier.size(28.dp))
        }
    }
}

@Composable
fun SendIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.11f
        val tip = Offset(w * 0.5f, h * 0.20f)
        drawLine(color, Offset(w * 0.5f, h * 0.82f), tip, strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, h * 0.46f), tip, strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.76f, h * 0.46f), tip, strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun MenuIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = h * 0.10f
        for (y in listOf(0.24f, 0.50f, 0.76f)) {
            drawLine(
                color,
                Offset(w * 0.08f, h * y),
                Offset(w * 0.92f, h * y),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.10f
        drawLine(color, Offset(w * 0.5f, h * 0.10f), Offset(w * 0.5f, h * 0.90f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.10f, h * 0.5f), Offset(w * 0.90f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
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
