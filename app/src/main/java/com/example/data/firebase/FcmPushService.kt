package com.example.data.firebase

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmPushService : FirebaseMessagingService() {

    private val TAG = "FcmPushService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM onNewToken received: ${token.take(20)}...")
        ChatNotificationManager.saveFcmToken(applicationContext, token)

        // Asociar el token al usuario autenticado, no a una cuenta fija.
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (!userEmail.isNullOrBlank()) {
            FcmTokenManager.syncTokenToFirestore(applicationContext, userEmail, token)
        }
        try {
            val tokenData = hashMapOf(
                "fcmToken" to token,
                "email" to userEmail.orEmpty(),
                "updatedAt" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance().collection("fcm_device_tokens")
                .document(token.hashCode().toString())
                .set(tokenData, SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "Error saving FCM device token: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // Las llamadas se enrutan solo en la app aislada de prueba.
        val data = remoteMessage.data
        val currentPushEnvironment = if (BuildConfig.APPLICATION_ID.endsWith(".test")) "test" else "production"
        val payloadEnvironment = data["pushEnvironment"].orEmpty()
        if (payloadEnvironment.isNotBlank() && payloadEnvironment != currentPushEnvironment) {
            Log.w(TAG, "Ignoring push for a different app environment.")
            return
        }
        if (data["eventType"] == "call") {
            if (payloadEnvironment != currentPushEnvironment) {
                Log.w(TAG, "Ignoring call push without a matching app environment.")
                return
            }
            val callId = data["callId"].orEmpty()
            val channelId = data["channelId"].orEmpty()
            if (callId.isBlank() || channelId.isBlank()) {
                Log.w(TAG, "Ignoring call push without call and channel identifiers.")
                return
            }
            ChatNotificationManager.showIncomingCallNotification(
                context = applicationContext,
                callId = callId,
                channelId = channelId,
                callerName = data["callerName"] ?: "Compañero",
                groupName = data["groupName"]?.takeIf { it.isNotBlank() },
                isVideo = data["isVideo"]?.toBoolean() ?: false,
                timeoutMinutes = data["timeoutMinutes"]?.toIntOrNull() ?: 5
            )
            return
        }

        if (data["eventType"] == "social") {
            if (!BuildConfig.APPLICATION_ID.endsWith(".test") || data["pushEnvironment"] != "test") {
                Log.w(TAG, "Ignoring Social push outside the isolated test environment.")
                return
            }
            FirebaseAnalyticsManager.logSocialNotificationReceived(
                applicationContext,
                data["socialType"].orEmpty()
            )
            ChatNotificationManager.showSocialNotification(
                context = applicationContext,
                socialType = data["socialType"].orEmpty(),
                actorName = data["actorName"] ?: "Alguien",
                actorUsername = data["actorUsername"].orEmpty(),
                eventId = data["eventId"].orEmpty(),
                objectId = data["postId"] ?: data["storyId"].orEmpty()
            )
            return
        }

        // Extraer datos del payload 'data' o 'notification'
        val channelId = data["channelId"] ?: data["channel_id"] ?: "general"
        val channelName = data["channelName"] ?: data["channel_name"] ?: "Chat"
        val senderName = data["senderName"] ?: data["sender_name"]
            ?: remoteMessage.notification?.title ?: "Compañero"
        val senderEmail = data["senderEmail"] ?: data["sender_email"] ?: ""
        val messageText = data["text"] ?: data["message"]
            ?: remoteMessage.notification?.body ?: "Nuevo mensaje recibido"
        val isGroup = data["isGroup"]?.toBoolean() ?: data["is_group"]?.toBoolean() ?: false

        Log.d(TAG, "Parsing FCM Push: channelId=$channelId, sender=$senderName, text=$messageText, isGroup=$isGroup")

        // Mostrar notificación si no estamos en este chat activo o si la app está en 2do plano
        if (ChatNotificationManager.shouldNotify(channelId)) {
            ChatNotificationManager.showChatNotification(
                context = applicationContext,
                channelId = channelId,
                channelName = channelName,
                senderName = senderName,
                senderEmail = senderEmail,
                messageText = messageText,
                isGroup = isGroup,
                timestamp = remoteMessage.sentTime.takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        }
    }
}
