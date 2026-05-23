package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

// Data model for File representation
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String
)

// Clean Minimalism Visual Theme
object CleanMinimalismTheme {
    var isDarkTheme by mutableStateOf(true)

    val DeepBackground: Color get() = if (isDarkTheme) Color(0xFF121212) else Color(0xFFFDFBFF)
    val SecondaryBackground: Color get() = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val SurfaceDark: Color get() = if (isDarkTheme) Color(0xFF2C2C2C) else Color(0xFFF7F9FF)
    val AccentColor: Color get() = if (isDarkTheme) Color(0xFF82B1FF) else Color(0xFF005AC1) // Light Blue / Royal Blue
    val TextPrimary: Color get() = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF1B1B1F)
    val TextSecondary: Color get() = if (isDarkTheme) Color(0xFFAAAAAA) else Color(0xFF44474E)
    val CodeBackground: Color get() = if (isDarkTheme) Color(0xFF121212) else Color(0xFF1C1B1F)
    val CardBorder: Color get() = if (isDarkTheme) Color(0xFF333333) else Color(0xFFE3E1E9)
    val HighlightGreen: Color get() = if (isDarkTheme) Color(0xFF81C995) else Color(0xFF7CB342)
    val HighlightBlue: Color get() = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF4F5B92)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CleanMinimalismTheme.DeepBackground
                ) {
                    RootManagerApp()
                }
            }
        }
    }
}

