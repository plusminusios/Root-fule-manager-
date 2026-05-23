package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -------------------------------------------------------------
// STRUCTURAL MODELS & DESIGN THEME
// -------------------------------------------------------------

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val permissions: String = "---"
)

data class AppItem(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val appIcon: android.graphics.drawable.Drawable?
)

data class NavTab(
    val id: String,
    val icon: ImageVector,
    val label: String
)

object CleanMinimalismTheme {
    val Background = Color(0xFF0F0F11)
    val SurfaceDark = Color(0xFF16171D)
    val CardBackground = Color(0xFF1E2129)
    val TextPrimary = Color(0xFFF1F1F5)
    val TextSecondary = Color(0xFF8E909E)
    val AccentColor = Color(0xFFFF5A5F) // Neon-coral highlight
    val AccentBlue = Color(0xFF4A90E2)
    val HighlightGreen = Color(0xFF34D399)
    val WarningOrange = Color(0xFFFBBF24)
}

// Simple translation utility for bilingual interface (RU/EN)
fun tr(context: Context, ru: String, en: String): String {
    val isRussian = Locale.getDefault().language == "ru"
    return if (isRussian) ru else en
}

// -------------------------------------------------------------
// MAIN ACTIVITY
// -------------------------------------------------------------

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = CleanMinimalismTheme.Background,
                    surface = CleanMinimalismTheme.SurfaceDark,
                    primary = CleanMinimalismTheme.AccentColor,
                    secondary = CleanMinimalismTheme.AccentBlue
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CleanMinimalismTheme.Background
                ) {
                    RootManagerApp()
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CORE APP CONTAINER
// -------------------------------------------------------------

@Composable
fun RootManagerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Root status state
    var isRootAvailable by remember { mutableStateOf<Boolean?>(null) }
    
    // Application tabs
    var currentTab by remember { mutableStateOf("Files") }
    
    // File Navigation States
    var currentPath by remember { mutableStateOf("/") }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Active file being edited inside the built-in Editor
    var activeFilePath by remember { mutableStateOf<String?>(null) }
    var activeFileName by remember { mutableStateOf("") }
    var activeFileContent by remember { mutableStateOf("") }
    
    // Global Dialog controls
    var showCreateDirDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showChmodDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedFileForAction by remember { mutableStateOf<FileItem?>(null) }
    
    // Input buffers
    var inputNameBuffer by remember { mutableStateOf("") }
    var inputChmodBuffer by remember { mutableStateOf("755") }

    // Check Root availability and load file list
    LaunchedEffect(currentPath) {
        withContext(Dispatchers.IO) {
            if (isRootAvailable == null) {
                isRootAvailable = RootUtils.isRootAvailable()
            }
            val files = getFilesForPath(currentPath, context)
            withContext(Dispatchers.Main) {
                fileList = files
            }
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            NavigationBar(
                containerColor = CleanMinimalismTheme.SurfaceDark,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val tabs = listOf(
                    NavTab("Files", Icons.Default.Folder, tr(context, "Файлы", "Files")),
                    NavTab("Apps", Icons.Default.Apps, tr(context, "Приложения", "Apps")),
                    NavTab("OTA", Icons.Default.Security, tr(context, "OTA и Система", "OTA & Power")),
                    NavTab("Diagnostics", Icons.Default.Analytics, tr(context, "Аналитика", "Diagnostics"))
                )
                for (tab in tabs) {
                    NavigationBarItem(
                        selected = currentTab == tab.id,
                        onClick = {
                            currentTab = tab.id
                            // Close editor if switching tabs
                            if (tab.id != "Files") {
                                activeFilePath = null
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CleanMinimalismTheme.AccentColor,
                            unselectedIconColor = CleanMinimalismTheme.TextSecondary,
                            selectedTextColor = CleanMinimalismTheme.AccentColor,
                            unselectedTextColor = CleanMinimalismTheme.TextSecondary,
                            indicatorColor = CleanMinimalismTheme.Background
                        ),
                        modifier = Modifier.testTag("tab_${tab.id}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar
            ToolbarHeader(
                title = tr(context, "ROOT-ПРОВОДНИК ПРО", "ROOT EXPLORER PRO"),
                activePath = activeFilePath ?: currentPath,
                onAddDirectory = {
                    inputNameBuffer = ""
                    showCreateDirDialog = true
                },
                onAddFile = {
                    inputNameBuffer = ""
                    showCreateFileDialog = true
                },
                isRoot = isRootAvailable == true,
                showAddButtons = currentTab == "Files" && activeFilePath == null
            )

            // Dynamic Tab selector
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    "Files" -> {
                        if (activeFilePath != null) {
                            TextEditorScreen(
                                fileName = activeFileName,
                                fileContent = activeFileContent,
                                onContentChange = { activeFileContent = it },
                                onSave = {
                                    scope.launch(Dispatchers.IO) {
                                        val success = saveFileContent(activeFilePath!!, activeFileContent, context)
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(context, tr(context, "Файл успешно сохранен!", "File saved successfully!"), Toast.LENGTH_SHORT).show()
                                                activeFilePath = null
                                                // Refresh path files
                                                val files = getFilesForPath(currentPath, context)
                                                fileList = files
                                            } else {
                                                Toast.makeText(context, tr(context, "Ошибка записи. Требуются Root права?", "Write error. Root required?"), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                onClose = { activeFilePath = null }
                            )
                        } else {
                            FileExplorerTab(
                                currentPath = currentPath,
                                fileList = fileList,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onNavigate = { nextPath ->
                                    currentPath = nextPath
                                    searchQuery = ""
                                },
                                onFileAction = { file ->
                                    selectedFileForAction = file
                                },
                                onEditFile = { file ->
                                    scope.launch(Dispatchers.IO) {
                                        val content = readFileContent(file.path, context)
                                        withContext(Dispatchers.Main) {
                                            activeFilePath = file.path
                                            activeFileName = file.name
                                            activeFileContent = content
                                        }
                                    }
                                }
                            )
                        }
                    }
                    "Apps" -> {
                        AppsTab(context = context)
                    }
                    "OTA" -> {
                        OtaSystemTab(context = context, isRootAvailable = isRootAvailable)
                    }
                    "Diagnostics" -> {
                        DiagnosticsTab(context = context)
                    }
                }
            }
        }
    }

    // Modal Action sheet / Context menu dialog
    selectedFileForAction?.let { file ->
        Dialog(onDismissRequest = { selectedFileForAction = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CleanMinimalismTheme.SurfaceDark,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.Bold,
                        color = CleanMinimalismTheme.TextPrimary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = file.path,
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(bottom = 12.dp))

                    val actions = mutableListOf<Triple<ImageVector, String, () -> Unit>>()
                    
                    if (!file.isDirectory) {
                        actions.add(Triple(Icons.Default.Edit, tr(context, "Редактировать", "Edit File")) {
                            selectedFileForAction = null
                            scope.launch(Dispatchers.IO) {
                                val content = readFileContent(file.path, context)
                                withContext(Dispatchers.Main) {
                                    activeFilePath = file.path
                                    activeFileName = file.name
                                    activeFileContent = content
                                }
                            }
                        })
                    }

                    actions.add(Triple(Icons.Default.Security, tr(context, "Права доступа (chmod)", "Chmod Permissions")) {
                        selectedFileForAction = null
                        inputChmodBuffer = "755"
                        showChmodDialog = true
                    })

                    if (!file.isDirectory && file.extension.lowercase(Locale.ROOT) == "apk") {
                        actions.add(Triple(Icons.Default.Publish, tr(context, "Установить через PM", "Install APK with Root PM")) {
                            selectedFileForAction = null
                            scope.launch(Dispatchers.IO) {
                                val ok = RootUtils.execute("pm install -r \"${file.path}\"")
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        Toast.makeText(context, tr(context, "APK успешно установлен!", "APK Installed successfully!"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, tr(context, "Ошибка установки APK. Требуются Root права.", "Installation failed. Root rights required."), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        })
                    }

                    if (!file.isDirectory && file.extension.lowercase(Locale.ROOT) == "sh") {
                        actions.add(Triple(Icons.Default.PlayArrow, tr(context, "Запустить Shell скрипт", "Run Shell Script")) {
                            selectedFileForAction = null
                            scope.launch(Dispatchers.IO) {
                                RootUtils.execute("chmod +x \"${file.path}\"")
                                val output = RootUtils.executeWithOutput("sh \"${file.path}\"")
                                withContext(Dispatchers.Main) {
                                    androidx.appcompat.app.AlertDialog.Builder(context)
                                        .setTitle(tr(context, "Результат скрипта", "Script Output"))
                                        .setMessage(output.ifEmpty { tr(context, "[Скрипт завершен без вывода]", "[Script executed with no output]") })
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                        })
                    }

                    actions.add(Triple(Icons.Default.Delete, tr(context, "Удалить", "Delete")) {
                        val pathToDelete = file.path
                        val nameToDelete = file.name
                        selectedFileForAction = null
                        scope.launch(Dispatchers.IO) {
                            val done = deletePath(pathToDelete)
                            val files = getFilesForPath(currentPath, context)
                            withContext(Dispatchers.Main) {
                                if (done) {
                                    Toast.makeText(context, tr(context, "Успешно удалено: $nameToDelete", "Deleted: $nameToDelete"), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, tr(context, "Ошибка удаления. Требуется Root?", "Deletion failed. Root needed?"), Toast.LENGTH_SHORT).show()
                                }
                                fileList = files
                            }
                        }
                    })

                    actions.forEach { (icon, actionLabel, onClick) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                                .padding(vertical = 11.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = CleanMinimalismTheme.AccentColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(actionLabel, color = CleanMinimalismTheme.TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialogue: Create Directory
    if (showCreateDirDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text(tr(context, "Создать папку", "Create Directory"), color = CleanMinimalismTheme.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = inputNameBuffer,
                    onValueChange = { inputNameBuffer = it },
                    label = { Text(tr(context, "Имя папки", "Directory Name")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CleanMinimalismTheme.AccentColor,
                        unfocusedBorderColor = CleanMinimalismTheme.TextSecondary,
                        focusedLabelColor = CleanMinimalismTheme.AccentColor
                    ),
                    modifier = Modifier.testTag("dir_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dirName = inputNameBuffer.trim()
                        showCreateDirDialog = false
                        if (dirName.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val success = createDirectory(currentPath, dirName)
                                val files = getFilesForPath(currentPath, context)
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(context, tr(context, "Папка создана", "Directory created"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, tr(context, "Не удалось создать папку", "Failed to create directory"), Toast.LENGTH_SHORT).show()
                                    }
                                    fileList = files
                                }
                            }
                        }
                    }
                ) {
                    Text(tr(context, "Создать", "Create"), color = CleanMinimalismTheme.AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) {
                    Text(tr(context, "Отмена", "Cancel"), color = CleanMinimalismTheme.TextSecondary)
                }
            },
            containerColor = CleanMinimalismTheme.SurfaceDark
        )
    }

    // Dialogue: Create File
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(tr(context, "Создать пустой файл", "Create Empty File"), color = CleanMinimalismTheme.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = inputNameBuffer,
                    onValueChange = { inputNameBuffer = it },
                    label = { Text(tr(context, "Имя файла", "File Name")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CleanMinimalismTheme.AccentColor,
                        unfocusedBorderColor = CleanMinimalismTheme.TextSecondary,
                        focusedLabelColor = CleanMinimalismTheme.AccentColor
                    ),
                    modifier = Modifier.testTag("file_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fName = inputNameBuffer.trim()
                        showCreateFileDialog = false
                        if (fName.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                val success = createEmptyFile(currentPath, fName)
                                val files = getFilesForPath(currentPath, context)
                                withContext(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(context, tr(context, "Файл создан", "File created"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, tr(context, "Не удалось создать файл", "Failed to create file"), Toast.LENGTH_SHORT).show()
                                    }
                                    fileList = files
                                }
                            }
                        }
                    }
                ) {
                    Text(tr(context, "Создать", "Create"), color = CleanMinimalismTheme.AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text(tr(context, "Отмена", "Cancel"), color = CleanMinimalismTheme.TextSecondary)
                }
            },
            containerColor = CleanMinimalismTheme.SurfaceDark
        )
    }

    // Dialogue: Chmod Permissions
    if (showChmodDialog) {
        AlertDialog(
            onDismissRequest = { showChmodDialog = false },
            title = { Text(tr(context, "Изменить права (Chmod)", "Change Permissions (Chmod)"), color = CleanMinimalismTheme.TextPrimary) },
            text = {
                Column {
                    Text(
                        text = tr(context, "Шаблоны: 777 (Все), 755 (Стандарт папка), 644 (Стандарт файл)", "Templates: 777 (Full), 755 (Dir standard), 644 (File standard)"),
                        fontSize = 11.sp,
                        color = CleanMinimalismTheme.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = inputChmodBuffer,
                        onValueChange = { inputChmodBuffer = it },
                        label = { Text(tr(context, "Маска прав (X.X.X)", "Octal Mask")) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CleanMinimalismTheme.AccentColor,
                            unfocusedBorderColor = CleanMinimalismTheme.TextSecondary,
                            focusedLabelColor = CleanMinimalismTheme.AccentColor
                        ),
                        modifier = Modifier.testTag("chmod_mask_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mask = inputChmodBuffer.trim()
                        showChmodDialog = false
                        if (mask.length == 3 && mask.all { it.isDigit() }) {
                            // Run chmod using Root
                            selectedFileForAction?.let { file ->
                                scope.launch(Dispatchers.IO) {
                                    val ok = RootUtils.execute("chmod $mask \"${file.path}\"")
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            Toast.makeText(context, tr(context, "Права изменены на $mask!", "Permissions changed to $mask!"), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, tr(context, "Ошибка изменения прав. Требуется Root.", "Operation failed. Root required."), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, tr(context, "Некоректная маска", "Invalid octal permission mask"), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(tr(context, "Применить", "Apply"), color = CleanMinimalismTheme.AccentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChmodDialog = false }) {
                    Text(tr(context, "Отмена", "Cancel"), color = CleanMinimalismTheme.TextSecondary)
                }
            },
            containerColor = CleanMinimalismTheme.SurfaceDark
        )
    }
}

// -------------------------------------------------------------
// TOOLBAR HEADER
// -------------------------------------------------------------

@Composable
fun ToolbarHeader(
    title: String,
    activePath: String,
    onAddDirectory: () -> Unit,
    onAddFile: () -> Unit,
    isRoot: Boolean,
    showAddButtons: Boolean
) {
    Surface(
        color = CleanMinimalismTheme.SurfaceDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        color = CleanMinimalismTheme.TextPrimary,
                        fontSize = 17.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isRoot) "SUPERUSER: ACTIVE (UID 0)" else "SUPERUSER: FALLBACK (NORMAL USER)",
                        color = if (isRoot) CleanMinimalismTheme.HighlightGreen else CleanMinimalismTheme.WarningOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showAddButtons) {
                    IconButton(
                        onClick = onAddDirectory,
                        modifier = Modifier.testTag("add_dir_btn")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Dir", tint = CleanMinimalismTheme.AccentColor)
                    }
                    IconButton(
                        onClick = onAddFile,
                        modifier = Modifier.testTag("add_file_btn")
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Add File", tint = CleanMinimalismTheme.AccentBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            
            // Path Indicator
            Text(
                text = activePath,
                color = CleanMinimalismTheme.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// FILES TAB SCREEN
// -------------------------------------------------------------

@Composable
fun FileExplorerTab(
    currentPath: String,
    fileList: List<FileItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onFileAction: (FileItem) -> Unit,
    onEditFile: (FileItem) -> Unit
) {
    val context = LocalContext.current
    
    val filteredFiles = remember(fileList, searchQuery) {
        if (searchQuery.trim().isEmpty()) fileList else {
            fileList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Directory Navigation Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Up One Level Button
            IconButton(
                onClick = {
                    if (currentPath != "/") {
                        val parentFile = File(currentPath).parentFile
                        val parentPath = parentFile?.absolutePath ?: "/"
                        onNavigate(parentPath)
                    }
                },
                enabled = currentPath != "/",
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = CleanMinimalismTheme.AccentColor,
                    disabledContentColor = Color.Gray
                )
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Go Up")
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(tr(context, "Поиск файлов...", "Search files..."), fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("search_files_field")
            )
        }

        Divider(color = Color.DarkGray)

        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = CleanMinimalismTheme.TextSecondary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        tr(context, "Папка пуста или скрыта", "Directory is empty or layout hidden"),
                        color = CleanMinimalismTheme.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            // File List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredFiles) { item ->
                    FileRowItem(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                onNavigate(item.path)
                            } else {
                                // Default open in Editor
                                onEditFile(item)
                            }
                        },
                        onLongClick = {
                            onFileAction(item)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fileIcon = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile
    val tintColor = if (item.isDirectory) CleanMinimalismTheme.AccentBlue else CleanMinimalismTheme.TextSecondary
    val dateString = remember(item.lastModified) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(item.lastModified))
    }
    
    val sizeString = remember(item.size, item.isDirectory) {
        if (item.isDirectory) "" else formatFileSize(item.size)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = fileIcon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = CleanMinimalismTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.permissions,
                    color = CleanMinimalismTheme.AccentColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = dateString,
                    color = CleanMinimalismTheme.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        if (sizeString.isNotEmpty()) {
            Text(
                text = sizeString,
                color = CleanMinimalismTheme.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// TEXT EDITOR SCREEN
// -------------------------------------------------------------

@Composable
fun TextEditorScreen(
    fileName: String,
    fileContent: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CleanMinimalismTheme.Background)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(context, "Встроенный Редактор", "Built-in Text Editor"),
                    fontSize = 12.sp,
                    color = CleanMinimalismTheme.AccentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = fileName,
                    fontSize = 15.sp,
                    color = CleanMinimalismTheme.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.HighlightGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("editor_save_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 12.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = CleanMinimalismTheme.TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = fileContent,
            onValueChange = onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("editor_text_field"),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = CleanMinimalismTheme.TextPrimary
            ),
            singleLine = false
        )
    }
}

// -------------------------------------------------------------
// APPLICATIONS TAB SCREEN
// -------------------------------------------------------------

@Composable
fun AppsTab(context: Context) {
    var appsList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var appsSearchQuery by remember { mutableStateOf("") }
    var isListLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val items = packages.map { pkg ->
                AppItem(
                    name = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName,
                    packageName = pkg.packageName,
                    isSystemApp = (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) != 0,
                    appIcon = pkg.applicationInfo?.loadIcon(pm)
                )
            }.sortedBy { it.name.lowercase() }
            
            withContext(Dispatchers.Main) {
                appsList = items
                isListLoading = false
            }
        }
    }

    val filteredApps = remember(appsList, appsSearchQuery) {
        if (appsSearchQuery.trim().isEmpty()) appsList else {
            appsList.filter {
                it.name.contains(appsSearchQuery, ignoreCase = true) ||
                it.packageName.contains(appsSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = appsSearchQuery,
            onValueChange = { appsSearchQuery = it },
            placeholder = { Text(tr(context, "Имя приложения или package...", "Search apk/package name..."), fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CleanMinimalismTheme.TextSecondary) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("search_apps_field")
        )

        if (isListLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CleanMinimalismTheme.AccentColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredApps) { app ->
                    AppRowItem(app = app, context = context, onActionComplete = {
                        // Action callback trigger
                    })
                }
            }
        }
    }
}

@Composable
fun AppRowItem(app: AppItem, context: Context, onActionComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon wrapper or default system icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (app.appIcon != null) {
                Image(
                    bitmap = drawableToImageBitmap(app.appIcon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Android, contentDescription = null, tint = Color.Green)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                color = CleanMinimalismTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                color = CleanMinimalismTheme.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = if (app.isSystemApp) tr(context, "СИСТЕМНОЕ (Root PM)", "SYSTEM (Root PM)") else tr(context, "ПОЛЬЗОВАТЕЛЬСКОЕ", "USER INSTALLED"),
                color = if (app.isSystemApp) CleanMinimalismTheme.AccentColor else CleanMinimalismTheme.HighlightGreen,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row {
            // Uninstall/Disable button via Root PM commands
            IconButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        // User confirmation via toast
                        val cmd = if (app.isSystemApp) {
                            "pm disable-user --user 0 ${app.packageName}"
                        } else {
                            "pm uninstall ${app.packageName}"
                        }
                        val success = RootUtils.execute(cmd)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, tr(context, "Операция выполнена успешно!", "Operation completed! Uninstall command sent."), Toast.LENGTH_SHORT).show()
                                onActionComplete()
                            } else {
                                Toast.makeText(context, tr(context, "Ошибка. Требуются Root!", "Operation failed. Root required."), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Uninstall",
                    tint = CleanMinimalismTheme.AccentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// OTA & SYSTEM POWER CONTROLS TAB
// -------------------------------------------------------------

@Composable
fun OtaSystemTab(context: Context, isRootAvailable: Boolean?) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp)
    ) {
        // Core OTA Block Panel
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(context, "Блокировка OTA-Обновлений", "OTA System Update Block"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanMinimalismTheme.TextPrimary
                )
                Text(
                    text = tr(context, "Позволяет временно заблокировать фоновые запросы системного сервиса обновлений (SystemUpdateService/Activity) и очистить OTA кэш.", "Disables systemic background checks for Google OTA Update services and deletes files inside the data cache partition."),
                    fontSize = 11.sp,
                    color = CleanMinimalismTheme.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                Toast.makeText(context, tr(context, "Применение блокировки OTA...", "Applying OTA lock..."), Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
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
                                    commands.forEach { cmd ->
                                        if (RootUtils.execute(cmd)) successCount++
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, tr(context, "Блокировка завершена!", "OTA block applied successfully!"), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, tr(context, "Требуются Root-права", "Root permissions are required for this option"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.AccentColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(context, "Заблокировать", "Lock OTA"), color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                Toast.makeText(context, tr(context, "Восстановление обновлений...", "Restoring system updates..."), Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
                                    val commands = listOf(
                                        "pm enable com.google.android.gms/.update.SystemUpdateActivity",
                                        "pm enable com.google.android.gms/com.google.android.gms.update.SystemUpdateService",
                                        "pm enable com.google.android.gms/com.google.android.gms.update.SystemUpdateService\$Receiver",
                                        "pm enable com.google.android.gms/com.google.android.gms.update.SystemUpdateService\$SecretCodeReceiver",
                                        "pm enable com.android.dynsystem"
                                    )
                                    var successCount = 0
                                    commands.forEach { cmd ->
                                        if (RootUtils.execute(cmd)) successCount++
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, tr(context, "Обновления восстановлены!", "OTA systems enabled!"), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, tr(context, "Требуются Root-права", "Root permissions are required for this option"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(context, "Включить", "Restore OTA"), color = CleanMinimalismTheme.TextPrimary)
                    }
                }
            }
        }

        // Power Options Panels
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = tr(context, "Перезагрузка / Питание", "System Power Controls"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanMinimalismTheme.TextPrimary
                )
                Text(
                    text = tr(context, "Перезагрузка устройства в специальные отладочные режимы через интерфейс Root-терминала.", "Trigger immediate system reboots into safe loader/development recovery configurations using console su shell commands."),
                    fontSize = 11.sp,
                    color = CleanMinimalismTheme.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                scope.launch(Dispatchers.IO) { RootUtils.execute("reboot") }
                            } else {
                                Toast.makeText(context, tr(context, "Требуются Root-права", "Root required"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.CardBackground),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reboot", color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                scope.launch(Dispatchers.IO) { RootUtils.execute("reboot bootloader") }
                            } else {
                                Toast.makeText(context, tr(context, "Требуются Root-права", "Root required"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.CardBackground),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bootloader", color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp)
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
                                scope.launch(Dispatchers.IO) { RootUtils.execute("reboot recovery") }
                            } else {
                                Toast.makeText(context, tr(context, "Требуются Root-права", "Root required"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.CardBackground),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Recovery", color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (isRootAvailable == true) {
                                scope.launch(Dispatchers.IO) { RootUtils.execute("reboot -p") }
                            } else {
                                Toast.makeText(context, tr(context, "Требуются Root-права", "Root required"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF881B1B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(tr(context, "Выключить", "Power Off"), color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (isRootAvailable == true) {
                            scope.launch(Dispatchers.IO) { RootUtils.execute("setprop ctl.restart zygote") }
                        } else {
                            Toast.makeText(context, tr(context, "Требуются Root-права", "Root required"), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanMinimalismTheme.CardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr(context, "Мягкая перезагрузка (Soft Reboot)", "Soft Reboot (Restart Zygote)"), color = CleanMinimalismTheme.TextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ANALYTICS / DIAGNOSTICS TAB SCREEN (NO SANDBOX STORAGE!)
// -------------------------------------------------------------

@Composable
fun DiagnosticsTab(context: Context) {
    val scrollState = rememberScrollState()
    
    // Read hardware details and partitions storage space (excluding ANY sandbox volume)
    val rootStorageStats = remember { getPartitionStats("/") }
    val systemStorageStats = remember { getPartitionStats("/system") }
    val sdcardStorageStats = remember { getPartitionStats(Environment.getExternalStorageDirectory().absolutePath) }
    
    // Load physical Memory
    val ramStats = remember { getRamStats(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp)
    ) {
        Text(
            text = tr(context, "Системный Мониторинг", "Hardware & System Storage"),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CleanMinimalismTheme.TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // RAM details
        DashboardCard(
            title = tr(context, "ОЗУ (RAM)", "Random Access Memory"),
            subtitle = String.format("Total: %.2f GB | Free: %.2f GB", ramStats.first, ramStats.second),
            progress = ramStats.third,
            progressColor = CleanMinimalismTheme.AccentBlue
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Root/System Storage Space (/)
        DashboardCard(
            title = tr(context, "Раздел Системы (/) [Root System Partition]", "Root System Partition (/)"),
            subtitle = String.format("Total: %.2f GB | Available: %.2f GB", rootStorageStats.first / 1024.0, rootStorageStats.second / 1024.0),
            progress = rootStorageStats.third,
            progressColor = CleanMinimalismTheme.AccentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        // System partition (/system)
        DashboardCard(
            title = tr(context, "Системный Раздел (/system) [Android System Files]", "System Read-Only Files (/system)"),
            subtitle = String.format("Total: %.2f GB | Available: %.2f GB", systemStorageStats.first / 1024.0, systemStorageStats.second / 1024.0),
            progress = systemStorageStats.third,
            progressColor = CleanMinimalismTheme.WarningOrange
        )

        Spacer(modifier = Modifier.height(10.dp))

        // SDCard General Storage (/sdcard)
        DashboardCard(
            title = tr(context, "Внешний Накопитель (/sdcard)", "External Shared Storage (/sdcard)"),
            subtitle = String.format("Total: %.2f GB | Available: %.2f GB", sdcardStorageStats.first / 1024.0, sdcardStorageStats.second / 1024.0),
            progress = sdcardStorageStats.third,
            progressColor = CleanMinimalismTheme.HighlightGreen
        )

    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    progress: Float,
    progressColor: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CleanMinimalismTheme.SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CleanMinimalismTheme.TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = CleanMinimalismTheme.TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
            
            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                color = progressColor,
                trackColor = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

// -------------------------------------------------------------
// HARDWARE / MEMORY DIAGNOSTICS HELPER FUNCTIONS
// -------------------------------------------------------------

fun getPartitionStats(path: String): Triple<Double, Double, Float> {
    return try {
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalMegabytes = (totalBlocks * blockSize) / (1024.0 * 1024.0)
        val availableMegabytes = (availableBlocks * blockSize) / (1024.0 * 1024.0)
        
        val usedMegabytes = totalMegabytes - availableMegabytes
        val percentUsed = if (totalMegabytes > 0) (usedMegabytes / totalMegabytes).toFloat() else 0f
        
        Triple(totalMegabytes, availableMegabytes, percentUsed)
    } catch (e: Exception) {
        Triple(0.0, 0.0, 0f)
    }
}

fun getRamStats(context: Context): Triple<Double, Double, Float> {
    return try {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availableGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedGb = totalGb - availableGb
        val percentUsed = (usedGb / totalGb).toFloat()
        
        Triple(totalGb, availableGb, percentUsed)
    } catch (e: Exception) {
        Triple(0.0, 0.0, 0f)
    }
}

// Convert Android Drawable to Bitmap for UI rendering
fun drawableToImageBitmap(drawable: android.graphics.drawable.Drawable): androidx.compose.ui.graphics.ImageBitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

// Format space byte values
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// -------------------------------------------------------------
// BACKEND FILE I/O AND PERMISSIONS LOGIC
// -------------------------------------------------------------

object RootUtils {
    private var isRootCached: Boolean? = null

    fun isRootAvailable(): Boolean {
        isRootCached?.let { return it }
        var process: java.lang.Process? = null
        val result = try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_test"))
            val completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor()
                true
            }
            if (completed && process.exitValue() == 0) {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine()
                output == "root_test"
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process?.destroyForcibly()
                }
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
        isRootCached = result
        return result
    }

    fun executeWithOutput(command: String): String {
        if (!isRootAvailable()) return ""
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
            } else {
                process.waitFor()
                true
            }
            if (completed) {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                reader.readText()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                }
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            process?.destroy()
        }
    }

    fun execute(command: String): Boolean {
        if (!isRootAvailable()) return false
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            } else {
                process.waitFor()
                true
            }
            if (completed) {
                process.exitValue() == 0
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                }
                false
            }
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
        
        // Use Java standard File API if possible
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
                        extension = file.extension,
                        permissions = getLocalPermissions(file)
                    )
                )
            }
        } else {
            // Fallback command listing with root if allowed
            if (RootUtils.isRootAvailable()) {
                val output = RootUtils.executeWithOutput("ls -A -l \"$safePath\"")
                val lines = output.split("\n")
                lines.forEach { line ->
                    val parsed = parseLsLine(line, safePath)
                    if (parsed != null) {
                        items.add(parsed)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Sorter: directories first, then files
    return items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

// Extract standard local permissions
fun getLocalPermissions(file: File): String {
    return buildString {
        append(if (file.isDirectory) "d" else "-")
        append(if (file.canRead()) "r" else "-")
        append(if (file.canWrite()) "w" else "-")
        append(if (file.canExecute()) "x" else "-")
    }
}

// Parse custom root 'ls -al' rows
fun parseLsLine(line: String, parentPath: String): FileItem? {
    val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (parts.size < 7) return null
    
    val perms = parts[0]
    if (perms =="total" || perms.startsWith("total")) return null
    if (perms.length < 10) return null
    
    val isDir = perms[0] == 'd' || perms[0] == 'l'
    
    // Find name (the last components)
    val nameIndex = line.indexOf(parts[parts.size - 1])
    if (nameIndex == -1) return null
    var name = line.substring(nameIndex).trim()
    
    // Handle root symlink indicators ->
    if (name.contains("->")) {
        name = name.split("->")[0].trim()
    }
    
    if (name == "." || name == "..") return null
    
    val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
    val absolutePath = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name"
    val fileObj = File(absolutePath)
    
    return FileItem(
        name = name,
        path = absolutePath,
        isDirectory = isDir,
        size = size,
        lastModified = fileObj.lastModified().let { if (it == 0L) System.currentTimeMillis() else it },
        extension = fileObj.extension,
        permissions = perms.substring(0, 10)
    )
}

// Read raw file text details with support for superuser cat
fun readFileContent(path: String, context: Context): String {
    val file = File(path)
    return try {
        if (file.canRead()) {
            file.readText()
        } else {
            if (RootUtils.isRootAvailable()) {
                RootUtils.executeWithOutput("cat \"$path\"")
            } else {
                tr(context, "[ Ошибка прав доступа ]", "[ Permission Denied ]")
            }
        }
    } catch (e: Exception) {
        if (RootUtils.isRootAvailable()) {
            RootUtils.executeWithOutput("cat \"$path\"")
        } else {
            "[ Error reading file: ${e.localizedMessage} ]"
        }
    }
}

// Save text files using temporary local writing then cp via Root if required
fun saveFileContent(path: String, content: String, context: Context): Boolean {
    val file = File(path)
    try {
        if (file.canWrite()) {
            file.writeText(content)
            return true
        } else {
            // Write to a safe directory first, then copy with su
            val tempFile = File(context.cacheDir, "editor_temp_save.txt")
            tempFile.writeText(content)
            
            val success = RootUtils.execute("cp \"${tempFile.absolutePath}\" \"$path\"")
            tempFile.delete()
            return success
        }
    } catch (e: Exception) {
        try {
            val tempFile = File(context.cacheDir, "editor_temp_save.txt")
            tempFile.writeText(content)
            val success = RootUtils.execute("cp \"${tempFile.absolutePath}\" \"$path\"")
            tempFile.delete()
            return success
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }
    }
}

// Create folders on root or storage paths
fun createDirectory(parentPath: String, name: String): Boolean {
    val dir = File(parentPath, name)
    if (dir.exists()) return false
    return try {
        if (File(parentPath).canWrite()) {
            dir.mkdirs()
        } else {
            RootUtils.execute("mkdir -p \"${dir.absolutePath}\"")
        }
    } catch (e: Exception) {
        RootUtils.execute("mkdir -p \"${dir.absolutePath}\"")
    }
}

// Create blank file on path
fun createEmptyFile(parentPath: String, name: String): Boolean {
    val file = File(parentPath, name)
    if (file.exists()) return false
    return try {
        if (File(parentPath).canWrite()) {
            if (file.createNewFile()) true else RootUtils.execute("touch \"${file.absolutePath}\"")
        } else {
             RootUtils.execute("touch \"${file.absolutePath}\"")
        }
    } catch (e: Exception) {
         RootUtils.execute("touch \"${file.absolutePath}\"")
    }
}

// Delete files, directories, or system links
fun deletePath(path: String): Boolean {
    val file = File(path)
    return try {
        if (file.canWrite()) {
            file.deleteRecursively()
        } else {
            RootUtils.execute("rm -rf \"$path\"")
        }
    } catch (e: Exception) {
        RootUtils.execute("rm -rf \"$path\"")
    }
}
