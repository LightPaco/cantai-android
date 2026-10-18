
package com.cantai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF050607)
private val Cyan = Color(0xFF00E5FF)
private val Turquoise = Color(0xFF00FFD1)
private val White = Color(0xFFFFFFFF)
private val Gold = Color(0xFFC5A044)
private val Panel = Color(0xFF10171B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 6, 7)
        window.navigationBarColor = android.graphics.Color.rgb(5, 6, 7)
        setContent { CantaiApp() }
    }
}

@Composable
fun CantaiApp() {
    var message by remember { mutableStateOf("") }
    var chat by remember {
        mutableStateOf(listOf("CANTAI" to "Systems online. How can I help you?"))
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Turquoise,
            background = Black,
            surface = Panel,
            onBackground = White,
            onSurface = White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "CANTAI",
                        color = White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp
                    )
                    Text(
                        "PERSONAL INTELLIGENCE SYSTEM",
                        color = Turquoise,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(10.dp)
                        .shadow(12.dp, CircleShape, ambientColor = Turquoise)
                        .background(Turquoise, CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text("ONLINE", color = Turquoise, fontSize = 10.sp)
            }

            HorizontalDivider(color = Cyan.copy(alpha = 0.25f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f),
                contentAlignment = Alignment.Center
            ) {
                val transition = rememberInfiniteTransition(label = "core")
                val pulse by transition.animateFloat(
                    initialValue = 0.88f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )

                Box(
                    Modifier
                        .size(205.dp)
                        .scale(pulse)
                        .shadow(35.dp, CircleShape, ambientColor = Cyan)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Turquoise.copy(alpha = 0.25f),
                                    Cyan.copy(alpha = 0.12f),
                                    Black
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(128.dp)
                            .border(2.dp, Cyan.copy(alpha = 0.8f), CircleShape)
                            .padding(9.dp)
                            .border(1.dp, Turquoise, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(70.dp)
                                .shadow(25.dp, CircleShape, ambientColor = Turquoise)
                                .background(
                                    Brush.radialGradient(
                                        listOf(White, Turquoise, Cyan.copy(alpha = 0.3f))
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 20.dp, end = 20.dp)
                            .size(9.dp)
                            .shadow(10.dp, CircleShape, ambientColor = Gold)
                            .background(Gold, CircleShape)
                    )
                }
            }

            Text(
                "YOUR SYSTEM. YOUR RULES.",
                color = Gold,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(chat) { item ->
                    val isAI = item.first == "CANTAI"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isAI) Panel else Color(0xFF10252A))
                            .border(
                                1.dp,
                                if (isAI) Cyan.copy(alpha = 0.25f)
                                else Turquoise.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(13.dp)
                    ) {
                        Text(
                            item.first,
                            color = if (isAI) Turquoise else Gold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(item.second, color = White, fontSize = 14.sp)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message Cantai...", color = White.copy(alpha = 0.45f))
                    },
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Turquoise.copy(alpha = 0.35f),
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        cursorColor = Cyan
                    ),
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val text = message.trim()
                        if (text.isNotEmpty()) {
                            chat = chat + ("YOU" to text)
                            chat = chat + (
                                "CANTAI" to
                                    "Your interface is working. AI connection will be added next."
                                )
                            message = ""
                        }
                    },
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Cyan,
                        contentColor = Black
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("SEND", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}