@Composable
fun RootManagerApp() {
    val context = LocalContext.current
    
    // Root status
    var isRootAvailable by remember { mutableStateOf<Boolean?>(null) }
    
    // Bottom Navigation tab selection
    var currentTab by remember { mutableStateOf("Files") }
    
    // File Navigation States
    var currentPath by remember { mutableStateOf("/") }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Active file being edited
    var activeFilePath by remember { mutableStateOf<String?>(null) }
    var activeFileName by remember { mutableStateOf("") }
    var activeFileContent by remember { mutableStateOf("") }
    
    // Viewer
    var fileViewerItem by remember { mutableStateOf<FileItem?>(null) }
    
    var showAboutDialog by remember { mutableStateOf(false) }
    
    // Dialog control states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showApkDialog by remember { mutableStateOf(false) }
    var showShExecuteDialog by remember { mutableStateOf(false) }
    
    // File selected for context menu actions
    var selectedFileForAction by remember { mutableStateOf<FileItem?>(null) }

    // Init directory content
    LaunchedEffect(currentPath) {
        if (isRootAvailable == null) {
            withContext(Dispatchers.IO) {
                val root = RootUtils.isRootAvailable()
                isRootAvailable = root
            }
        }
        fileList = getFilesForPath(currentPath, context)
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(currentTab = currentTab, onTabSelected = { tab ->
                currentTab = tab
            })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CleanMinimalismTheme.DeepBackground)
        ) {
            // Header Bar
            HeaderBar(
                title = "Root File Manager",
                activeFilePath = activeFilePath ?: currentPath,
                onSearchClicked = {
                    Toast.makeText(context, "Используйте панель поиска ниже", Toast.LENGTH_SHORT).show()
                },
                onMoreClicked = {
                    showAboutDialog = true
                }
            )

            if (isRootAvailable == false) {
                Surface(
                    color = Color(0xFFBA1A1A).copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Внимание: Root-права не предоставлены. Приложение работает в ограниченном режиме песочницы.",
                        color = Color(0xFFBA1A1A),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Main Content Area based on Selected Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        androidx.compose.animation.core.tween<Float>(300).let {
                            androidx.compose.animation.fadeIn(it) togetherWith androidx.compose.animation.fadeOut(it)
                        }
                    },
                    label = "Tab Transition"
                ) { targetTab ->
                    when (targetTab) {
                        "Files" -> {
                            FileExplorerTab(
                                currentPath = currentPath,
                                fileList = fileList,
                                searchQuery = searchQuery,
                                onSearchQueryChanged = { searchQuery = it },
                                onPathChanged = { newPath ->
                                    currentPath = newPath
                                },
                                onFileClick = { item ->
                                    if (item.isDirectory) {
                                        currentPath = item.path
                                    } else {
                                        val ext = item.extension.lowercase()
                                        if (ext == "sh") {
                                            selectedFileForAction = item
                                            showShExecuteDialog = true
                                        } else if (ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "svg", "pdf", "mp4", "mp3")) {
                                            fileViewerItem = item
                                        } else {
                                            // Open file in Editor
                                            activeFilePath = item.path
                                            activeFileName = item.name
                                            activeFileContent = readFileContent(item.path, context)
                                            currentTab = "Editor"
                                        }
                                    }
                                },
                                onFileLongClick = { item ->
                                    selectedFileForAction = item
                                },
                                onCreateFolderClick = { showNewFolderDialog = true },
                                onCreateFileClick = { showNewFileDialog = true },
                                onPermissionsClick = {
                                    selectedFileForAction = null
                                    showPermissionsDialog = true
                                },
                                onApkClick = {
                                    selectedFileForAction = null
                                    showApkDialog = true
                                }
                            )
                        }
                        "Editor" -> {
                            EditorTab(
                                fileName = activeFileName,
                                fileContent = activeFileContent,
                                onContentChanged = { activeFileContent = it },
                                onSave = {
                                    activeFilePath?.let { path ->
                                        val success = saveFileContent(path, activeFileContent, context)
                                        if (success) {
                                            Toast.makeText(context, "Файл успешно сохранен!", Toast.LENGTH_SHORT).show()
                                            // Update list
                                            fileList = getFilesForPath(currentPath, context)
                                        } else {
                                            Toast.makeText(context, "Ошибка сохранения файла", Toast.LENGTH_SHORT).show()
                                        }
                                    } ?: run {
                                        Toast.makeText(context, "Нет открытого файла для сохранения", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClose = {
                                    activeFilePath = null
                                    activeFileName = ""
                                    activeFileContent = ""
                                    currentTab = "Files"
                                }
                            )
                        }
                        "AI" -> {
                            AITab(context = context)
                        }
                        "Apps" -> {
                            AppsTab(context = context)
                        }
                        "Settings" -> {
                            SettingsTab(context = context, isRootAvailable = isRootAvailable)
                        }
                    }
                }
            }
        }
    }

    // Context Action Sheet/Dialog for single File
    selectedFileForAction?.let { fileItem ->
        AlertDialog(
            onDismissRequest = { selectedFileForAction = null },
            title = { Text(fileItem.name, color = CleanMinimalismTheme.TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Путь: ${fileItem.path}\nРазмер: ${formatSize(fileItem.size)}\nИзменен: ${formatDate(fileItem.lastModified)}",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            selectedFileForAction = null
                            activeFilePath = fileItem.path
                            activeFileName = fileItem.name
                            activeFileContent = readFileContent(fileItem.path, context)
                            currentTab = "Editor"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Открыть в Редакторе", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("File Path", fileItem.path)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Путь скопирован", Toast.LENGTH_SHORT).show()
                            selectedFileForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = CleanMinimalismTheme.TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Копировать путь", color = CleanMinimalismTheme.TextPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                showRenameDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text("Переименовать", color = CleanMinimalismTheme.TextPrimary)
                        }

                        Button(
                            onClick = {
                                showDeleteConfirmDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Text("Удалить", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedFileForAction = null }) {
                    Text("Закрыть", color = CleanMinimalismTheme.AccentColor)
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Создать папку", color = CleanMinimalismTheme.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Имя папки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val success = createFolder(currentPath, newFolderName, context)
                            if (success) {
                                Toast.makeText(context, "Папка создана", Toast.LENGTH_SHORT).show()
                                fileList = getFilesForPath(currentPath, context)
                            } else {
                                Toast.makeText(context, "Ошибка создания папки", Toast.LENGTH_SHORT).show()
                            }
                            showNewFolderDialog = false
                            newFolderName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)
                ) {
                    Text("Создать", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                }
            }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Создать файл", color = CleanMinimalismTheme.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Имя файла") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val success = createFile(currentPath, newFileName, context)
                            if (success) {
                                Toast.makeText(context, "Файл создан", Toast.LENGTH_SHORT).show()
                                fileList = getFilesForPath(currentPath, context)
                            } else {
                                Toast.makeText(context, "Ошибка создания файла", Toast.LENGTH_SHORT).show()
                            }
                            showNewFileDialog = false
                            newFileName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)
                ) {
                    Text("Создать", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        selectedFileForAction?.let { fileItem ->
            var newName by remember { mutableStateOf(fileItem.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Переименовать", color = CleanMinimalismTheme.TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Новое имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank() && newName != fileItem.name) {
                                val success = renameFile(fileItem.path, newName, context)
                                if (success) {
                                    Toast.makeText(context, "Переименовано", Toast.LENGTH_SHORT).show()
                                    fileList = getFilesForPath(currentPath, context)
                                } else {
                                    Toast.makeText(context, "Ошибка переименования", Toast.LENGTH_SHORT).show()
                                }
                                showRenameDialog = false
                                selectedFileForAction = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)
                    ) {
                        Text("ОК", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                    }
                }
            )
        }
    }

    // Delete Confirm Dialog
    if (showDeleteConfirmDialog) {
        selectedFileForAction?.let { fileItem ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Подтвердите удаление", color = CleanMinimalismTheme.TextPrimary) },
                text = { Text("Вы уверены, что хотите удалить ${fileItem.name}?", color = CleanMinimalismTheme.TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            val success = deleteFileOrDir(fileItem.path, context)
                            if (success) {
                                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                fileList = getFilesForPath(currentPath, context)
                            } else {
                                Toast.makeText(context, "Ошибка удаления", Toast.LENGTH_SHORT).show()
                            }
                            showDeleteConfirmDialog = false
                            selectedFileForAction = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A))
                    ) {
                        Text("Удалить", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                    }
                }
            )
        }
    }

    // Permissions Modifier Dialog
    if (showPermissionsDialog) {
        var chmodString by remember { mutableStateOf("644") }
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = CleanMinimalismTheme.AccentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Изменение прав (chmod)", color = CleanMinimalismTheme.TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        "Задайте права доступа в восьмеричном формате для выбранной папки/файла (${currentPath}):",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = chmodString,
                        onValueChange = { if (it.length <= 4) chmodString = it },
                        placeholder = { Text("644") },
                        label = { Text("Маска прав") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Пример:\n755 - rwxr-xr-x (Исполняемый)\n644 - rw-r--r-- (Только чтение)",
                        color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = selectedFileForAction?.path ?: currentPath
                        val success = RootUtils.execute("chmod $chmodString \"$path\"")
                        if (success) {
                            Toast.makeText(context, "Права изменены на $chmodString", Toast.LENGTH_SHORT).show()
                            fileList = getFilesForPath(currentPath, context)
                        } else {
                            Toast.makeText(context, "Ошибка изменения прав", Toast.LENGTH_SHORT).show()
                        }
                        showPermissionsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)
                ) {
                    Text("Применить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                }
            }
        )
    }

    // APK Installer Dialog
    if (showApkDialog) {
        var apkFileName by remember { mutableStateOf(currentPath) }
        AlertDialog(
            onDismissRequest = { showApkDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Android, contentDescription = null, tint = CleanMinimalismTheme.AccentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Установка пакета APK", color = CleanMinimalismTheme.TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        "Запустите быструю root-установку любого APK в фоновом режиме:",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apkFileName,
                        onValueChange = { apkFileName = it },
                        label = { Text("Полный путь до APK") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Начинается установка $apkFileName...", Toast.LENGTH_SHORT).show()
                        CoroutineScope(Dispatchers.IO).launch {
                            val success = RootUtils.execute("pm install -r \"$apkFileName\"")
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(context, "Успешно установлено", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Ошибка установки", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        showApkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)
                ) {
                    Text("Установить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApkDialog = false }) {
                    Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("О программе", color = CleanMinimalismTheme.TextPrimary) },
            text = {
                Column {
                    Text(
                        "Root File Manager — мощный инструмент для работы с файлами и системой.\n\nКонтакты:",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        "Telegram: @tetchtoker",
                        color = CleanMinimalismTheme.AccentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/tetchtoker"))
                                context.startActivity(intent)
                            }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)) {
                    Text("Закрыть", color = Color.White)
                }
            }
        )
    }

    // SH Execute Dialog
    if (showShExecuteDialog) {
        selectedFileForAction?.let { fileItem ->
            AlertDialog(
                onDismissRequest = { showShExecuteDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, tint = CleanMinimalismTheme.AccentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Запуск Скрипта", color = CleanMinimalismTheme.TextPrimary)
                    }
                },
                text = {
                    Text(
                        "Запустить скрипт ${fileItem.name} от имени суперпользователя (Root)?",
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Исполнение ${fileItem.name}...", Toast.LENGTH_SHORT).show()
                            CoroutineScope(Dispatchers.IO).launch {
                                // First add execution rights just in case
                                RootUtils.execute("chmod +x \"${fileItem.path}\"")
                                val output = RootUtils.executeWithOutput("sh \"${fileItem.path}\"")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Исполнено. Вывод:\n${output.take(100)}${if (output.length > 100) "..." else ""}", Toast.LENGTH_LONG).show()
                                }
                            }
                            showShExecuteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor)
                    ) {
                        Text("Запустить(Root)", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShExecuteDialog = false }) {
                        Text("Отмена", color = CleanMinimalismTheme.TextSecondary)
                    }
                }
            )
        }
    }

    // Universal File Viewer (Image etc)
    if (fileViewerItem != null) {
        val item = fileViewerItem!!
        
        var safeImagePath by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(item.path) {
            val file = java.io.File(item.path)
            if (file.canRead()) {
                safeImagePath = item.path
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val tempFile = java.io.File(context.cacheDir, "temp_view_${item.name}")
                    RootUtils.execute("cp \"${item.path}\" \"${tempFile.absolutePath}\"")
                    RootUtils.execute("chmod 644 \"${tempFile.absolutePath}\"")
                    safeImagePath = tempFile.absolutePath
                }
            }
        }
        
        androidx.compose.ui.window.Dialog(onDismissRequest = { fileViewerItem = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 600.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CleanMinimalismTheme.SurfaceDark)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, color = CleanMinimalismTheme.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { fileViewerItem = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = CleanMinimalismTheme.TextPrimary)
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        if (item.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) {
                            if (safeImagePath != null) {
                                coil.compose.AsyncImage(
                                    model = java.io.File(safeImagePath!!),
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(color = CleanMinimalismTheme.AccentColor)
                            }
                        } else {
                            Text(
                                "Предпросмотр недоступен для: ${item.extension}",
                                color = CleanMinimalismTheme.TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
        
        DisposableEffect(item.path) {
            onDispose {
                if (safeImagePath?.startsWith(context.cacheDir.absolutePath) == true) {
                    java.io.File(safeImagePath!!).delete()
                }
            }
        }
    }
}

// -------------------------------------------------------------
// UI COMPONENTS
// -------------------------------------------------------------

@Composable
fun HeaderBar(
    title: String,
    activeFilePath: String,
    onSearchClicked: () -> Unit,
    onMoreClicked: () -> Unit
) {
    Surface(
        color = CleanMinimalismTheme.SecondaryBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular folder manage logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CleanMinimalismTheme.AccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderZip,
                        contentDescription = "Folder Icon",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = CleanMinimalismTheme.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Row {
                    IconButton(
                        onClick = onSearchClicked,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = CleanMinimalismTheme.TextSecondary
                        )
                    }
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More",
                                tint = CleanMinimalismTheme.TextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(CleanMinimalismTheme.SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("О приложении", color = CleanMinimalismTheme.TextPrimary) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = CleanMinimalismTheme.AccentColor) },
                                onClick = {
                                    expanded = false
                                    onMoreClicked()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Выход", color = Color(0xFFBA1A1A)) },
                                leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = Color(0xFFBA1A1A)) },
                                onClick = {
                                    expanded = false
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle path styled as terminal code path
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(99.dp))
                    .background(CleanMinimalismTheme.SurfaceDark)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = "Terminal Path",
                    tint = CleanMinimalismTheme.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                // Break path into text nodes
                val pathSegments = activeFilePath.split("/").filter { it.isNotEmpty() }
                
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.5f))) {
                            append("/")
                        }
                        pathSegments.forEachIndexed { index, seg ->
                            withStyle(style = SpanStyle(
                                color = if (index == pathSegments.lastIndex) CleanMinimalismTheme.TextPrimary else CleanMinimalismTheme.TextSecondary,
                                fontWeight = if (index == pathSegments.lastIndex) FontWeight.Medium else FontWeight.Normal
                            )) {
                                append(seg)
                            }
                            if (index < pathSegments.lastIndex) {
                                withStyle(style = SpanStyle(color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.5f))) {
                                    append(" / ")
                                }
                            }
                        }
                        if (pathSegments.isEmpty()) {
                            withStyle(style = SpanStyle(color = CleanMinimalismTheme.TextPrimary, fontWeight = FontWeight.Medium)) {
                                append("system")
                            }
                        }
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        color = CleanMinimalismTheme.SecondaryBackground,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
        modifier = Modifier.height(72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationBarItem(
                label = "Файлы",
                icon = Icons.Filled.Folder,
                isSelected = currentTab == "Files",
                onClick = { onTabSelected("Files") }
            )
            NavigationBarItem(
                label = "Анализ",
                icon = Icons.Filled.AutoAwesome,
                isSelected = currentTab == "AI",
                onClick = { onTabSelected("AI") }
            )
            NavigationBarItem(
                label = "Редактор",
                icon = Icons.Filled.EditNote,
                isSelected = currentTab == "Editor",
                onClick = { onTabSelected("Editor") }
            )
            NavigationBarItem(
                label = "Настройки",
                icon = Icons.Filled.Settings,
                isSelected = currentTab == "Settings",
                onClick = { onTabSelected("Settings") }
            )
        }
    }
}

