package com.supakeyboard.neuralkey.ime.clipboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/** A screenshot found in the device's media library. */
data class ScreenshotRef(val mediaId: Long, val uri: Uri, val dateAddedMs: Long)

/**
 * Finds the newest screenshot so it can be offered for pasting without the user having to tap "Copy".
 * Strictly opt-in: nothing is queried unless the user enabled it and granted media access.
 */
object ScreenshotFinder {

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    /** Newest screenshot added within the last [maxAgeSeconds], or null. */
    fun findRecent(context: Context, maxAgeSeconds: Long): ScreenshotRef? {
        if (!hasPermission(context)) return null
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val cutoff = System.currentTimeMillis() / 1000 - maxAgeSeconds
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND (" +
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
        val args = arrayOf(cutoff.toString(), "%Screenshot%", "%Screenshot%")
        return try {
            context.contentResolver.query(
                collection, projection, selection, args, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                ScreenshotRef(id, ContentUris.withAppendedId(collection, id), cursor.getLong(1) * 1000)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
