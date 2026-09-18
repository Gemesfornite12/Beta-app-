package com.example.data.firebase

import android.util.Log
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

        // Sincronizar token con Firestore para el usuario activo
        try {
            val db = FirebaseFirestore.getInstance()
            val tokenData = hashMapOf(
                "fcmToken" to token,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("fcm_device_tokens").document(token.hashCode().toString())
                .set(tokenData, SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "Error saving token in Firestore: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // Extraer datos del payload 'data' o 'notification'
        val data = remoteMessage.data
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