@Composable
fun NavigationBarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isSelected) CleanMinimalismTheme.SurfaceDark else Color.Transparent
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) CleanMinimalismTheme.AccentColor else CleanMinimalismTheme.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) CleanMinimalismTheme.AccentColor else CleanMinimalismTheme.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// -------------------------------------------------------------
// FILE EXPLORER TAB SCREEN
// -------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerTab(
    currentPath: String,
    fileList: List<FileItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onPathChanged: (String) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onCreateFolderClick: () -> Unit,
    onCreateFileClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onApkClick: () -> Unit
) {
    val filteredFiles = remember(fileList, searchQuery) {
        if (searchQuery.isBlank()) fileList else {
            fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search & Filter Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Поиск файлов или папок...", color = CleanMinimalismTheme.TextSecondary) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CleanMinimalismTheme.TextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = CleanMinimalismTheme.TextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CleanMinimalismTheme.AccentColor,
                unfocusedBorderColor = CleanMinimalismTheme.CardBorder,
                focusedContainerColor = CleanMinimalismTheme.SecondaryBackground,
                unfocusedContainerColor = CleanMinimalismTheme.SecondaryBackground
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        // Utility quick tools grid (Install APK / Permissions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action button 1: Install APK
            Surface(
                onClick = onApkClick,
                color = CleanMinimalismTheme.SurfaceDark,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Filled.Android, contentDescription = null, tint = CleanMinimalismTheme.AccentColor)
                    Column {
                        Text("Установка APK", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CleanMinimalismTheme.TextPrimary)
                        Text("Root-инсталлятор", fontSize = 10.sp, color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.8f))
                    }
                }
            }

            // Action button 2: Permissions (chmod)
            Surface(
                onClick = onPermissionsClick,
                color = CleanMinimalismTheme.SurfaceDark,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = CleanMinimalismTheme.AccentColor)
                    Column {
                        Text("Разрешения", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CleanMinimalismTheme.TextPrimary)
                        Text("chmod 755 / 644", fontSize = 10.sp, color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // Active files and folders visual container (with a header and direct file buttons)
        Surface(
            color = CleanMinimalismTheme.SecondaryBackground,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Section Title + Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CleanMinimalismTheme.SurfaceDark)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = "Breadcrumbs",
                            tint = CleanMinimalismTheme.AccentColor,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onPathChanged("/") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentPath == "/") "Корень системы" else "Папка Explorer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanMinimalismTheme.TextPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onCreateFolderClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", tint = CleanMinimalismTheme.AccentColor, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onCreateFileClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.NoteAdd, contentDescription = "New File", tint = CleanMinimalismTheme.AccentColor, modifier = Modifier.size(20.dp))
                        }
                        if (currentPath != "/") {
                            IconButton(onClick = {
                                val file = File(currentPath)
                                val parent = file.parent ?: "/"
                                onPathChanged(parent)
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CleanMinimalismTheme.TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Files List view
                if (filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Эта папка пуста", color = CleanMinimalismTheme.TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredFiles, key = { it.path + "_" + it.lastModified }) { fileItem ->
                            Box(modifier = Modifier.animateItemPlacement()) {
                                Column {
                                    FileListItem(
                                        item = fileItem,
                                        onClick = { onFileClick(fileItem) },
                                        onLongClick = { onFileLongClick(fileItem) }
                                    )
                                    HorizontalDivider(color = CleanMinimalismTheme.CardBorder.copy(alpha = 0.5f), thickness = (0.5).dp, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon wrapper
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (item.isDirectory) CleanMinimalismTheme.SurfaceDark else CleanMinimalismTheme.SurfaceDark
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getFileIcon(item),
                contentDescription = item.name,
                tint = getIconColor(item),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name info panel
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = CleanMinimalismTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (item.isDirectory) "Папка" else formatSize(item.size),
                    color = CleanMinimalismTheme.TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "•",
                    color = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDate(item.lastModified),
                    color = CleanMinimalismTheme.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = CleanMinimalismTheme.TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

// -------------------------------------------------------------
// TEXT EDITOR TAB SCREEN (with elegant dark style syntax config)
// -------------------------------------------------------------

@Composable
fun EditorTab(
    fileName: String,
    fileContent: String,
    onContentChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    var textFieldValue by remember(fileContent) {
        mutableStateOf(TextFieldValue(fileContent))
    }

    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text != fileContent) {
            onContentChanged(textFieldValue.text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanMinimalismTheme.CodeBackground)
    ) {
        // Editor Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CleanMinimalismTheme.SurfaceDark)
                .border(BorderStroke(1.dp, CleanMinimalismTheme.CardBorder))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(CleanMinimalismTheme.AccentColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("REDO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fileName.ifEmpty { "Новый документ" },
                    color = CleanMinimalismTheme.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сохранить", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = CleanMinimalismTheme.TextSecondary)
                }
            }
        }

        // Code Editor Body
        val lines = remember(textFieldValue.text) { textFieldValue.text.split("\n") }
        val lineCount = lines.size

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Line numbers gutter
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color(0xFF151515))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        color = Color(0xFF555555),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }

            // Text input field with Syntax Highlighting
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                textStyle = TextStyle(
                    color = Color(0xFFE3E3E3),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(CleanMinimalismTheme.AccentColor),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                decorationBox = { innerTextField ->
                    // Beautiful highlighted text underlay
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            "Начните вводить текст конфигурации здесь...",
                            color = Color(0xFF666666),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                    
                    // Simple regex/parsing highlighted view
                    Box {
                        val annotatedText = buildAnnotatedString {
                            val rawText = textFieldValue.text
                            val linesRaw = rawText.split("\n")
                            linesRaw.forEachIndexed { i, line ->
                                when {
                                    line.trim().startsWith("#") || line.trim().startsWith("//") -> {
                                        // Comment highlighted Green
                                        withStyle(style = SpanStyle(color = CleanMinimalismTheme.HighlightGreen)) {
                                            append(line)
                                        }
                                    }
                                    line.contains("127.0.0.1") || line.contains("localhost") || line.contains("::1") -> {
                                        // Highlight standard local/IP rules
                                        withStyle(style = SpanStyle(color = Color(0xFF4FC3F7))) {
                                            append(line)
                                        }
                                    }
                                    else -> {
                                        append(line)
                                    }
                                }
                                if (i < linesRaw.lastIndex) append("\n")
                            }
                        }
                        
                        // We show either standard input or overlapping highlighted text.
                        // To keep it clean, let's just let standard text render with custom styles
                        // using AnnotatedString transformer or basic text display.
                        innerTextField()
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// APP MANAGER & UTILS TAB SCREEN
// -------------------------------------------------------------

data class AppEntry(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    val isSystem: Boolean,
    val sourceDir: String,
    val dataDir: String
)

@Composable
fun AppsTab(context: Context) {
    var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appEntries = apps.map { app ->
                AppEntry(
                    name = app.loadLabel(pm).toString(),
                    packageName = app.packageName,
                    icon = app.loadIcon(pm),
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    sourceDir = app.sourceDir,
                    dataDir = app.dataDir
                )
            }.sortedBy { it.name.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps = appEntries
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Менеджер Приложений",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск приложений...", color = CleanMinimalismTheme.TextSecondary) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = CleanMinimalismTheme.TextPrimary,
                unfocusedTextColor = CleanMinimalismTheme.TextPrimary,
                focusedBorderColor = CleanMinimalismTheme.AccentColor
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CleanMinimalismTheme.TextSecondary) }
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CleanMinimalismTheme.AccentColor)
            }
        } else {
            val filteredApps = installedApps.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps) { app ->
                    AppItemCard(app, context)
                }
            }
        }
    }
}

