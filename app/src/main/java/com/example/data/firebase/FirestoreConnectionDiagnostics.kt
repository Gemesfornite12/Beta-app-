package com.example.data.firebase

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

/** Diagnóstico mínimo: prueba una escritura real, sin usar la caché local. */
class FirestoreConnectionDiagnostics(context: Context) {
    private val app = FirebaseAppProvider.get(context)
    private val db = FirebaseFirestore.getInstance(app)
    private val TAG = "FirestoreDiagnostics"

    suspend fun run(): String {
        val projectId = app.options.projectId ?: "desconocido"
        Log.d(TAG, "Iniciando diagnóstico para proyecto: $projectId")
        return try {
            db.collection("_diagnostics")
                .document("ping")
                .set(
                    mapOf(
                        "projectId" to projectId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "source" to "android-diagnostic"
                    )
                )
                .await()

            "OK: escritura confirmada en Cloud Firestore. Proyecto=$projectId"
        } catch (e: Exception) {
            val msg = "ERROR REAL: ${e::class.simpleName}: ${e.message}"
            Log.e(TAG, msg)
            msg
        }
    }
}
