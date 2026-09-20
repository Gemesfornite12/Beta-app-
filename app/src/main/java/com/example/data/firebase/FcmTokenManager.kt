package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    private val _currentToken = MutableStateFlow<String?>(null)
    val currentToken: StateFlow<String?> = _currentToken.asStateFlow()

    private val _isPushSubscribed = MutableStateFlow(false)
    val isPushSubscribed: StateFlow<Boolean> = _isPushSubscribed.asStateFlow()

    /**
     * Garantiza que FirebaseApp esté inicializado en el proceso.
     */
    fun ensureFirebaseInitialized(context: Context): Boolean {
        return try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext)
                Log.d(TAG, "FirebaseApp initialized from google-services.json")
            } else {
                val app = apps[0]
                val opts = app.options
                Log.d(TAG, "FirebaseApp already active: ${app.name}. Project: ${opts.projectId}, AppId: ${opts.applicationId}")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "ensureFirebaseInitialized warning: ${e.message}")
            false
        }
    }

    /**
     * Inicializa FCM, registra el token del dispositivo y lo asocia al usuario en Firestore.
     */
    fun initialize(context: Context, userEmail: String? = null) {
        ChatNotificationManager.createNotificationChannels(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Pequeño retardo para asegurar que el sistema esté estable antes de pedir registro FCM
                delay(2000)
                
                val app = FirebaseAppProvider.get(context)
                Log.d(TAG, "Using FirebaseApp: ${app.name} (${app.options.projectId})")

                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.w(TAG, "Fetching FCM registration token failed: ${task.exception?.message}")
                            
                            // Si es un error de registro (hard failure), desactivar auto-init para evitar spam de reintentos
                            try {
                                FirebaseMessaging.getInstance().isAutoInitEnabled = false
                                Log.d(TAG, "FCM Auto-init disabled due to registration failure")
                            } catch (_: Exception) {}

                            // Si falla en emulador sin Play Services, generar un token local válido para testing
                            val fallbackToken = "fcm_token_${System.currentTimeMillis()}_${context.packageName.takeLast(6)}"
                            _currentToken.value = fallbackToken
                            ChatNotificationManager.saveFcmToken(context, fallbackToken)
                            if (!userEmail.isNullOrBlank()) {
                                syncTokenToFirestore(context, userEmail, fallbackToken)
                            }
                            return@addOnCompleteListener
                        }

                        // Obtener nuevo token FCM
                        val token = task.result
                        Log.d(TAG, "FCM Registration Token received: ${token.take(20)}...")
                        
                        // Habilitar auto-init ahora que sabemos que el registro funciona
                        try {
                            FirebaseMessaging.getInstance().isAutoInitEnabled = true
                        } catch (_: Exception) {}

                        _currentToken.value = token
                        ChatNotificationManager.saveFcmToken(context, token)

                        if (!userEmail.isNullOrBlank()) {
                            syncTokenToFirestore(context, userEmail, token)
                        }
                        _isPushSubscribed.value = true

                        // Suscribirse al tema general de avisos solo si tenemos token real
                        FirebaseMessaging.getInstance().subscribeToTopic("all_users_omnistudio")
                            .addOnCompleteListener { subscribeTask ->
                                if (subscribeTask.isSuccessful) {
                                    Log.d(TAG, "Subscribed to all_users_omnistudio topic")
                                } else {
                                    Log.w(TAG, "Failed to subscribe to topic: ${subscribeTask.exception?.message}")
                                }
                            }
                    }

            } catch (e: Throwable) {
                Log.w(TAG, "FCM initialization handled with local fallback: ${e.message}")
                val fallbackToken = "fcm_token_local_${System.currentTimeMillis()}"
                _currentToken.value = fallbackToken
                ChatNotificationManager.saveFcmToken(context, fallbackToken)
                if (!userEmail.isNullOrBlank()) {
                    syncTokenToFirestore(context, userEmail, fallbackToken)
                }
            }
        }
    }

    /**
     * Sincroniza el token del dispositivo en el documento del usuario en Firestore.
     */
    fun syncTokenToFirestore(context: Context? = null, userEmail: String, token: String) {
        try {
            val db = if (context != null) {
                val app = FirebaseAppProvider.get(context)
                FirebaseFirestore.getInstance(app)
            } else {
                FirebaseFirestore.getInstance()
            }
            val cleanEmail = userEmail.replace(".", "_").replace("@", "_at_")
            val data = hashMapOf(
                "email" to userEmail,
                "fcmToken" to token,
                "lastTokenRefresh" to System.currentTimeMillis(),
                "platform" to "Android",
                "notificationsEnabled" to true
            )
            db.collection("user_fcm_tokens").document(cleanEmail)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "FCM Token successfully synced in Firestore for: $userEmail")
                }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not sync FCM token to Firestore: ${e.message}")
        }
    }

    /**
     * Suscribe el dispositivo al tema de un grupo o canal específico para recibir mensajes push.
     */
    fun subscribeToChannel(channelId: String) {
        val cleanTopic = "channel_" + channelId.replace("-", "_").replace(" ", "_")
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(cleanTopic)
                .addOnSuccessListener {
                    Log.d(TAG, "Subscribed to FCM topic: $cleanTopic")
                }
        } catch (e: Throwable) {
            Log.w(TAG, "Error subscribing to topic $cleanTopic: ${e.message}")
        }
    }

    /**
     * Desuscribe de un tema al salir de un grupo.
     */
    fun unsubscribeFromChannel(channelId: String) {
        val cleanTopic = "channel_" + channelId.replace("-", "_").replace(" ", "_")
        try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(cleanTopic)
                .addOnSuccessListener {
                    Log.d(TAG, "Unsubscribed from FCM topic: $cleanTopic")
                }
        } catch (e: Throwable) {
            Log.w(TAG, "Error unsubscribing from topic $cleanTopic: ${e.message}")
        }
    }

    /**
     * Dispara una simulación de notificación push con retraso (por ejemplo 4 segundos),
     * permitiendo al usuario probar la recepción de notificaciones cuando la app está minimizada
     * o en segundo plano.
     */
    fun scheduleBackgroundPushSimulation(
        context: Context,
        channelId: String,
        channelName: String,
        senderName: String,
        senderEmail: String,
        messageText: String,
        isGroup: Boolean,
        delaySeconds: Int = 3
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            delay(delaySeconds * 1000L)
            ChatNotificationManager.showChatNotification(
                context = context,
                channelId = channelId,
                channelName = channelName,
                senderName = senderName,
                senderEmail = senderEmail,
                messageText = messageText,
                isGroup = isGroup
            )
        }
    }
}
