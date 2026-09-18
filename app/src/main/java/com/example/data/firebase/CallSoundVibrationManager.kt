package com.example.data.firebase

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Gestor integral de Audio, Sonido de Timbre (Ringtone), Tonos de Marcación y Vibración Continua
 * para llamadas de voz y videollamadas entrantes, salientes y estados de conexión.
 */
object CallSoundVibrationManager {

    private const val TAG = "CallSoundVibMgr"

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var dialToneJob: Job? = null

    private var isPlayingIncoming = false
    private var isPlayingOutgoing = false

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Vibrator: ${e.message}")
            null
        }
    }

    /**
     * Inicia el sonido de timbre y la vibración en bucle para una llamada entrante.
     */
    fun startIncomingCallAlert(context: Context, soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        stopAll(context)
        isPlayingIncoming = true

        // 1. Vibración continua en bucle (1s vibrar, 1s pausa)
        if (vibrationEnabled) {
            startRepeatingVibration(context)
        }

        // 2. Reproducción de Sonido de Timbre en bucle
        if (soundEnabled) {
            try {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                if (ringtoneUri != null) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(context, ringtoneUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        start()
                    }
                    Log.d(TAG, "Incoming call ringtone started via MediaPlayer")
                } else {
                    startFallbackRingtone(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer failed, falling back to RingtoneManager: ${e.message}")
                startFallbackRingtone(context)
            }
        }
    }

    private fun startFallbackRingtone(context: Context) {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (ringtoneUri != null) {
                ringtone = RingtoneManager.getRingtone(context, ringtoneUri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
                Log.d(TAG, "Incoming call ringtone started via RingtoneManager fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone fallback failed: ${e.message}")
        }
    }

    /**
     * Inicia tono de llamada saliente (espera a que el otro responda)
     */
    fun startOutgoingDialTone(context: Context, soundEnabled: Boolean = true) {
        stopAll(context)
        if (!soundEnabled) return

        isPlayingOutgoing = true
        dialToneJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                toneGenerator = toneGen
                while (isActive && isPlayingOutgoing) {
                    toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500)
                    delay(3500)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Outgoing dial tone exception: ${e.message}")
            }
        }
    }

    /**
     * Efecto sonoro y háptico al contestar / conectar la llamada
     */
    fun playCallConnected(context: Context) {
        stopAll(context)
        // Vibración corta de confirmación
        vibrateOnce(context, 70)
        try {
            val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        } catch (e: Exception) {
            Log.w(TAG, "Connected tone error: ${e.message}")
        }
    }

    /**
     * Efecto sonoro y háptico al colgar, rechazar o finalizar llamada
     */
    fun playCallEnded(context: Context) {
        stopAll(context)
        // Vibración doble de fin de llamada
        vibratePattern(context, longArrayOf(0, 100, 80, 100))
        try {
            val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            tg.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
        } catch (e: Exception) {
            Log.w(TAG, "Ended tone error: ${e.message}")
        }
    }

    /**
     * Alerta de llamada perdida / expirada
     */
    fun playCallMissed(context: Context) {
        stopAll(context)
        vibratePattern(context, longArrayOf(0, 200, 100, 200, 100, 300))
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 400)
        } catch (e: Exception) {
            Log.w(TAG, "Missed tone error: ${e.message}")
        }
    }

    /**
     * Inicia una vibración continua repetitiva
     */
    fun startRepeatingVibration(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 1) // 1 = repetir desde índice 1
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 1)
            }
            Log.d(TAG, "Continuous call vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting continuous vibration: ${e.message}")
        }
    }

    /**
     * Vibración única de duración específica en milisegundos
     */
    fun vibrateOnce(context: Context, durationMs: Long = 100) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating once: ${e.message}")
        }
    }

    /**
     * Patrón de vibración personalizado
     */
    fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating pattern: ${e.message}")
        }
    }

    /**
     * Detiene de inmediato todos los sonidos, tonos y vibraciones activas.
     */
    fun stopAll(context: Context) {
        isPlayingIncoming = false
        isPlayingOutgoing = false

        // Cancelar Job de tono de marcación
        dialToneJob?.cancel()
        dialToneJob = null

        // Detener MediaPlayer
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing media player: ${e.message}")
        } finally {
            mediaPlayer = null
        }

        // Detener Ringtone
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone: ${e.message}")
        } finally {
            ringtone = null
        }

        // Detener ToneGenerator
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing tone generator: ${e.message}")
        } finally {
            toneGenerator = null
        }

        // Detener Vibración
        try {
            val vibrator = getVibrator(context)
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling vibrator: ${e.message}")
        }

        Log.d(TAG, "All sounds, ringtones and vibrations stopped")
    }
}
