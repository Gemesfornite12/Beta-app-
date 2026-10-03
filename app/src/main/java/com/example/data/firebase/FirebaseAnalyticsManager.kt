package com.example.data.firebase

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

/** Principal records only screen/menu events, never chat content or account/message IDs. */
object FirebaseAnalyticsManager {
    private const val TAG = "FirebaseAnalytics"
    private const val PRINCIPAL_APPLICATION_ID = "com.aistudio.omnistudio.wkspea"
    private const val EVENT_SOCIAL_NOTIFICATION_RECEIVED = "social_notification_received"

    private val allowedUsageEvents = setOf(
        "sara_message_sent",
        "sara_attachment_sent",
        "private_chat_opened",
        "private_chat_message_sent"
    )

    private val allowedMenus = setOf(
        "home", "documents", "music_studio", "chat", "profile", "sara", "social", "maps"
    )

    private val allowedPopupMenus = setOf(
        "sara_event_type",
        "sara_advanced_tools",
        "sara_library",
        "sara_connectors",
        "sara_workspace_actions",
        "chat_overflow",
        "chat_language",
        "chat_user_actions",
        "chat_my_chats",
        "chat_create_group",
        "chat_start_direct",
        "chat_group_manage",
        "chat_user_info",
        "chat_language_picker",
        "chat_notification_settings",
        "chat_attachment_picker",
        "chat_reaction_picker",
        "home_quick_create",
        "home_document_actions",
        "home_music_actions",
        "maps_battery",
        "maps_hud_language",
        "maps_route_profile",
        "maps_route_language",
        "maps_route_battery"
    )

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
        if (!isAnalyticsEnabledVariant()) return
        try {
            instance(context).apply {
                setAnalyticsCollectionEnabled(true)
                if (isTestVariant()) logEvent("analytics_test_active", null)
            }
            val variant = if (isTestVariant()) "the isolated test app" else "Principal"
            Log.i(TAG, "Firebase Analytics enabled for $variant.")
        } catch (error: Exception) {
            Log.w(TAG, "Could not enable Firebase Analytics: ${error.message}")
        }
    }

    fun logScreenView(context: Context, screenName: String, screenClass: String) {
        if (!isAnalyticsEnabledVariant()) return
        runCatching {
            val params = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName.take(100))
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass.take(100))
            }
            instance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
        }.onFailure { Log.w(TAG, "Could not record screen view: ${it.message}") }
    }

    fun logMenuOpened(context: Context, menuName: String) {
        if (!isAnalyticsEnabledVariant() || menuName !in allowedMenus) return
        runCatching { instance(context).logEvent("menu_${menuName}_opened", null) }
            .onFailure { Log.w(TAG, "Could not record menu analytics event: ${it.message}") }
    }

    fun logMenuPopupOpened(context: Context, menuName: String) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test") || menuName !in allowedPopupMenus) return
        runCatching { instance(context).logEvent("popup_${menuName}_opened", null) }
            .onFailure { Log.w(TAG, "Could not record popup menu analytics event: ${it.message}") }
    }

    fun logUsageEvent(context: Context, eventName: String) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test") || eventName !in allowedUsageEvents) return
        runCatching { instance(context).logEvent(eventName, null) }
            .onFailure { Log.w(TAG, "Could not record app usage analytics event: ${it.message}") }
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

    private fun isTestVariant(): Boolean = BuildConfig.APPLICATION_ID.endsWith(".test")

    private fun isAnalyticsEnabledVariant(): Boolean =
        BuildConfig.APPLICATION_ID == PRINCIPAL_APPLICATION_ID || isTestVariant()

    private fun instance(context: Context): FirebaseAnalytics {
        return analytics ?: synchronized(this) {
            analytics ?: FirebaseAnalytics.getInstance(context.applicationContext).also {
                it.setAnalyticsCollectionEnabled(true)
                analytics = it
            }
        }
    }
}
