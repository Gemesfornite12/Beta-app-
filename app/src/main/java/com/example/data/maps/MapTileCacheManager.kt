package com.example.data.maps

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Estado de progreso de descarga y almacenamiento en caché de baldosas de mapa para uso sin conexión.
 */
data class PreCacheProgress(
    val isDownloading: Boolean = false,
    val percentage: Int = 0,
    val completedTiles: Long = 0,
    val totalTiles: Long = 0,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Gestor de almacenamiento en caché para MapLibre.
 *
 * Implementa una arquitectura de caché en múltiples niveles:
 * 1. Caché HTTP en disco (OkHttp Cache de 250 MB) con interceptor tolerante a fallos que
 *    fuerza el uso de caché local cuando la conectividad es intermitente o inexistente.
 * 2. Caché ambiental SQLite nativo de MapLibre (350 MB) para almacenar automáticamente las
 *    baldosas vectoriales, estilos y fuentes consultadas durante la navegación.
 * 3. Descarga previa bajo demanda de corredores de ruta para navegación 100% offline.
 */
object MapTileCacheManager {

    private const val HTTP_CACHE_SIZE = 250L * 1024L * 1024L // 250 MB
    private const val AMBIENT_SQLITE_CACHE_SIZE = 350L * 1024L * 1024L // 350 MB
    private const val MAX_OFFLINE_TILES = 100000L

    private const val PREFS_NAME = "maplibre_cache_prefs"
    private const val KEY_AUTO_CLEAN_DAYS = "auto_clean_days"
    const val DEFAULT_AUTO_CLEAN_DAYS = 15

    @Volatile
    private var isInitialized = false

    @Volatile
    private var httpDiskCache: Cache? = null

    /**
     * Obtiene la política configurada de eliminación automática en días (-1 = Nunca).
     */
    fun getAutoCleanDays(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AUTO_CLEAN_DAYS, DEFAULT_AUTO_CLEAN_DAYS)
    }

    /**
     * Guarda la política de eliminación automática de mapas descargados.
     */
    fun setAutoCleanDays(context: Context, days: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AUTO_CLEAN_DAYS, days).apply()
    }

    /**
     * Inicializa MapLibre y configura el pipeline de red con almacenamiento en caché persistente en disco.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext

        // 1. Inicializar el SDK de MapLibre primero de forma segura
        try {
            MapLibre.getInstance(appContext)
        } catch (_: Throwable) {}

        // 2. Configurar cliente OkHttp con caché en disco para MapLibre
        try {
            val cacheDirectory = File(appContext.cacheDir, "maplibre_tile_http_cache")
            if (!cacheDirectory.exists()) {
                cacheDirectory.mkdirs()
            }
            val diskCache = Cache(cacheDirectory, HTTP_CACHE_SIZE)
            httpDiskCache = diskCache

            val customOkHttpClient = OkHttpClient.Builder()
                .cache(diskCache)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            HttpRequestUtil.setOkHttpClient(customOkHttpClient)
        } catch (_: Throwable) {}

        // 3. Configurar la base de datos SQLite de almacenamiento ambiental de MapLibre
        try {
            val offlineManager = OfflineManager.getInstance(appContext)
            offlineManager.setMaximumAmbientCacheSize(
                AMBIENT_SQLITE_CACHE_SIZE,
                object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() {}
                    override fun onError(message: String) {}
                }
            )
            offlineManager.setOfflineMapboxTileCountLimit(MAX_OFFLINE_TILES)
            offlineManager.runPackDatabaseAutomatically(true)
        } catch (_: Throwable) {}

        // 4. Ejecutar limpieza automática en segundo plano de rutas antiguas según los días configurados
        try {
            cleanExpiredDownloads(appContext)
        } catch (_: Throwable) {}

        isInitialized = true
    }

    /**
     * Verifica si el dispositivo tiene conectividad a internet activa.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Calcula el tamaño total en megabytes utilizado actualmente por la caché de mapas
     * (combinando la caché HTTP en disco y las bases de datos de baldosas de MapLibre).
     */
    fun getCacheSizeMb(context: Context): Double {
        var totalBytes = 0L
        val appContext = context.applicationContext
        try {
            val httpCacheDir = File(appContext.cacheDir, "maplibre_tile_http_cache")
            if (httpCacheDir.exists()) {
                totalBytes += calculateDirectorySize(httpCacheDir)
            }
            val appFilesDir = appContext.filesDir
            val maplibreDbFiles = appFilesDir.listFiles { file ->
                file.name.contains("mbgl-offline")
            }
            maplibreDbFiles?.forEach { totalBytes += it.length() }

            // Bases de datos en databases/
            val dbDir = File(appContext.applicationInfo.dataDir, "databases")
            if (dbDir.exists()) {
                dbDir.listFiles { file -> file.name.contains("mbgl-offline") }?.forEach { totalBytes += it.length() }
            }
        } catch (_: Exception) {}
        return totalBytes / (1024.0 * 1024.0)
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }

    /**
     * Limpia completamente la memoria caché local en disco, elimina regiones sin conexión
     * y compacta la base de datos de MapLibre.
     */
    fun clearCache(context: Context, onComplete: () -> Unit = {}) {
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        Thread {
            try {
                // 1. Evict OkHttp Disk Cache
                try {
                    httpDiskCache?.evictAll()
                } catch (_: Throwable) {}

                // 2. Limpiar directorio HTTP
                val httpCacheDir = File(appContext.cacheDir, "maplibre_tile_http_cache")
                if (httpCacheDir.exists()) {
                    httpCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }

                // 3. Limpiar regiones offline y caché ambiental en MapLibre
                val offlineManager = OfflineManager.getInstance(appContext)

                offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) {
                        if (!offlineRegions.isNullOrEmpty()) {
                            for (region in offlineRegions) {
                                try {
                                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                        override fun onDelete() {}
                                        override fun onError(error: String) {}
                                    })
                                } catch (_: Throwable) {}
                            }
                        }

                        // 4. Limpiar e invalidar caché ambiental
                        offlineManager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
                            override fun onSuccess() {
                                offlineManager.packDatabase(object : OfflineManager.FileSourceCallback {
                                    override fun onSuccess() {
                                        mainHandler.post { onComplete() }
                                    }
                                    override fun onError(message: String) {
                                        mainHandler.post { onComplete() }
                                    }
                                })
                            }
                            override fun onError(message: String) {
                                offlineManager.packDatabase(object : OfflineManager.FileSourceCallback {
                                    override fun onSuccess() {
                                        mainHandler.post { onComplete() }
                                    }
                                    override fun onError(message: String) {
                                        mainHandler.post { onComplete() }
                                    }
                                })
                            }
                        })
                    }

                    override fun onError(error: String) {
                        offlineManager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
                            override fun onSuccess() {
                                offlineManager.packDatabase(object : OfflineManager.FileSourceCallback {
                                    override fun onSuccess() {
                                        mainHandler.post { onComplete() }
                                    }
                                    override fun onError(message: String) {
                                        mainHandler.post { onComplete() }
                                    }
                                })
                            }
                            override fun onError(message: String) {
                                mainHandler.post { onComplete() }
                            }
                        })
                    }
                })
            } catch (_: Throwable) {
                mainHandler.post { onComplete() }
            }
        }.start()
    }

    /**
     * Elimina automáticamente las rutas descargadas y baldosas cuya antigüedad supere los días configurados.
     * Invoca onComplete con el número de rutas eliminadas.
     */
    fun cleanExpiredDownloads(context: Context, onComplete: (deletedCount: Int) -> Unit = {}) {
        val days = getAutoCleanDays(context)
        if (days <= 0) {
            onComplete(0)
            return
        }

        val maxAgeMs = days.toLong() * 24L * 60L * 60L * 1000L
        val cutoffTime = System.currentTimeMillis() - maxAgeMs

        try {
            val offlineManager = OfflineManager.getInstance(context.applicationContext)
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (offlineRegions.isNullOrEmpty()) {
                        onComplete(0)
                        return
                    }

                    var deletedCount = 0
                    var processedCount = 0
                    val total = offlineRegions.size

                    for (region in offlineRegions) {
                        var shouldDelete = false
                        try {
                            val metadataBytes = region.metadata
                            if (metadataBytes != null) {
                                val metaStr = String(metadataBytes, Charsets.UTF_8)
                                val timestamp = if (metaStr.startsWith("Ruta_")) {
                                    metaStr.removePrefix("Ruta_").toLongOrNull()
                                } else null

                                if (timestamp != null && timestamp < cutoffTime) {
                                    shouldDelete = true
                                }
                            }
                        } catch (_: Throwable) {}

                        if (shouldDelete) {
                            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() {
                                    deletedCount++
                                    processedCount++
                                    if (processedCount >= total) {
                                        onComplete(deletedCount)
                                    }
                                }

                                override fun onError(error: String) {
                                    processedCount++
                                    if (processedCount >= total) {
                                        onComplete(deletedCount)
                                    }
                                }
                            })
                        } else {
                            processedCount++
                            if (processedCount >= total) {
                                onComplete(deletedCount)
                            }
                        }
                    }
                }

                override fun onError(error: String) {
                    onComplete(0)
                }
            })
        } catch (_: Throwable) {
            onComplete(0)
        }
    }

    /**
     * Descarga y almacena de forma persistente las baldosas de mapa de la ruta actual
     * cubriendo niveles de zoom clave (11 a 16) para que la navegación funcione 100% offline.
     */
    fun preCacheRouteTiles(
        context: Context,
        routePoints: List<LatLng>,
        styleUrl: String,
        onProgress: (PreCacheProgress) -> Unit
    ) {
        if (routePoints.isEmpty()) {
            onProgress(PreCacheProgress(errorMessage = "No hay puntos en la ruta para almacenar"))
            return
        }

        try {
            var minLat = 90.0
            var maxLat = -90.0
            var minLon = 180.0
            var maxLon = -180.0

            routePoints.forEach { pt ->
                if (pt.latitude < minLat) minLat = pt.latitude
                if (pt.latitude > maxLat) maxLat = pt.latitude
                if (pt.longitude < minLon) minLon = pt.longitude
                if (pt.longitude > maxLon) maxLon = pt.longitude
            }

            // Margen de protección perimetral alrededor de la traza de la ruta (~1.8 km)
            val margin = 0.016
            val bounds = LatLngBounds.Builder()
                .include(LatLng(minLat - margin, minLon - margin))
                .include(LatLng(maxLat + margin, maxLon + margin))
                .build()

            val density = context.resources.displayMetrics.density
            val definition = OfflineTilePyramidRegionDefinition(
                styleUrl,
                bounds,
                11.0, // Nivel de zoom regional/visión general
                16.0, // Nivel de zoom de detalle de calles para giros
                density
            )

            val metadata = "Ruta_${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
            val offlineManager = OfflineManager.getInstance(context.applicationContext)

            onProgress(PreCacheProgress(isDownloading = true, percentage = 0))

            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(region: OfflineRegion) {
                        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                val required = status.requiredResourceCount
                                val completed = status.completedResourceCount
                                val pct = if (required > 0) {
                                    ((100.0 * completed) / required).toInt().coerceIn(0, 100)
                                } else 0

                                if (status.isComplete) {
                                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    onProgress(
                                        PreCacheProgress(
                                            isDownloading = false,
                                            percentage = 100,
                                            completedTiles = status.completedTileCount,
                                            totalTiles = status.completedTileCount,
                                            isComplete = true
                                        )
                                    )
                                } else {
                                    onProgress(
                                        PreCacheProgress(
                                            isDownloading = true,
                                            percentage = pct,
                                            completedTiles = status.completedTileCount,
                                            totalTiles = required,
                                            isComplete = false
                                        )
                                    )
                                }
                            }

                            override fun onError(error: OfflineRegionError) {
                                onProgress(
                                    PreCacheProgress(
                                        isDownloading = false,
                                        errorMessage = error.message ?: "Error al descargar baldosas"
                                    )
                                )
                            }

                            override fun mapboxTileCountLimitExceeded(limit: Long) {
                                onProgress(
                                    PreCacheProgress(
                                        isDownloading = false,
                                        errorMessage = "Límite de baldosas alcanzado ($limit)"
                                    )
                                )
                            }
                        })
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        onProgress(
                            PreCacheProgress(
                                isDownloading = false,
                                errorMessage = error
                            )
                        )
                    }
                }
            )
        } catch (t: Throwable) {
            onProgress(PreCacheProgress(isDownloading = false, errorMessage = t.message))
        }
    }
}
