package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Selects only the shipped Principal release and preserves existing .test behavior. */
internal object CrashlyticsBuildPolicy {
    private const val PRINCIPAL_APPLICATION_ID = "com.aistudio.omnistudio.wkspea"

    fun isCollectionEnabled(applicationId: String, isDebuggable: Boolean): Boolean =
        applicationId.endsWith(".test") ||
            (applicationId == PRINCIPAL_APPLICATION_ID && !isDebuggable)
}

object FirebaseCrashlyticsManager {
    private const val TAG = "FirebaseCrashlytics"

    fun initialize(context: Context) {
        if (!CrashlyticsBuildPolicy.isCollectionEnabled(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)) return
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            Log.i(TAG, "Crashlytics enabled for the approved app variant.")
        }.onFailure { Log.w(TAG, "Could not enable Crashlytics.") }
    }
}
