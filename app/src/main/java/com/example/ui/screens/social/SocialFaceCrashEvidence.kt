package com.example.ui.screens.social

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps one minimal, on-device breadcrumb for a Social camera session. It stores only fixed stage
 * names, counters, SDK level, exception class and one static app stack site; never exception text,
 * images, landmarks, message content, account data, or a full stack trace.
 */
internal data class SocialFaceCrashRecord(
    val state: String,
    val stage: String,
    val apiLevel: Int,
    val frames: Long,
    val submissions: Long,
    val acceptedCallbacks: Long,
    val staleCallbacks: Long,
    val pending: Boolean,
    val pendingAgeMs: Long,
    val errorType: String? = null,
    val crashType: String? = null,
    val crashSite: String? = null
)

internal fun safeSocialFaceDiagnosticToken(value: String?, fallback: String = "unknown"): String =
    value.orEmpty()
        .take(96)
        .takeIf { it.matches(Regex("[A-Za-z0-9_$.#:-]{1,96}")) }
        ?: fallback

internal fun buildSocialFaceCrashEvidenceReport(record: SocialFaceCrashRecord): String = buildString {
    appendLine("Previous Social camera process evidence (local only)")
    appendLine("Previous session state: ${safeSocialFaceDiagnosticToken(record.state)}")
    appendLine("Last checkpoint: ${safeSocialFaceDiagnosticToken(record.stage)}")
    appendLine("Android API: ${record.apiLevel.coerceIn(1, 100)}")
    appendLine("Frames received: ${record.frames.coerceAtLeast(0)}")
    appendLine("Face submissions: ${record.submissions.coerceAtLeast(0)}")
    appendLine("Accepted callbacks: ${record.acceptedCallbacks.coerceAtLeast(0)}")
    appendLine("Stale callbacks: ${record.staleCallbacks.coerceAtLeast(0)}")
    appendLine("Frame still pending at last checkpoint: ${record.pending}")
    if (record.pending) appendLine("Pending duration at report time (ms): ${record.pendingAgeMs.coerceAtLeast(0)}")
    record.errorType?.let { appendLine("Last error type: ${safeSocialFaceDiagnosticToken(it)}") }
    record.crashType?.let { appendLine("Uncaught exception type: ${safeSocialFaceDiagnosticToken(it)}") }
    record.crashSite?.let { appendLine("First app stack site: ${safeSocialFaceDiagnosticToken(it)}") }
    appendLine("A process restart with an unfinished session means the process ended before a clean camera close; it does not by itself prove the cause.")
}

internal object SocialFaceCrashEvidence {
    private const val PREFS_NAME = "social_face_diagnostic_evidence"
    private const val ACTIVE = "active_"
    private const val PREVIOUS = "previous_"
    private const val SESSION_ACTIVE = "active"
    private val handlerInstalled = AtomicBoolean(false)

    /** Install once, wrapping and delegating to the crash handler already registered by the SDK. */
    fun onProcessStart(context: Context) {
        val appContext = context.applicationContext
        if (!handlerInstalled.compareAndSet(false, true)) return
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(ACTIVE + "state", null) == SESSION_ACTIVE) {
            val now = System.currentTimeMillis()
            val pendingSince = preferences.getLong(ACTIVE + "pending_since", 0L)
            val record = recordFrom(preferences, ACTIVE, state = "process-restarted", now = now)
            saveRecord(preferences, PREVIOUS, record, pendingSince)
            preferences.edit().remove(ACTIVE + "state").remove(ACTIVE + "pending_since").commit()
        }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { saveUncaughtCameraFailure(appContext, throwable) }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun beginCameraSession(context: Context) {
        val now = System.currentTimeMillis()
        val preferences = preferences(context)
        preferences.edit()
            .putString(ACTIVE + "state", SESSION_ACTIVE)
            .putString(ACTIVE + "stage", "CAMERA_SESSION_STARTED")
            .putInt(ACTIVE + "api", android.os.Build.VERSION.SDK_INT)
            .putLong(ACTIVE + "frames", 0L)
            .putLong(ACTIVE + "submissions", 0L)
            .putLong(ACTIVE + "accepted", 0L)
            .putLong(ACTIVE + "stale", 0L)
            .putBoolean(ACTIVE + "pending", false)
            .putLong(ACTIVE + "pending_since", 0L)
            .putLong(ACTIVE + "updated_at", now)
            .remove(ACTIVE + "error")
            .commit()
    }

    fun recordCheckpoint(
        context: Context,
        snapshot: SocialFaceLiveDiagnosticSnapshot,
        stage: String,
        pending: Boolean
    ) {
        val preferences = preferences(context)
        if (preferences.getString(ACTIVE + "state", null) != SESSION_ACTIVE) return
        val now = System.currentTimeMillis()
        val editor = preferences.edit()
            .putString(ACTIVE + "stage", safeSocialFaceDiagnosticToken(stage))
            .putInt(ACTIVE + "api", android.os.Build.VERSION.SDK_INT)
            .putLong(ACTIVE + "frames", snapshot.analyzerFramesReceived.coerceAtLeast(0))
            .putLong(ACTIVE + "submissions", snapshot.faceFrameSubmissions.coerceAtLeast(0))
            .putLong(ACTIVE + "accepted", snapshot.callbacksAccepted.coerceAtLeast(0))
            .putLong(ACTIVE + "stale", snapshot.callbacksStale.coerceAtLeast(0))
            .putBoolean(ACTIVE + "pending", pending)
            .putLong(ACTIVE + "updated_at", now)
        if (pending && !preferences.getBoolean(ACTIVE + "pending", false)) {
            editor.putLong(ACTIVE + "pending_since", now)
        } else if (!pending) {
            editor.putLong(ACTIVE + "pending_since", 0L)
        }
        snapshot.lastErrorType?.let { editor.putString(ACTIVE + "error", safeSocialFaceDiagnosticToken(it)) }
        editor.commit()
    }

