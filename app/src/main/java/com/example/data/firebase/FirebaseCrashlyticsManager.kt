package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Enables crash and stability reporting only for the isolated .test application. */
object FirebaseCrashlyticsManager {
    private const val TAG = "FirebaseCrashlytics"

    fun initialize(context: Context) {
        if (!BuildConfig.APPLICATION_ID.endsWith(".test")) return
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            Log.i(TAG, "Crashlytics enabled for the isolated test app.")
        }.onFailure { Log.w(TAG, "Could not enable Crashlytics: ${it.message}") }
    }
}
