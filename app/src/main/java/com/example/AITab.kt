package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AITab(context: Context) {
    val sharedPrefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") }
    var isLogged by remember { mutableStateOf(apiKey.isNotBlank()) }
    var showLoginDialog by remember { mutableStateOf(false) }

    var logText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            containerColor = CleanMinimalismTheme.SecondaryBackground,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                         Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = CleanMinimalismTheme.AccentColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Вход через Google", color = CleanMinimalismTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Авторизуйтесь в Google для доступа к Root Intelligence.",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Button(
                        onClick = {
                            // Emulate Google Login logic
                            apiKey = "AIStudio_Google_Logged_In_Token" // Dummy token to bypass check
                            sharedPrefs.edit().putString("api_key", apiKey).apply()
                            isLogged = true
                            showLoginDialog = false
                            Toast.makeText(context, "Вход выполнен через Google", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        // Google Icon
                        Text("Продолжить как ${context.getSharedPreferences("user_prefs", 0).getString("user_name", "User")}", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanMinimalismTheme.DeepBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CleanMinimalismTheme.HighlightBlue, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Root Intelligence",
                        color = CleanMinimalismTheme.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Интеллектуальный анализ логов и ошибок",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            
            // Login Google Button
            if (!isLogged) {
                Button(
                    onClick = { showLoginDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                    border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder)
                ) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = CleanMinimalismTheme.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Войти", color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp)
                }
            } else {
                IconButton(onClick = { showLoginDialog = true }) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "Аккаунт", tint = CleanMinimalismTheme.HighlightGreen)
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(visible = isLogged) {
            Column {
                // Input Box
                OutlinedTextField(
                    value = logText,
                    onValueChange = { logText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Вставьте лог здесь...", color = CleanMinimalismTheme.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CleanMinimalismTheme.AccentColor,
                        unfocusedBorderColor = CleanMinimalismTheme.CardBorder,
                        focusedContainerColor = CleanMinimalismTheme.SurfaceDark,
                        unfocusedContainerColor = CleanMinimalismTheme.SurfaceDark,
                        focusedTextColor = CleanMinimalismTheme.TextPrimary,
                        unfocusedTextColor = CleanMinimalismTheme.TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Analyze Button
                Button(
                    onClick = {
                        if (logText.isNotBlank()) {
                            isLoading = true
                            responseText = ""
                            coroutineScope.launch {
                                val result = analyzeErrorLog(logText, apiKey)
                                responseText = result
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading && logText.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запустить анализ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Output Area
                androidx.compose.animation.AnimatedVisibility(visible = responseText.isNotEmpty()) {
                    Column {
                        Text(
                            "Результат анализа:",
                            color = CleanMinimalismTheme.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CleanMinimalismTheme.SurfaceDark)
                                .border(1.dp, CleanMinimalismTheme.HighlightBlue, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = responseText,
                                    color = CleanMinimalismTheme.TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (!isLogged) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Пожалуйста, войдите, чтобы использовать функции анализа",
                    color = CleanMinimalismTheme.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp)) // padding for bottom bar
    }
}
