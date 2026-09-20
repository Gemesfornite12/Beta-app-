package com.example.data.firebase

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import android.util.Log

/** Diagnóstico mínimo: prueba una escritura real, sin usar la caché local. */
class FirestoreConnectionDiagnostics(context: Context) {
    private val app = FirebaseAppProvider.get(context)
    private val db = FirebaseFirestore.getInstance(app)
    private val rtdb = FirebaseDatabase.getInstance(app)
    private val TAG = "FirestoreDiagnostics"

    suspend fun run(): String {
        val projectId = app.options.projectId ?: "desconocido"
        Log.d(TAG, "Iniciando diagnóstico para proyecto: $projectId")
        
        val results = mutableListOf<String>()
        
        // Firestore
        try {
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
            results.add("Firestore: OK")
        } catch (e: Exception) {
            results.add("Firestore ERROR: ${e.message}")
        }

        // RTDB
        try {
            rtdb.getReference("_diagnostics").child("ping").setValue(
                mapOf(
                    "projectId" to projectId,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            results.add("RTDB: OK")
        } catch (e: Exception) {
            results.add("RTDB ERROR: ${e.message}")
        }

        return results.joinToString(" | ")
    }
}
