package com.example.data.supabase

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Requests server-verified FCM pushes for .test chat messages and call invitations. */
object SupabaseChatPushService {
    private const val TAG = "SupabaseChatPush"
    private const val FUNCTION_PATH = "/functions/v1/notify-chat-message"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun requestNotification(channelId: String, messageId: String) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test")) return
        postNotification(
            JSONObject()
                .put("eventType", "message")
                .put("channelId", channelId)
                .put("messageId", messageId)
        )
    }

    suspend fun requestCallNotification(channelId: String, callId: String) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test")) return
        postNotification(
            JSONObject()
                .put("eventType", "call")
                .put("channelId", channelId)
                .put("callId", callId)
        )
    }

    private suspend fun postNotification(payload: JSONObject) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val firebaseIdToken = user.getIdToken(false).await().token
        if (firebaseIdToken.isNullOrBlank()) {
            Log.w(TAG, "Skipping push request because Firebase did not provide an ID token.")
            return
        }

        val supabaseUrl = BuildConfig.SUPABASE_PROJECT_URL.trimEnd('/')
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) {
            Log.w(TAG, "Skipping push request because Supabase configuration is unavailable.")
            return
        }

        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(supabaseUrl + FUNCTION_PATH)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $firebaseIdToken")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Push endpoint returned HTTP ${response.code}.")
                } else {
                    val sent = runCatching {
                        JSONObject(response.body?.string().orEmpty()).optInt("sent", -1)
                    }.getOrDefault(-1)
                    Log.i(TAG, "Push request accepted; sent=$sent.")
                }
            }
        }
    }
}
