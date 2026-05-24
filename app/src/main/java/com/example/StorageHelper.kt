package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

object StorageHelper {

    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun needsManageAllFiles(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    fun requiredRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasRuntimePermissions(context: Context): Boolean {
        val perms = requiredRuntimePermissions()
        if (perms.isEmpty()) return true
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun canBrowsePath(path: String, context: Context): Boolean {
        if (path == "/" || path.startsWith("/system") || path.startsWith("/data")) {
            return RootUtils.isRootAvailable() || hasStorageAccess(context)
        }
        val file = java.io.File(path)
        return file.canRead() || hasStorageAccess(context) || RootUtils.isRootAvailable()
    }

    fun resolveStartPath(context: Context, location: StartLocation): String {
        return when (location) {
            StartLocation.ROOT -> "/"
            StartLocation.DOWNLOADS -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath
            }
            StartLocation.STORAGE -> Environment.getExternalStorageDirectory().absolutePath
        }
    }

    fun createManageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun getStorageStats(): Pair<Long, Long> {
        return try {
            val path = Environment.getExternalStorageDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val free = stat.availableBlocksLong * blockSize
            Pair(total, free)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }
}
