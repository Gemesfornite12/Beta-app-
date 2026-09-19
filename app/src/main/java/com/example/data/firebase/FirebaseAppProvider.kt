package com.example.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * Única fuente de inicialización para Auth, Firestore y Storage.
 * Requiere google-services.json dentro de app/ durante la compilación.
 */
object FirebaseAppProvider {
    @Synchronized
    fun get(context: Context): FirebaseApp {
        val appContext = context.applicationContext
        return FirebaseApp.getApps(appContext).firstOrNull()
            ?: FirebaseApp.initializeApp(appContext)
            ?: error(
                "Firebase no pudo inicializarse. " +
                    "Verifica google-services.json en app/ y el proyecto Firebase correcto."
            )
    }
}
