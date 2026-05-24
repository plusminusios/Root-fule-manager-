package com.example

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

// ── SharedPrefs keys ──────────────────────────────────────────
private const val PREFS_NAME = "gemini_prefs"
private const val KEY_API_KEY = "api_key"
private const val KEY_USER_NAME = "user_name"
private const val KEY_USER_EMAIL = "user_email"
private const val KEY_IS_SIGNED_IN = "is_google_signed_in"

@Composable
fun AITab(context: Context) {
    val sharedPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(sharedPrefs.getString(KEY_API_KEY, "") ?: "") }
    var isLogged by remember { mutableStateOf(sharedPrefs.getBoolean(KEY_IS_SIGNED_IN, false)) }
    var userName by remember {
        mutableStateOf(sharedPrefs.getString(KEY_USER_NAME, "Пользователь") ?: "Пользователь")
    }
    var userEmail by remember {
        mutableStateOf(sharedPrefs.getString(KEY_USER_EMAIL, "") ?: "")
    }
    var userPhotoUrl by remember { mutableStateOf<String?>(null) }

    var showLoginDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // ── Input / output state ──────────────────────────────────
    var selectedMode by remember { mutableIntStateOf(0) }   // 0 = Log Analyzer, 1 = AI Chat
    var logText by remember { mutableStateOf("") }
    var chatMessage by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Google Sign-In client ─────────────────────────────────
    val webClientId = context.getString(R.string.default_web_client_id)
    val googleSignInClient = remember(webClientId) {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
        if (webClientId.isNotBlank() && !webClientId.startsWith("YOUR_")) {
            gsoBuilder.requestIdToken(webClientId)
        }
        GoogleSignIn.getClient(context, gsoBuilder.build())
    }

    // Restore existing Google session on launch
    LaunchedEffect(Unit) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            userName = account.displayName ?: sharedPrefs.getString(KEY_USER_NAME, "Пользователь") ?: "Пользователь"
            userEmail = account.email ?: ""
            userPhotoUrl = account.photoUrl?.toString()
            isLogged = sharedPrefs.getBoolean(KEY_IS_SIGNED_IN, true)
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val name = account?.displayName ?: "Пользователь"
            val email = account?.email ?: ""
            userPhotoUrl = account?.photoUrl?.toString()
            sharedPrefs.edit()
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_EMAIL, email)
                .putBoolean(KEY_IS_SIGNED_IN, true)
                .apply()
            userName = name
            userEmail = email
            isLogged = true
            showLoginDialog = false
            Toast.makeText(context, "Добро пожаловать, $name!", Toast.LENGTH_SHORT).show()
            if (apiKey.isBlank()) showApiKeyDialog = true
        } catch (e: ApiException) {
            val hint = when (e.statusCode) {
                10 -> " Настройте OAuth в Google Cloud Console (SHA-1 + package name)."
                12501 -> " Вход отменён."
                else -> ""
            }
            Toast.makeText(context, "Ошибка входа (код ${e.statusCode}).$hint", Toast.LENGTH_LONG).show()
        }
    }

    // ── Dialogs ───────────────────────────────────────────────

    if (showLoginDialog) {
        GoogleLoginDialog(
            onSignIn = {
                val signInIntent = googleSignInClient.signInIntent
                signInLauncher.launch(signInIntent)
            },
            onDismiss = { showLoginDialog = false }
        )
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = apiKey,
            onSave = { newKey ->
                apiKey = newKey
                sharedPrefs.edit().putString(KEY_API_KEY, newKey).apply()
                showApiKeyDialog = false
                Toast.makeText(context, "API ключ сохранён ✓", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // ── Main UI ───────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanMinimalismTheme.DeepBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = CleanMinimalismTheme.HighlightBlue,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Root Intelligence",
                        color = CleanMinimalismTheme.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isLogged) "Привет, $userName 👋" else "Анализ логов и чат с Gemini AI",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 12.sp
                    )
                    if (isLogged && userEmail.isNotBlank()) {
                        Text(
                            userEmail,
                            color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLogged && userPhotoUrl != null) {
                    AsyncImage(
                        model = userPhotoUrl,
                        contentDescription = "Аватар",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .padding(end = 4.dp)
                    )
                }
                // API key shortcut icon (shown when logged in and key set)
                if (isLogged) {
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(
                            if (apiKey.isNotBlank()) Icons.Filled.VpnKey else Icons.Filled.VpnKeyOff,
                            contentDescription = "API Key",
                            tint = if (apiKey.isNotBlank()) CleanMinimalismTheme.HighlightGreen
                                   else Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Sign-out button
                    IconButton(onClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            sharedPrefs.edit()
                                .putBoolean(KEY_IS_SIGNED_IN, false)
                                .remove(KEY_USER_NAME)
                                .remove(KEY_USER_EMAIL)
                                .apply()
                            isLogged = false
                            userName = "Пользователь"
                            userEmail = ""
                            userPhotoUrl = null
                            Toast.makeText(context, "Выход выполнен", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Выйти",
                            tint = CleanMinimalismTheme.HighlightGreen
                        )
                    }
                } else {
                    Button(
                        onClick = { showLoginDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CleanMinimalismTheme.SurfaceDark
                        ),
                        border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder)
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = CleanMinimalismTheme.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Войти", color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Logged-in content ─────────────────────────────────
        AnimatedVisibility(visible = isLogged) {
            Column {
                // API key warning banner
                if (apiKey.isBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFB300).copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFB300))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Требуется Gemini API ключ",
                                    color = CleanMinimalismTheme.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Получите бесплатно на aistudio.google.com",
                                    color = CleanMinimalismTheme.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = { showApiKeyDialog = true }) {
                                Text("Добавить", color = CleanMinimalismTheme.AccentColor, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Mode tabs
                TabRow(
                    selectedTabIndex = selectedMode,
                    containerColor = CleanMinimalismTheme.SurfaceDark,
                    contentColor = CleanMinimalismTheme.AccentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(bottom = 16.dp)
                ) {
                    Tab(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0; responseText = "" },
                        icon = {
                            Icon(
                                Icons.Filled.BugReport,
                                null,
                                tint = if (selectedMode == 0) CleanMinimalismTheme.AccentColor
                                       else CleanMinimalismTheme.TextSecondary
                            )
                        },
                        text = {
                            Text(
                                "Анализ логов",
                                color = if (selectedMode == 0) CleanMinimalismTheme.AccentColor
                                        else CleanMinimalismTheme.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1; responseText = "" },
                        icon = {
                            Icon(
                                Icons.Filled.Chat,
                                null,
                                tint = if (selectedMode == 1) CleanMinimalismTheme.AccentColor
                                       else CleanMinimalismTheme.TextSecondary
                            )
                        },
                        text = {
                            Text(
                                "AI Чат",
                                color = if (selectedMode == 1) CleanMinimalismTheme.AccentColor
                                        else CleanMinimalismTheme.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    )
                }

                // ── Mode: Log Analyzer ────────────────────────
                if (selectedMode == 0) {
                    OutlinedTextField(
                        value = logText,
                        onValueChange = { logText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = {
                            Text(
                                "Вставьте лог ошибки или вывод команды dmesg / logcat...",
                                color = CleanMinimalismTheme.TextSecondary,
                                fontSize = 13.sp
                            )
                        },
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (apiKey.isBlank()) { showApiKeyDialog = true; return@Button }
                            if (logText.isNotBlank()) {
                                isLoading = true
                                responseText = ""
                                coroutineScope.launch {
                                    responseText = analyzeErrorLog(logText, apiKey)
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
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Анализирую...", color = Color.White)
                        } else {
                            Icon(Icons.Filled.Search, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Анализировать лог", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Mode: AI Chat ─────────────────────────────
                if (selectedMode == 1) {
                    OutlinedTextField(
                        value = chatMessage,
                        onValueChange = { chatMessage = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = {
                            Text(
                                "Спросите что-нибудь об Android, Linux, root-правах...",
                                color = CleanMinimalismTheme.TextSecondary,
                                fontSize = 13.sp
                            )
                        },
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (apiKey.isBlank()) { showApiKeyDialog = true; return@Button }
                            if (chatMessage.isNotBlank()) {
                                isLoading = true
                                responseText = ""
                                coroutineScope.launch {
                                    responseText = chatWithGemini(chatMessage, apiKey)
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading && chatMessage.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Думаю...", color = Color.White)
                        } else {
                            Icon(Icons.Filled.Send, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отправить", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Response box ──────────────────────────────
                Spacer(modifier = Modifier.height(20.dp))

                AnimatedVisibility(visible = responseText.isNotEmpty()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Ответ ИИ:",
                                color = CleanMinimalismTheme.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            IconButton(onClick = { responseText = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Очистить",
                                    tint = CleanMinimalismTheme.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CleanMinimalismTheme.SurfaceDark)
                                .border(
                                    1.dp,
                                    CleanMinimalismTheme.HighlightBlue,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = responseText,
                                    color = CleanMinimalismTheme.TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Not logged-in splash ──────────────────────────────
        AnimatedVisibility(visible = !isLogged) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = CleanMinimalismTheme.AccentColor,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Root Intelligence",
                        color = CleanMinimalismTheme.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Анализ логов и ошибок Android/Linux с помощью Gemini AI",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { showLoginDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CleanMinimalismTheme.AccentColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Icon(Icons.Filled.AccountCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Войти через Google", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ── Extracted dialog composables ──────────────────────────────

@Composable
private fun GoogleLoginDialog(onSignIn: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CleanMinimalismTheme.SecondaryBackground,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = CleanMinimalismTheme.AccentColor,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Вход через Google",
                    color = CleanMinimalismTheme.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Войдите через Google аккаунт, чтобы использовать Root Intelligence.",
                    color = CleanMinimalismTheme.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                Button(
                    onClick = onSignIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFDDDDDD))
                ) {
                    // Google brand colours on G icon simulation
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Войти через Google", color = Color(0xFF333333), fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
            }
        }
    )
}

@Composable
private fun ApiKeyDialog(currentKey: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var tempKey by remember { mutableStateOf(currentKey) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CleanMinimalismTheme.SecondaryBackground,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VpnKey, null, tint = CleanMinimalismTheme.AccentColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini API Ключ", color = CleanMinimalismTheme.TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Ключ хранится только на устройстве. Получите бесплатно на aistudio.google.com",
                    color = CleanMinimalismTheme.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    label = { Text("API Ключ") },
                    placeholder = { Text("AIza...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Скрыть" else "Показать",
                                tint = CleanMinimalismTheme.TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CleanMinimalismTheme.AccentColor,
                        unfocusedBorderColor = CleanMinimalismTheme.CardBorder,
                        focusedTextColor = CleanMinimalismTheme.TextPrimary,
                        unfocusedTextColor = CleanMinimalismTheme.TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (tempKey.isNotBlank()) onSave(tempKey) },
                colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor),
                enabled = tempKey.isNotBlank()
            ) {
                Text("Сохранить", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
            }
        }
    )
}
