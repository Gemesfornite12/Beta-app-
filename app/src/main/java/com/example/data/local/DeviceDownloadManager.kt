package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object DeviceDownloadManager {

    fun saveText(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain"
    ): Uri? {
        return saveBytes(
            context = context,
            fileName = fileName,
            bytes = content.toByteArray(Charsets.UTF_8),
            mimeType = mimeType
        )
    }

    fun saveBytes(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null

            return try {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                }

                val completed = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }

                resolver.update(uri, completed, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)

            return try {
                FileOutputStream(file).use { output ->
                    output.write(bytes)
                }
                Uri.fromFile(file)
            } catch (_: Exception) {
                null
            }
        }
    }
}