    fun endCameraSession(context: Context) {
        val preferences = preferences(context)
        if (preferences.getString(ACTIVE + "state", null) != SESSION_ACTIVE) return
        preferences.edit()
            .putString(ACTIVE + "state", "closed")
            .putString(ACTIVE + "stage", "CAMERA_SESSION_CLOSED")
            .putBoolean(ACTIVE + "pending", false)
            .putLong(ACTIVE + "pending_since", 0L)
            .putLong(ACTIVE + "updated_at", System.currentTimeMillis())
            .commit()
    }

    fun previousReport(context: Context): String? {
        val preferences = preferences(context)
        if (!preferences.contains(PREVIOUS + "state")) return null
        val record = recordFrom(preferences, PREVIOUS, now = System.currentTimeMillis())
        return buildSocialFaceCrashEvidenceReport(record)
    }

    private fun saveUncaughtCameraFailure(context: Context, throwable: Throwable) {
        val preferences = preferences(context)
        if (preferences.getString(ACTIVE + "state", null) != SESSION_ACTIVE) return
        val now = System.currentTimeMillis()
        val existing = recordFrom(preferences, ACTIVE, now = now)
        val crashSite = throwable.stackTrace
            .firstOrNull { it.className.startsWith("com.example.") }
            ?.let { frame -> "${frame.className}#${frame.methodName}:${frame.lineNumber.coerceAtLeast(0)}" }
            ?.let { safeSocialFaceDiagnosticToken(it) }
        val record = existing.copy(
            state = "uncaught-crash",
            crashType = safeSocialFaceDiagnosticToken(throwable.javaClass.simpleName, "UnknownError"),
            crashSite = crashSite
        )
        saveRecord(preferences, PREVIOUS, record, preferences.getLong(ACTIVE + "pending_since", 0L))
        preferences.edit().remove(ACTIVE + "state").remove(ACTIVE + "pending_since").commit()
    }

    private fun recordFrom(
        preferences: SharedPreferences,
        prefix: String,
        state: String = preferences.getString(prefix + "state", "unknown") ?: "unknown",
        now: Long
    ): SocialFaceCrashRecord {
        val pending = preferences.getBoolean(prefix + "pending", false)
        val pendingSince = preferences.getLong(prefix + "pending_since", 0L)
        return SocialFaceCrashRecord(
            state = state,
            stage = preferences.getString(prefix + "stage", "unknown") ?: "unknown",
            apiLevel = preferences.getInt(prefix + "api", android.os.Build.VERSION.SDK_INT),
            frames = preferences.getLong(prefix + "frames", 0L),
            submissions = preferences.getLong(prefix + "submissions", 0L),
            acceptedCallbacks = preferences.getLong(prefix + "accepted", 0L),
            staleCallbacks = preferences.getLong(prefix + "stale", 0L),
            pending = pending,
            pendingAgeMs = if (pending && pendingSince > 0L) (now - pendingSince).coerceAtLeast(0L) else 0L,
            errorType = preferences.getString(prefix + "error", null),
            crashType = preferences.getString(prefix + "crash_type", null),
            crashSite = preferences.getString(prefix + "crash_site", null)
        )
    }

    private fun saveRecord(
        preferences: SharedPreferences,
        prefix: String,
        record: SocialFaceCrashRecord,
        pendingSince: Long
    ) {
        val editor = preferences.edit()
            .putString(prefix + "state", safeSocialFaceDiagnosticToken(record.state))
            .putString(prefix + "stage", safeSocialFaceDiagnosticToken(record.stage))
            .putInt(prefix + "api", record.apiLevel.coerceIn(1, 100))
            .putLong(prefix + "frames", record.frames.coerceAtLeast(0))
            .putLong(prefix + "submissions", record.submissions.coerceAtLeast(0))
            .putLong(prefix + "accepted", record.acceptedCallbacks.coerceAtLeast(0))
            .putLong(prefix + "stale", record.staleCallbacks.coerceAtLeast(0))
            .putBoolean(prefix + "pending", record.pending)
            .putLong(prefix + "pending_since", pendingSince)
            .putLong(prefix + "updated_at", System.currentTimeMillis())
            .remove(prefix + "error")
            .remove(prefix + "crash_type")
            .remove(prefix + "crash_site")
        record.errorType?.let { editor.putString(prefix + "error", safeSocialFaceDiagnosticToken(it)) }
        record.crashType?.let { editor.putString(prefix + "crash_type", safeSocialFaceDiagnosticToken(it)) }
        record.crashSite?.let { editor.putString(prefix + "crash_site", safeSocialFaceDiagnosticToken(it)) }
        editor.commit()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
