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

/** Requests server-verified FCM notifications for OmniStudio Social in the isolated .test build. */
object SupabaseSocialPushService {
    private const val TAG = "SupabaseSocialPush"
    private const val FUNCTION_PATH = "/functions/v1/notify-social-event"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun likedPost(ownerUid: String, postId: String) = request(
        JSONObject().put("socialType", "like").put("targetUid", ownerUid).put("postId", postId)
    )

    suspend fun followedUser(targetUid: String) = request(
        JSONObject().put("socialType", "follow").put("targetUid", targetUid)
    )

    suspend fun acceptedFollow(requesterUid: String) = request(
        JSONObject().put("socialType", "follow_accepted").put("targetUid", requesterUid)
    )

    suspend fun publishedPost(postId: String) = request(
        JSONObject().put("socialType", "new_post").put("postId", postId)
    )

    suspend fun publishedStory(storyId: String) = request(
        JSONObject().put("socialType", "new_story").put("storyId", storyId)
    )

    private suspend fun request(payload: JSONObject) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test")) return
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

        payload.put("eventType", "social")
        val request = Request.Builder()
            .url(supabaseUrl + FUNCTION_PATH)
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $firebaseIdToken")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Social push endpoint returned HTTP ${response.code}.")
                } else {
                    val body = response.body?.string().orEmpty()
                    val sent = runCatching { JSONObject(body).optInt("sent", -1) }.getOrDefault(-1)
                    Log.i(TAG, "Social push request accepted; sent=$sent.")
                }
            }
        }
    }
}
