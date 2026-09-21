package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio en primer plano (Foreground Service con tipo location)
 * que mantiene el GPS, el seguimiento de la ruta y las indicaciones por voz
 * activas y funcionando cuando la aplicación está en segundo plano o con la pantalla apagada.
 */
class NavigationForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            _isNavigatingInBackground.value = false
            _stopRequested.value = true
            stopForegroundService()
            return START_NOT_STICKY
        }
        if (action == ACTION_TOGGLE_VOICE) {
            _toggleVoiceRequested.value = true
            return START_STICKY
        }

        val instruction = intent?.getStringExtra(EXTRA_INSTRUCTION) ?: "Navegación GPS activa"
        val etaInfo = intent?.getStringExtra(EXTRA_ETA) ?: "Ruta en curso"
        val isVoiceActive = intent?.getBooleanExtra(EXTRA_VOICE_ENABLED, true) ?: true

        val notification = buildNotification(instruction, etaInfo, isVoiceActive)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}

        _isNavigatingInBackground.value = true
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OmniStudio:NavWakeLock")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 horas máx de seguridad
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun buildNotification(instruction: String, etaInfo: String, isVoiceActive: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NavigationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleVoiceIntent = Intent(this, NavigationForegroundService::class.java).apply {
            action = ACTION_TOGGLE_VOICE
        }
        val toggleVoicePendingIntent = PendingIntent.getService(
            this,
            1003,
            toggleVoiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val voiceStatusText = if (isVoiceActive) "🔊 Voz activa" else "🔇 Silencio"
        val voiceActionTitle = if (isVoiceActive) "🔇 Silenciar" else "🔊 Activar voz"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(instruction)
            .setContentText(etaInfo)
            .setSubText(voiceStatusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openPendingIntent)
            .addAction(
                if (isVoiceActive) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_btn_speak_now,
                voiceActionTitle,
                toggleVoicePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Finalizar",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Navegación GPS en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene el GPS y las indicaciones activas con la pantalla apagada o en segundo plano"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
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
        releaseWakeLock()
        _isNavigatingInBackground.value = false
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "omnistudio_navigation_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_OR_UPDATE = "com.example.action.NAV_START_OR_UPDATE"
        const val ACTION_STOP = "com.example.action.NAV_STOP"
        const val ACTION_TOGGLE_VOICE = "com.example.action.NAV_TOGGLE_VOICE"
        const val EXTRA_INSTRUCTION = "extra_instruction"
        const val EXTRA_ETA = "extra_eta"
        const val EXTRA_VOICE_ENABLED = "extra_voice_enabled"

        private val _isNavigatingInBackground = MutableStateFlow(false)
        val isNavigatingInBackground = _isNavigatingInBackground.asStateFlow()

        private val _stopRequested = MutableStateFlow(false)
        val stopRequested = _stopRequested.asStateFlow()

        private val _toggleVoiceRequested = MutableStateFlow(false)
        val toggleVoiceRequested = _toggleVoiceRequested.asStateFlow()

        fun clearStopRequest() {
            _stopRequested.value = false
        }

        fun clearToggleVoiceRequest() {
            _toggleVoiceRequested.value = false
        }

        fun startOrUpdate(context: Context, instruction: String, etaInfo: String, isVoiceActive: Boolean = true) {
            try {
                val intent = Intent(context, NavigationForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_INSTRUCTION, instruction)
                    putExtra(EXTRA_ETA, etaInfo)
                    putExtra(EXTRA_VOICE_ENABLED, isVoiceActive)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NavigationForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
