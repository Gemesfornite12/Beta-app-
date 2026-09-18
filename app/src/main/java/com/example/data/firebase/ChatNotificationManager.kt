package com.example.data.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatNotificationManager {

    private const val TAG = "ChatNotificationMgr"

    const val CHANNEL_ID_CHAT = "chat_messages_channel"
    const val CHANNEL_ID_GROUPS = "group_messages_channel"

    const val EXTRA_CHANNEL_ID = "extra_target_channel_id"
    const val EXTRA_ROUTE = "extra_target_route"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val ACTION_OPEN_CHAT = "com.example.action.OPEN_CHAT"

    private const val PREFS_NAME = "fcm_push_prefs"
    private const val KEY_FCM_TOKEN = "cached_fcm_token"

    // Estado del ciclo de vida para saber si mostrar notificación
    @Volatile
    var isAppInForeground: Boolean = true

    @Volatile
    var activeChatChannelId: String? = null

    private val _fcmTokenState = MutableStateFlow<String?>(null)
    val fcmTokenState: StateFlow<String?> = _fcmTokenState.asStateFlow()

    private val _lastNotificationReceived = MutableStateFlow<String?>(null)
    val lastNotificationReceived: StateFlow<String?> = _lastNotificationReceived.asStateFlow()

    /**
     * Inicializa y registra los canales de notificación en el sistema Android (API 26+).
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Canal para mensajes privados y directos
            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHAT,
                "Mensajes Privados de Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones instantáneas de mensajes directos y privados"
                enableLights(true)
                lightColor = 0xFF6366F1.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Canal para mensajes de grupos de colaboración
            val groupsChannel = NotificationChannel(
                CHANNEL_ID_GROUPS,
                "Mensajes de Grupos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones instantáneas de grupos y proyectos colaborativos"
                enableLights(true)
                lightColor = 0xFF7C3AED.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(chatChannel)
            notificationManager.createNotificationChannel(groupsChannel)
            Log.d(TAG, "Notification channels initialized successfully")
        }

        // Cargar token en memoria si existe en prefs
        val cached = getFcmToken(context)
        if (!cached.isNullOrBlank()) {
            _fcmTokenState.value = cached
        }
    }

    /**
     * Determina si debe dispararse una notificación de sistema al recibir un mensaje:
     * Dispara si la app está en segundo plano o si el usuario no está en ese chat actualmente.
     */
    fun shouldNotify(channelId: String): Boolean {
        if (!isAppInForeground) return true
        return activeChatChannelId != channelId
    }

    /**
     * Muestra una notificación push enriquecida en la barra de estado y pantalla de bloqueo.
     */
    fun showChatNotification(
        context: Context,
        channelId: String,
        channelName: String,
        senderName: String,
        senderEmail: String,
        messageText: String,
        isGroup: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ) {
        // Verificar permisos en Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Skipping notification display.")
                return
            }
        }

        createNotificationChannels(context)

        val targetChannelId = if (isGroup) CHANNEL_ID_GROUPS else CHANNEL_ID_CHAT

        // Intent para abrir la aplicación y dirigirse al chat específico
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_ROUTE, "chat")
            putExtra(EXTRA_SENDER_NAME, senderName)
        }

        val requestCode = (channelId.hashCode() and 0xFFFF)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isGroup) {
            "$senderName en #$channelName"
        } else {
            senderName
        }

        val summary = if (isGroup) "Grupo • $channelName" else "Chat Privado"

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(title)
            .setContentText(messageText)
            .setSubText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(messageText)
                    .setBigContentTitle(title)
                    .setSummaryText(summary)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setWhen(timestamp)
            .setShowWhen(true)
            .setSound(defaultSound)
            .setVibrate(if (isGroup) longArrayOf(0, 300, 200, 300) else longArrayOf(0, 250, 150, 250))
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_stat_chat,
                "Abrir Chat",
                pendingIntent
            )

        val notificationId = (channelId.hashCode() and 0x7FFFFFFF)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            _lastNotificationReceived.value = "$senderName: $messageText"
            Log.d(TAG, "Notification posted for channel $channelId, sender: $senderName, isGroup: $isGroup")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while posting notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification: ${e.message}", e)
        }
    }

    /**
     * Limpia la notificación de un canal específico cuando el usuario ingresa a él.
     */
    fun cancelChannelNotification(context: Context, channelId: String) {
        val notificationId = (channelId.hashCode() and 0x7FFFFFFF)
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling notification: ${e.message}")
        }
    }

    /**
     * Guarda el token FCM obtenido en preferencias compartidas y memoria.
     */
    fun saveFcmToken(context: Context, token: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
            _fcmTokenState.value = token
            Log.d(TAG, "FCM Token saved successfully: ${token.take(15)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token: ${e.message}")
        }
    }

    /**
     * Obtiene el token FCM guardado localmente.
     */
    fun getFcmToken(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_FCM_TOKEN, null)
        } catch (e: Exception) {
            null
        }
    }
}