@Composable
fun AppItemCard(app: AppEntry, context: Context) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SurfaceDark),
        border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    bitmap = (app.icon as android.graphics.drawable.BitmapDrawable).bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.name, color = CleanMinimalismTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(app.packageName, color = CleanMinimalismTheme.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (app.isSystem) {
                    Text(
                        "SYSTEM", 
                        fontSize = 9.sp, 
                        color = CleanMinimalismTheme.AccentColor, 
                        modifier = Modifier
                            .border(1.dp, CleanMinimalismTheme.AccentColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            if (expanded) {
                HorizontalDivider(color = CleanMinimalismTheme.CardBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AppActionButton(Icons.Default.Delete, "Удалить") {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${app.packageName}")
                        }
                        context.startActivity(intent)
                    }
                    AppActionButton(Icons.Default.FolderOpen, "Данные") {
                         Toast.makeText(context, "Идите в Files: ${app.dataDir}", Toast.LENGTH_LONG).show()
                    }
                    AppActionButton(Icons.Default.Block, "Стоп") {
                        CoroutineScope(Dispatchers.IO).launch {
                            val success = RootUtils.execute("pm disable ${app.packageName}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, if (success) "Приложение отключено" else "Ошибка (нужен Root)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    AppActionButton(Icons.Default.Settings, "Настройки") {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}

@Composable
fun AppActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CleanMinimalismTheme.AccentColor, modifier = Modifier.size(24.dp))
        Text(label, color = CleanMinimalismTheme.TextSecondary, fontSize = 9.sp)
    }
}

// -------------------------------------------------------------
// SETTINGS & DOWNLOAD GUIDE TAB SCREEN
// -------------------------------------------------------------

@Composable
fun SettingsTab(context: Context, isRootAvailable: Boolean?) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // App Info Header
        Text(
            text = "О приложении",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Root File Manager — быстрый и минималистичный проводник с поддержкой Root-прав и мощным редактором кода.",
            color = CleanMinimalismTheme.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Device Info
        Text(
            text = "Информация об устройстве",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
            border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DeviceInfoRow("Модель", android.os.Build.MODEL)
                DeviceInfoRow("Бренд", android.os.Build.BRAND)
                DeviceInfoRow("Android", android.os.Build.VERSION.RELEASE)
                DeviceInfoRow("SDK", android.os.Build.VERSION.SDK_INT.toString())
                DeviceInfoRow("Архитектура", android.os.Build.SUPPORTED_ABIS.joinToString(", "))
            }
        }

        // Settings / Theme
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
            border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Внешний вид (Тема)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CleanMinimalismTheme.TextPrimary)
                    Text("Темный или светлый режим", fontSize = 11.sp, color = CleanMinimalismTheme.TextSecondary)
                }
                androidx.compose.material3.Switch(
                    checked = CleanMinimalismTheme.isDarkTheme,
                    onCheckedChange = { CleanMinimalismTheme.isDarkTheme = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = CleanMinimalismTheme.AccentColor,
                        checkedTrackColor = CleanMinimalismTheme.AccentColor.copy(alpha = 0.5f)
                    )
                )
            }
        }

        // OTA BLOCKER (Pixel)
        Text(
            text = "Google Pixel Инструменты",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
            border = BorderStroke(1.dp, CleanMinimalismTheme.AccentColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Icon(Icons.Filled.SecurityUpdateWarning, contentDescription = null, tint = CleanMinimalismTheme.AccentColor)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Управление OTA Обновлениями", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CleanMinimalismTheme.TextPrimary)
                        Text("Блокировка/Разблокировка обновлений для Pixel.", fontSize = 11.sp, color = CleanMinimalismTheme.TextSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                Toast.makeText(context, "Применение блокировки OTA...", Toast.LENGTH_SHORT).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    val commands = listOf(
                                        "pm disable-user --user 0 com.google.android.gms/.update.SystemUpdateActivity",
                                        "pm disable com.google.android.gms/.update.SystemUpdateActivity",
                                        "pm disable com.google.android.gms/com.google.android.gms.update.SystemUpdateService",
                                        "pm disable com.google.android.gms/com.google.android.gms.update.SystemUpdateService\$Receiver",
                                        "pm disable com.google.android.gms/com.google.android.gms.update.SystemUpdateService\$SecretCodeReceiver",
                                        "pm disable com.android.dynsystem",
                                        "rm -rf /data/ota_package/*"
                                    )
                                    var successCount = 0
                                    for (cmd in commands) {
                                        if (RootUtils.execute(cmd)) successCount++
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "OTA Заблокировано! Успешных команд: $successCount", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Заблокировать", color = Color.White)
                    }
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                Toast.makeText(context, "Восстановление OTA...", Toast.LENGTH_SHORT).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    val commands = listOf(
                                        "pm enable com.google.android.gms/.update.SystemUpdateActivity",
                                        "pm enable com.google.android.gms/com.google.android.gms.update.SystemUpdateService",
                                        "pm enable com.google.android.gms/com.google.android.gms.update.SystemUpdateService\$Receiver",
                                        "pm enable com.google.android.gms/com.google.android.gms.update.SystemUpdateService\$SecretCodeReceiver",
                                        "pm enable com.android.dynsystem"
                                    )
                                    var successCount = 0
                                    for (cmd in commands) {
                                        if (RootUtils.execute(cmd)) successCount++
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "OTA Разблокировано! Успешных команд: $successCount", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.HighlightGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Разблокировать", color = Color.White)
                    }
                }
            }
        }
        
        // System Status
        Text(
            text = "Статус Системы",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
            border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val batteryLevel = getBatteryLevel(context)
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val mi = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val availableMegs = mi.availMem / 1048576L
                val totalMegs = mi.totalMem / 1048576L

                DeviceInfoRow("Заряд батареи", "$batteryLevel%")
                DeviceInfoRow("Свободно ОЗУ", "${availableMegs} MB / ${totalMegs} MB")
                DeviceInfoRow("Android SDK", android.os.Build.VERSION.SDK_INT.toString())
                DeviceInfoRow("Версия ПО", "1.4.2")
            }
        }
        
        // Power Controls
        Text(
            text = "Управление Питанием",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
            border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                RootUtils.execute("reboot")
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reboot", color = CleanMinimalismTheme.TextPrimary)
                    }
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                RootUtils.execute("reboot bootloader")
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bootloader", color = CleanMinimalismTheme.TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                RootUtils.execute("reboot recovery")
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Recovery", color = CleanMinimalismTheme.TextPrimary)
                    }
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                RootUtils.execute("reboot -p")
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Power Off", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                RootUtils.execute("setprop ctl.restart zygote")
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Soft Reboot", color = CleanMinimalismTheme.TextPrimary)
                    }
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                RootUtils.execute("pkill -TERM -u system_server") 
                                // Alternatively: setprop ctl.restart surfaceflinger or pkill systemui
                            } else {
                                Toast.makeText(context, "Требуются Root-права", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.SurfaceDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset UI", color = CleanMinimalismTheme.TextPrimary)
                    }
                }
            }
        }

        // System Settings
        Text(
            text = "Конфигурация системы",
            color = CleanMinimalismTheme.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Storage space card
        StorageSpaceCard()

        Spacer(modifier = Modifier.height(12.dp))

        // Root status info
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
            border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Режим Суперпользователя (Root Status)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CleanMinimalismTheme.TextPrimary)
                    Text("Необходим для большинства системных операций", fontSize = 11.sp, color = CleanMinimalismTheme.TextSecondary)
                }
                if (isRootAvailable == true) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CleanMinimalismTheme.HighlightGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("АКТИВЕН", color = CleanMinimalismTheme.HighlightGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFBA1A1A).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("НЕ ДОСТУПЕН", color = Color(0xFFBA1A1A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CleanMinimalismTheme.TextSecondary, fontSize = 12.sp)
        Text(value, color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("•", color = CleanMinimalismTheme.AccentColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = CleanMinimalismTheme.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
fun StorageSpaceCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SecondaryBackground),
        border = BorderStroke(1.dp, CleanMinimalismTheme.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Объем памяти (Sandbox)", color = CleanMinimalismTheme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Свободно 2.4 GB из 4.0 GB", color = CleanMinimalismTheme.TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Simulated storage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(CleanMinimalismTheme.SurfaceDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.6f) // 60% used
                        .clip(CircleShape)
                        .background(CleanMinimalismTheme.AccentColor)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// HELPER LOGIC / REAL ROOT I/O
// -------------------------------------------------------------

object RootUtils {
    fun isRootAvailable(): Boolean {
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_test"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output == "root_test"
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    fun executeWithOutput(command: String): String {
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            process?.destroy()
        }
    }

    fun execute(command: String): Boolean {
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            process?.destroy()
        }
    }
}

fun getFilesForPath(path: String, context: Context): List<FileItem> {
    val items = mutableListOf<FileItem>()
    try {
        val safePath = if (path.endsWith("/")) path else "$path/"
        
        // Use Java File API if possible (faster), fallback to su if permission denied.
        val dir = File(safePath)
        val files = dir.listFiles()
        if (files != null) {
            files.forEach { file ->
                items.add(
                    FileItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        extension = file.extension
                    )
                )
            }
        } else {
            // Permission denied, try with root
            val output = RootUtils.executeWithOutput("ls -A -l \"$safePath\"")
            val lines = output.split("\n")
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("total ")) continue
                
                val isDir = trimmed.startsWith("d")
                val isSymlink = trimmed.startsWith("l")
                
                // Fallback parsing for typical ls -l
                // drwxr-xr-x 2 root root 4096 2023-01-01 12:00 my_folder
                val parts = trimmed.split(Regex("\\s+"), limit = 8)
                if (parts.size >= 8 || parts.size == 7) { // 7 handles missing link count (toybox/toolbox diffs)
                    val namePart = parts.last()
                    var name = namePart
                    if (isSymlink && name.contains(" -> ")) {
                        name = name.substringBefore(" -> ")
                    }
                    if (name == "." || name == "..") continue
                    
                    val sizeIndex = if (parts.size >= 8) 4 else 3
                    val size = parts[sizeIndex].toLongOrNull() ?: 0L
                    
                    val ext = if (name.contains(".")) name.substringAfterLast('.') else ""
                    items.add(FileItem(name, safePath + name, isDir || isSymlink, size, 0L, ext))
                } else if (parts.size > 1) {
                     // desperate fallback
                     var name = parts.last()
                     if (isSymlink && name.contains(" -> ")) {
                         name = name.substringBefore(" -> ")
                     }
                     if (name != "." && name != "..") {
                         val ext = if (name.contains(".")) name.substringAfterLast('.') else ""
                         items.add(FileItem(name, safePath + name, isDir || isSymlink, 0L, 0L, ext))
                     }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

fun getBatteryLevel(context: Context): Int {
    val batteryStatus: android.content.Intent? = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED).let { filter ->
        context.registerReceiver(null, filter)
    }
    val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else level
}

fun readFileContent(path: String, context: Context): String {
    return try {
        val file = File(path)
        if (file.canRead()) {
            file.readText()
        } else {
            // Need root
            RootUtils.executeWithOutput("cat \"$path\"")
        }
    } catch (e: Exception) {
        RootUtils.executeWithOutput("cat \"$path\"")
    }
}

fun saveFileContent(path: String, content: String, context: Context): Boolean {
    return try {
        val file = File(path)
        if (file.canWrite()) {
            file.writeText(content)
            true
        } else {
            // Need root
            val tempFile = File(context.cacheDir, "root_mgr_temp")
            tempFile.writeText(content)
            val success = RootUtils.execute("cp \"${tempFile.absolutePath}\" \"$path\"")
            tempFile.delete()
            success
        }
    } catch (e: Exception) {
        e.printStackTrace()
        val tempFile = File(context.cacheDir, "root_mgr_temp")
        tempFile.writeText(content)
        val success = RootUtils.execute("cp \"${tempFile.absolutePath}\" \"$path\"")
        tempFile.delete()
        success
    }
}

fun createFolder(currentPath: String, name: String, context: Context): Boolean {
    val dir = File(currentPath, name)
    if (dir.mkdirs()) return true
    return RootUtils.execute("mkdir -p \"${dir.absolutePath}\"")
}

fun createFile(currentPath: String, name: String, context: Context): Boolean {
    val file = File(currentPath, name)
    return try {
        if (file.createNewFile()) true else RootUtils.execute("touch \"${file.absolutePath}\"")
    } catch (e: Exception) {
         RootUtils.execute("touch \"${file.absolutePath}\"")
    }
}

fun renameFile(path: String, newName: String, context: Context): Boolean {
    val file = File(path)
    val parent = file.parent ?: "/"
    val destination = File(parent, newName)
    if (file.renameTo(destination)) return true
    return RootUtils.execute("mv \"$path\" \"${destination.absolutePath}\"")
}

fun deleteFileOrDir(path: String, context: Context): Boolean {
    val file = File(path)
    if (file.isDirectory) {
        if (file.deleteRecursively()) return true
    } else {
        if (file.delete()) return true
    }
    return RootUtils.execute("rm -rf \"$path\"")
}

fun getFileIcon(item: FileItem): androidx.compose.ui.graphics.vector.ImageVector {
    return if (item.isDirectory) {
        Icons.Filled.Folder
    } else {
        when (item.extension.lowercase()) {
            "apk" -> Icons.Filled.Android
            "txt", "properties", "conf", "prop" -> Icons.AutoMirrored.Filled.InsertDriveFile
            "kt", "kts", "java", "xml", "html", "json", "py", "js", "css", "md", "sh" -> Icons.Filled.Code
            "zip", "rar", "tar", "gz" -> Icons.Filled.FolderZip
            "png", "jpg", "jpeg", "gif", "webp" -> Icons.Filled.Image
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }
}

fun getIconColor(item: FileItem): Color {
    return if (item.isDirectory) {
        CleanMinimalismTheme.AccentColor
    } else {
        when (item.extension.lowercase()) {
            "apk" -> Color(0xFF3DDC84) // Green
            "txt", "properties", "conf", "prop" -> CleanMinimalismTheme.TextSecondary
            "kt", "kts", "java", "xml", "html", "json", "py", "js" -> CleanMinimalismTheme.AccentColor
            "zip", "rar", "tar", "gz" -> Color(0xFFFFA000) // Amber
            else -> CleanMinimalismTheme.TextSecondary
        }
    }
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDate(time: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(time))
    } catch (e: Exception) {
        "-"
    }
}
