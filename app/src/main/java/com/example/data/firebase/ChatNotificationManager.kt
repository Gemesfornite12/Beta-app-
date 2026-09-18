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
    const val CHANNEL_ID_CALLS = "calls_channel"
    const val CHANNEL_ID_MISSED_CALLS = "missed_calls_channel"

    const val EXTRA_CHANNEL_ID = "extra_target_channel_id"
    const val EXTRA_ROUTE = "extra_target_route"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALL_ACTION = "extra_call_action"
    const val ACTION_OPEN_CHAT = "com.example.action.OPEN_CHAT"
    const val ACTION_ANSWER_CALL = "com.example.action.ANSWER_CALL"
    const val ACTION_REJECT_CALL = "com.example.action.REJECT_CALL"

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

            // Canal para llamadas de voz y videollamadas entrantes
            val defaultRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val callAudioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val callsChannel = NotificationChannel(
                CHANNEL_ID_CALLS,
                "Llamadas y Videollamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones y alertas de llamadas y videollamadas entrantes"
                enableLights(true)
                lightColor = 0xFF10B981.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
                setSound(defaultRingtone, callAudioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Canal para llamadas perdidas
            val missedCallsChannel = NotificationChannel(
                CHANNEL_ID_MISSED_CALLS,
                "Llamadas Perdidas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de llamadas no contestadas o expiradas"
                enableLights(true)
                lightColor = 0xFFEF4444.toInt()
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(chatChannel)
            notificationManager.createNotificationChannel(groupsChannel)
            notificationManager.createNotificationChannel(callsChannel)
            notificationManager.createNotificationChannel(missedCallsChannel)
            Log.d(TAG, "Notification channels initialized successfully")
        }

        // Cargar token en memoria si existe en prefs
        val cached = getFcmToken(context)
        if (!cached.isNullOrBlank()) {
            _fcmTokenState.value = cached
        }
    }

    fun isMessageNotificationEnabled(context: Context, channelId: String): Boolean {
        val prefs = context.getSharedPreferences("channel_notification_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("notify_msg_$channelId", true)
    }

    fun isVoiceCallNotificationEnabled(context: Context, channelId: String): Boolean {
        val prefs = context.getSharedPreferences("channel_notification_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("notify_voice_$channelId", true)
    }

    fun isVideoCallNotificationEnabled(context: Context, channelId: String): Boolean {
        val prefs = context.getSharedPreferences("channel_notification_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("notify_video_$channelId", true)
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
        // Verificar preferencia del canal para mensajes
        if (!isMessageNotificationEnabled(context, channelId)) {
            Log.d(TAG, "Notificaciones de mensaje desactivadas para canal: $channelId")
            return
        }

        // Verificar si Horario No Molestar (DND) está activo
        if (CallSoundVibrationManager.isDndActive(context)) {
            Log.d(TAG, "Horario No Molestar (DND) activo: se silencia la notificación de mensaje")
            return
        }
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
     * Muestra una notificación de llamada entrante (voz o video) con acciones Responder (verde) y Rechazar (rojo).
     */
    fun showIncomingCallNotification(
        context: Context,
        callId: String,
        channelId: String,
        callerName: String,
        groupName: String?,
        isVideo: Boolean,
        timeoutMinutes: Int = 5
    ) {
        val allowed = if (isVideo) isVideoCallNotificationEnabled(context, channelId) else isVoiceCallNotificationEnabled(context, channelId)
        if (!allowed) {
            Log.d(TAG, "Notificación de ${if (isVideo) "videollamada" else "llamada de voz"} desactivada para el canal $channelId")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) return
        }

        createNotificationChannels(context)

        // Intent de Responder
        val answerIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ANSWER_CALL
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CALL_ACTION, "ANSWER")
            putExtra(EXTRA_ROUTE, "chat")
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            (callId.hashCode() and 0x3FFF) + 1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent de Rechazar / No responder
        val rejectIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REJECT_CALL
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CALL_ACTION, "REJECT")
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            context,
            (callId.hashCode() and 0x3FFF) + 2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isVideo) "📹 Videollamada entrante" else "📞 Llamada de voz entrante"
        val subtitle = if (!groupName.isNullOrBlank()) {
            "$callerName en $groupName"
        } else {
            callerName
        }
        val contentText = "$subtitle te está llamando • Tiempo para responder: $timeoutMinutes min"

        val defaultRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(if (!groupName.isNullOrBlank()) groupName else "Llamada entrante")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(contentText)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(defaultRingtone)
            .setVibrate(longArrayOf(0, 800, 500, 800, 500, 800))
            .setContentIntent(answerPendingIntent)
            .addAction(
                R.drawable.ic_stat_chat,
                "✓ Responder",
                answerPendingIntent
            )
            .addAction(
                R.drawable.ic_stat_chat,
                "✕ Rechazar",
                rejectPendingIntent
            )

        val notificationId = (callId.hashCode() and 0x7FFFFFFF)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "Incoming call notification displayed for call $callId from $callerName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display incoming call notification: ${e.message}")
        }

        // Reproducir timbre en loop y vibración continua mientras timbra
        try {
            val appPrefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
            val soundEnabled = appPrefs.getBoolean("call_sound_enabled", true)
            val vibEnabled = appPrefs.getBoolean("call_vibration_enabled", true)
            val ringtoneMode = appPrefs.getInt("call_ringtone_mode", 0)
            CallSoundVibrationManager.startIncomingCallAlert(
                context = context,
                soundEnabled = soundEnabled,
                vibrationEnabled = vibEnabled,
                ringtoneMode = ringtoneMode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call sound & vibration: ${e.message}")
        }
    }

    /**
     * Muestra notificación de llamada perdida cuando se agota el tiempo de espera o no se contesta.
     */
    fun showMissedCallNotification(
        context: Context,
        callerName: String,
        groupName: String?,
        isVideo: Boolean,
        timeoutMinutes: Int
    ) {
        CallSoundVibrationManager.playCallMissed(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) return
        }

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, "chat")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt() and 0xFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerDisplay = if (!groupName.isNullOrBlank()) "$callerName en $groupName" else callerName
        val title = if (isVideo) "📹 Videollamada perdida" else "📵 Llamada de voz perdida"
        val message = "Llamada de $callerDisplay sin respuesta tras $timeoutMinutes min."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_MISSED_CALLS)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setSubText("Llamada no contestada")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationId = (System.currentTimeMillis().toInt() and 0x7FFFFFFF)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "Missed call notification displayed for $callerName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display missed call notification: ${e.message}")
        }
    }

    /**
     * Cancela la notificación de llamada activa y detiene sonidos y vibraciones.
     */
    fun cancelCallNotification(context: Context, callId: String) {
        CallSoundVibrationManager.stopAll(context)
        val notificationId = (callId.hashCode() and 0x7FFFFFFF)
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling call notification: ${e.message}")
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
