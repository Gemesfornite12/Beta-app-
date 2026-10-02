package com.example.data.firebase

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

/** Analytics is enabled only in the isolated test application and records non-identifying events. */
object FirebaseAnalyticsManager {
    private const val TAG = "FirebaseAnalytics"
    private const val EVENT_SOCIAL_NOTIFICATION_RECEIVED = "social_notification_received"

    private val allowedSocialEvents = setOf(
        "social_post_created",
        "social_story_created",
        "social_like",
        "social_unlike",
        "social_follow",
        "social_follow_request",
        "social_follow_accepted",
        "social_unfollow"
    )

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test")) return
        try {
            instance(context).apply {
                setAnalyticsCollectionEnabled(true)
                logEvent("analytics_test_active", null)
            }
            Log.i(TAG, "Firebase Analytics enabled for the isolated test app.")
        } catch (error: Exception) {
            Log.w(TAG, "Could not enable Firebase Analytics: ${error.message}")
        }
    }

    fun logSocialEvent(context: Context, eventName: String) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test") || eventName !in allowedSocialEvents) return
        runCatching { instance(context).logEvent(eventName, null) }
            .onFailure { Log.w(TAG, "Could not record Social analytics event: ${it.message}") }
    }

    fun logSocialNotificationReceived(context: Context, socialType: String) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test")) return
        runCatching {
            val params = Bundle().apply {
                putString("notification_type", socialType.take(40))
            }
            instance(context).logEvent(EVENT_SOCIAL_NOTIFICATION_RECEIVED, params)
        }.onFailure { Log.w(TAG, "Could not record notification analytics event: ${it.message}") }
    }

    private fun instance(context: Context): FirebaseAnalytics {
        return analytics ?: synchronized(this) {
            analytics ?: FirebaseAnalytics.getInstance(context.applicationContext).also {
                it.setAnalyticsCollectionEnabled(true)
                analytics = it
            }
        }
    }
}
