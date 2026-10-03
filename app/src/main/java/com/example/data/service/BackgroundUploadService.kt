package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.firebase.FirebaseAppProvider
import com.example.data.firebase.RealtimeDatabaseService
import com.example.data.model.ChatMessage
import com.example.data.supabase.SupabaseMediaStorageService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service that handles media uploads in the background (Supabase + Firebase)
 * so that uploads continue seamlessly even if the app is minimized, closed, or screen turned off.
 */
class BackgroundUploadService : Service() {

    companion object {
        private const val TAG = "BackgroundUploadService"
        private const val CHANNEL_ID = "media_upload_background_channel"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START_UPLOAD = "com.example.data.service.START_UPLOAD"
        const val ACTION_CANCEL_UPLOAD = "com.example.data.service.CANCEL_UPLOAD"

        const val EXTRA_MEDIA_URI = "extra_media_uri"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_CAPTION = "extra_caption"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_OWNER_UID = "extra_owner_uid"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_SENDER_EMAIL = "extra_sender_email"

        data class UploadState(
            val isUploading: Boolean = false,
            val mediaType: String = "",
            val progress: Int = 0,
            val message: String = "",
            val isSuccess: Boolean = false,
            val isError: Boolean = false,
            val channelId: String = ""
        )

        private val _uploadStateFlow = MutableStateFlow(UploadState())
        val uploadStateFlow = _uploadStateFlow.asStateFlow()

        fun startUpload(
            context: Context,
            mediaUri: String,
            mediaType: String,
            caption: String,
            channelId: String,
            ownerUid: String,
            senderName: String,
            senderEmail: String
        ) {
            val intent = Intent(context, BackgroundUploadService::class.java).apply {
                action = ACTION_START_UPLOAD
                putExtra(EXTRA_MEDIA_URI, mediaUri)
                putExtra(EXTRA_MEDIA_TYPE, mediaType)
                putExtra(EXTRA_CAPTION, caption)
                putExtra(EXTRA_CHANNEL_ID, channelId)
                putExtra(EXTRA_OWNER_UID, ownerUid)
                putExtra(EXTRA_SENDER_NAME, senderName)
                putExtra(EXTRA_SENDER_EMAIL, senderEmail)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_UPLOAD -> {
                _uploadStateFlow.value = UploadState(
                    isUploading = false,
                    isError = true,
                    message = "Subida cancelada por el usuario"
                )
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START_UPLOAD -> {
                val mediaUriStr = intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty()
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "video"
                val caption = intent.getStringExtra(EXTRA_CAPTION).orEmpty()
                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
                val ownerUid = intent.getStringExtra(EXTRA_OWNER_UID).orEmpty()
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: "Usuario"
                val senderEmail = intent.getStringExtra(EXTRA_SENDER_EMAIL) ?: ""

                if (mediaUriStr.isBlank() || channelId.isBlank() || ownerUid.isBlank()) {
                    Log.e(TAG, "Parámetros de subida inválidos")
                    stopForegroundService()
                    return START_NOT_STICKY
                }

                val initialNotification = buildNotification(
                    title = "Subiendo ${getMediaTypeName(mediaType)}...",
                    content = "Iniciando transferencia en segundo plano...",
                    progress = 0,
                    indeterminate = true
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            initialNotification,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                            } else {
                                0
                            }
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, initialNotification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo iniciar servicio en primer plano: ${e.message}")
                }

                executeBackgroundUpload(
                    mediaUriStr = mediaUriStr,
                    mediaType = mediaType,
                    caption = caption,
                    channelId = channelId,
                    ownerUid = ownerUid,
                    senderName = senderName,
                    senderEmail = senderEmail
                )
                return START_REDELIVER_INTENT
            }
            else -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
        }
    }

    private fun executeBackgroundUpload(
        mediaUriStr: String,
        mediaType: String,
        caption: String,
        channelId: String,
        ownerUid: String,
        senderName: String,
        senderEmail: String
    ) {
        serviceScope.launch {
            _uploadStateFlow.value = UploadState(
                isUploading = true,
                mediaType = mediaType,
                progress = 0,
                message = "Preparando archivo para subida en segundo plano...",
                channelId = channelId
            )

            try {
                val context = applicationContext
                val mediaStorageService = SupabaseMediaStorageService(context)
                val uri = Uri.parse(mediaUriStr)

                val mimeType = context.contentResolver.getType(uri)
                    ?: when (mediaType) {
                        "video" -> "video/mp4"
                        "image" -> "image/jpeg"
                        "audio" -> "audio/mpeg"
                        "gif" -> "image/gif"
                        "sticker" -> "image/webp"
                        else -> "application/octet-stream"
                    }

                // Detect real media type if requested type is generic
                val detectedType = detectMediaType(context, uri, mediaType, mimeType)

                updateNotification(
                    title = "Subiendo ${getMediaTypeName(detectedType)} en segundo plano...",
                    content = "Subiendo archivo... 0%",
                    progress = 0
                )

                val uploaded = mediaStorageService.uploadMedia(
                    ownerUid = ownerUid,
                    localUri = uri,
                    mediaType = detectedType,
                    mimeType = mimeType,
                    onProgress = { transferred, total ->
                        val percent = if (total > 0L) {
                            ((transferred * 90L) / total).toInt().coerceIn(0, 90)
                        } else 0

                        val msg = if (percent >= 90) {
                            "Verificando respuesta del servidor Supabase... 90%"
                        } else {
                            "Subiendo archivo... $percent%"
                        }

                        _uploadStateFlow.value = UploadState(
                            isUploading = true,
                            mediaType = detectedType,
                            progress = percent,
                            message = msg,
                            channelId = channelId
                        )

                        updateNotification(
                            title = "Subiendo ${getMediaTypeName(detectedType)} en segundo plano...",
                            content = msg,
                            progress = percent
                        )
                    }
                )

                // Subida completada en Supabase Storage -> Crear mensaje en Realtime Database
                _uploadStateFlow.value = UploadState(
                    isUploading = true,
                    mediaType = detectedType,
                    progress = 95,
                    message = "Guardando mensaje en el chat...",
                    channelId = channelId
                )

                updateNotification(
                    title = "Guardando mensaje...",
                    content = "Guardando archivo en #${channelId}...",
                    progress = 95
                )

                val now = System.currentTimeMillis()
                val fallbackCaption = when (detectedType) {
                    "video" -> if (caption.isNotBlank()) caption else "🎥 Video adjunto"
                    "image" -> if (caption.isNotBlank()) caption else "📷 Foto adjunta"
                    "gif" -> if (caption.isNotBlank()) caption else "🎭 GIF animado"
                    "sticker" -> if (caption.isNotBlank()) caption else "✨ Sticker"
                    "audio" -> if (caption.isNotBlank()) caption else "🎵 Audio adjunto"
                    else -> if (caption.isNotBlank()) caption else "📄 Archivo adjunto"
                }

                val chatMsg = ChatMessage(
                    channelId = channelId,
                    senderName = senderName,
                    senderEmail = senderEmail,
                    text = fallbackCaption,
                    timestamp = now,
                    mediaUrl = uploaded.downloadUrl,
                    mediaType = detectedType,
                    mediaThumbnail = if (detectedType == "image") uploaded.downloadUrl else null,
                    isSyncedFirestore = false,
                    deliveryStatus = "enviando",
                    sentTimestamp = now
                )

                val rtdbService = RealtimeDatabaseService()
                rtdbService.sendMessage(chatMsg)

                _uploadStateFlow.value = UploadState(
                    isUploading = false,
                    isSuccess = true,
                    mediaType = detectedType,
                    progress = 100,
                    message = "${getMediaTypeName(detectedType)} enviado al chat en segundo plano",
                    channelId = channelId
                )

                val notifyManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val completedNotification = buildCompletedNotification(
                    title = "✓ ${getMediaTypeName(detectedType)} enviado",
                    content = "Tu archivo se subió con éxito al chat #$channelId"
                )
                notifyManager.notify(NOTIFICATION_ID + 1, completedNotification)

            } catch (e: Exception) {
                Log.e(TAG, "Error en la subida en segundo plano", e)
                _uploadStateFlow.value = UploadState(
                    isUploading = false,
                    isError = true,
                    message = "Error en la subida: ${e.message ?: "Error de red"}",
                    channelId = channelId
                )

                val notifyManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val errorNotification = buildErrorNotification(
                    title = "❌ Error en la subida",
                    content = e.message ?: "No se pudo subir el archivo en segundo plano"
                )
                notifyManager.notify(NOTIFICATION_ID + 2, errorNotification)
            } finally {
                stopForegroundService()
            }
        }
    }

    private fun detectMediaType(
        context: Context,
        uri: Uri,
        requestedType: String,
        mimeType: String
    ): String {
        val fileName = getFileNameFromUri(context, uri).lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()

        return when {
            ext in listOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "flv", "wmv", "m4v") ||
                    mimeType.startsWith("video/") -> "video"

            ext == "gif" || mimeType == "image/gif" -> "gif"

            ext in listOf("jpg", "jpeg", "png", "webp", "bmp", "heic") ||
                    mimeType.startsWith("image/") -> "image"

            ext in listOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus") ||
                    mimeType.startsWith("audio/") -> "audio"

            requestedType.isNotBlank() && requestedType != "document" -> requestedType

            else -> "document"
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) name = cursor.getString(index)
                    }
                }
            } catch (_: Exception) {}
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name ?: "archivo"
    }

    private fun getMediaTypeName(type: String): String = when (type) {
        "video" -> "Video"
        "image" -> "Imagen"
        "audio" -> "Audio"
        "gif" -> "GIF"
        "sticker" -> "Sticker"
        else -> "Archivo"
    }

    private fun updateNotification(title: String, content: String, progress: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(title, content, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean = false
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildCompletedNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun buildErrorNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subidas multimedia en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso de las subidas de videos y archivos en segundo plano"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OmniStudio:BackgroundUploadWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun stopForegroundService() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
