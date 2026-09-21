package com.example.ui.screens.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.api.OpenRouteServiceClient
import com.example.data.api.OrsProfiles
import com.example.data.api.OrsRouteSummary
import com.example.data.api.OrsStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon as MapLibreIcon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val profiles = listOf(
    OrsProfiles.DRIVING_CAR to "Automóvil",
    OrsProfiles.FOOT_WALKING to "Caminar",
    OrsProfiles.CYCLING_REGULAR to "Bicicleta",
    OrsProfiles.CYCLING_ELECTRIC to "Bicicleta eléctrica",
    OrsProfiles.FOOT_HIKING to "Senderismo",
    OrsProfiles.WHEELCHAIR to "Silla de ruedas"
)

private const val STYLE = "https://tiles.openfreemap.org/styles/liberty"

private fun getManeuverIcon(instruction: String): ImageVector {
    val l = instruction.lowercase()
    return when {
        "sharp left" in l || "fuerte a la izquierda" in l -> Icons.Default.TurnSharpLeft
        "sharp right" in l || "fuerte a la derecha" in l -> Icons.Default.TurnSharpRight
        "slight left" in l || "leve a la izquierda" in l -> Icons.Default.TurnSlightLeft
        "slight right" in l || "leve a la derecha" in l -> Icons.Default.TurnSlightRight
        "left" in l || "izquierda" in l -> Icons.Default.TurnLeft
        "right" in l || "derecha" in l -> Icons.Default.TurnRight
        "arrive" in l || "lleg" in l || "destin" in l -> Icons.Default.Place
        else -> Icons.Default.Navigation
    }
}

private fun distanceMeters(a: LatLng, b: LatLng): Float {
    val res = FloatArray(1)
    Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, res)
    return res[0]
}

private fun calculateBearing(from: LatLng, to: LatLng): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val lat2 = Math.toRadians(to.latitude)
    val lon2 = Math.toRadians(to.longitude)
    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val radians = atan2(y, x)
    return ((Math.toDegrees(radians) + 360) % 360).toFloat()
}

/**
 * Crea el Punto Azul (Blue Location Puck) con halo semitransparente, aro blanco y núcleo azul Google Maps.
 * Si se incluye [bearing], dibuja una flecha direccional orientada al rumbo de avance.
 */
private fun createBluePuckIcon(context: Context, bearing: Float? = null): MapLibreIcon {
    val sizePx = 64
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    // 1. Halo translúcido azul exterior (efecto pulso/radar)
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#443B82F6")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center - 2f, haloPaint)

    // 2. Anillo blanco de contraste con sombra
    val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, AndroidColor.parseColor("#44000000"))
    }
    canvas.drawCircle(center, center, center - 10f, whitePaint)

    // 3. Núcleo azul eléctrico (#2563EB)
    val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#2563EB")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center - 14f, bluePaint)

    // 4. Flecha direccional blanca apuntando al rumbo si está en movimiento
    if (bearing != null) {
        canvas.save()
        canvas.rotate(bearing, center, center)
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(center, center - 9f)
            lineTo(center + 5.5f, center + 4.5f)
            lineTo(center, center + 2f)
            lineTo(center - 5.5f, center + 4.5f)
            close()
        }
        canvas.drawPath(path, arrowPaint)
        canvas.restore()
    } else {
        // Punto blanco interior cuando está quieto
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 4f, dotPaint)
    }

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current

    val fallback = remember { LatLng(9.9951797, -84.1403642) }
    val lm = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var line by remember { mutableStateOf<Polyline?>(null) }

    var permission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permission = it }

    LaunchedEffect(Unit) {
        if (!permission) request.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var current by remember { mutableStateOf(fallback) }
    var currentBearing by remember { mutableFloatStateOf(0f) }
    var locationText by remember { mutableStateOf("Buscando tu ubicación...") }
    var real by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }
    var profile by remember { mutableStateOf(profiles.first()) }
    var menu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<OrsRouteSummary?>(null) }
    var routeSteps by remember { mutableStateOf<List<OrsStep>>(emptyList()) }
    var points by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Estados de navegación automática
    var navigating by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var distanceToNextManeuver by remember { mutableFloatStateOf(0f) }
    var isRecalculating by remember { mutableStateOf(false) }
    var offRouteCount by remember { mutableIntStateOf(0) }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    instance?.language = Locale("es", "ES")
                } catch (_: Exception) {}
            }
        }
        instance
    }

    val ors = BuildConfig.OPENROUTESERVICE_API_KEY.isNotBlank() &&
        BuildConfig.OPENROUTESERVICE_API_KEY != "MY_OPENROUTESERVICE_API_KEY"

    fun redraw() {
        val m = map ?: return

        // Dibujar o actualizar el PUNTO AZUL con rumbo
        val blueIcon = createBluePuckIcon(context, if (currentBearing != 0f) currentBearing else null)
        marker?.let { m.removeMarker(it) }
        marker = m.addMarker(
            MarkerOptions()
                .position(current)
                .icon(blueIcon)
                .title(if (real) "Tu ubicación (GPS)" else "Ubicación inicial")
                .snippet(locationText)
        )

        destinationPoint?.let { p ->
            destMarker?.let { m.removeMarker(it) }
            destMarker = m.addMarker(
                MarkerOptions()
                    .position(p)
                    .title("Destino")
                    .snippet(destination)
            )
        }

        line?.let { m.removePolyline(it) }
        if (points.isNotEmpty()) {
            line = m.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(AndroidColor.parseColor("#10B981"))
                    .width(6f)
            )
            if (!navigating) {
                try {
                    val b = LatLngBounds.Builder()
                    b.include(current)
                    points.forEach { b.include(it) }
                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120), 1000)
                } catch (_: Exception) {
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 14.0))
                }
            }
        }
    }

    // Centrar la cámara en el usuario / punto azul con vista de conducción en 3D
    fun trackUserCamera(target: LatLng, bearing: Float?) {
        val m = map ?: return
        try {
            val camBuilder = CameraPosition.Builder()
                .target(target)
                .zoom(18.0)
                .tilt(45.0)

            if (bearing != null && (bearing > 0.1f || bearing < -0.1f)) {
                camBuilder.bearing(bearing.toDouble())
            }
            m.animateCamera(CameraUpdateFactory.newCameraPosition(camBuilder.build()), 700)
        } catch (_: Exception) {}
    }

    fun speakInstruction(text: String) {
        if (text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "omnistudio-nav")
        }
    }

    fun speakCurrentInstruction() {
        routeSteps.getOrNull(step)?.let {
            speakInstruction(it.instruction)
        }
    }

    /**
     * Recalcula la ruta en tiempo real a partir de la posición actual del usuario.
     * Esencial si la persona se desvía o toma otra calle para que nunca se pierda.
     */
    fun recalculateRoute(userPos: LatLng) {
        val destPt = destinationPoint ?: return
        if (isRecalculating || !ors) return

        scope.launch {
            isRecalculating = true
            try {
                speakInstruction("Recalculando ruta...")
                val feature = withContext(Dispatchers.IO) {
                    OpenRouteServiceClient.api.route(
                        profile.first,
                        BuildConfig.OPENROUTESERVICE_API_KEY,
                        "${userPos.longitude},${userPos.latitude}",
                        "${destPt.longitude},${destPt.latitude}",
                        true
                    ).features.firstOrNull()
                }

                if (feature != null) {
                    summary = feature.properties.summary
                    val segments = feature.properties.segments
                    val newSteps = segments.flatMap { it.steps }
                    val newPts = feature.geometry.coordinates.mapNotNull {
                        if (it.size >= 2) LatLng(it[1], it[0]) else null
                    }
                    if (newPts.isNotEmpty()) {
                        routeSteps = newSteps
                        points = newPts
                        step = 0
                        offRouteCount = 0
                        redraw()
                        speakCurrentInstruction()
                    }
                }
            } catch (_: Exception) {
            } finally {
                isRecalculating = false
            }
        }
    }

    /**
     * Comprueba la posición del usuario respecto a la ruta y avanza de paso AUTOMÁTICAMENTE:
     * - Calcula distancia a la maniobra del paso actual.
     * - Si está a menos de 30m de la maniobra o si superó el índice del waypoint, avanza al paso siguiente.
     * - Si el usuario se desvía más de 45 metros de la ruta trazada, recalcula automáticamente la ruta.
     */
    fun checkAutoStepProgression(newPos: LatLng) {
        if (!navigating || routeSteps.isEmpty() || points.isEmpty() || isRecalculating) return

        // 1. Detección de desviación de ruta (Off-route): distancia al punto más cercano de la ruta
        var minDistanceToPolyline = Float.MAX_VALUE
        var closestIdx = 0
        points.forEachIndexed { idx, pt ->
            val d = distanceMeters(newPos, pt)
            if (d < minDistanceToPolyline) {
                minDistanceToPolyline = d
                closestIdx = idx
            }
        }

        // Si el usuario se aleja más de 45 metros de la ruta trazada
        if (minDistanceToPolyline > 45f) {
            offRouteCount++
            // Si confirma 2 lecturas consecutivas fuera de ruta, recalcular
            if (offRouteCount >= 2) {
                recalculateRoute(newPos)
                return
            }
        } else {
            offRouteCount = 0
        }

        // 2. Cálculo de distancia a la maniobra actual
        val currentStepObj = routeSteps.getOrNull(step) ?: return
        val endWpIdx = currentStepObj.way_points.getOrNull(1)

        if (endWpIdx != null && endWpIdx in points.indices) {
            val maneuverPoint = points[endWpIdx]
            val dist = distanceMeters(newPos, maneuverPoint)
            distanceToNextManeuver = dist

            // Si está dentro de 30 metros de la intersección o giro, pasar automáticamente al siguiente paso
            if (dist <= 30f && step < routeSteps.lastIndex) {
                step++
                return
            }
        }

        // 3. Avance automático si el usuario avanzó a tramos posteriores
        if (minDistanceToPolyline < 45f) {
            for (sIdx in (step + 1)..routeSteps.lastIndex) {
                val st = routeSteps[sIdx]
                val startWp = st.way_points.getOrNull(0) ?: 0
                val endWp = st.way_points.getOrNull(1) ?: (points.size - 1)
                if (closestIdx in startWp..endWp) {
                    step = sIdx
                    break
                }
            }
        }
    }

    // Actualiza la posición con cada evento de movimiento real del GPS
    fun update(loc: Location) {
        val newPos = LatLng(loc.latitude, loc.longitude)
        if (loc.hasBearing() && loc.bearing != 0f) {
            currentBearing = loc.bearing
        } else if (distanceMeters(current, newPos) > 3f) {
            currentBearing = calculateBearing(current, newPos)
        }
        current = newPos
        real = true
        locationText = String.format(Locale.getDefault(), "%.5f, %.5f", loc.latitude, loc.longitude)

        // Actualizar el punto azul en el mapa
        redraw()

        // Si está en navegación, seguir al usuario en tiempo real
        if (navigating) {
            trackUserCamera(current, currentBearing)
            checkAutoStepProgression(current)
        }

        if (ors && !navigating) {
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) {
                        OpenRouteServiceClient.api.reverseGeocode(
                            BuildConfig.OPENROUTESERVICE_API_KEY,
                            current.longitude,
                            current.latitude,
                            1
                        )
                    }
                    r.features.firstOrNull()?.properties?.label?.let {
                        locationText = it
                        redraw()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Suscripción al sensor GPS real con alta frecuencia (1s, 1m) para navegación activa
    DisposableEffect(permission) {
        if (!permission) return@DisposableEffect onDispose {}
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                update(l)
            }
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 1000L, 1f, listener)
                    lm.getLastKnownLocation(p)?.let { update(it) }
                }
            }
        } catch (_: SecurityException) {
            locationText = "No se pudo acceder a la ubicación"
        }
        onDispose {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.shutdown()
        }
    }

    // Pronuncia la instrucción cada vez que cambia el paso (automático o manual)
    LaunchedEffect(step, navigating) {
        if (navigating && routeSteps.isNotEmpty()) {
            val s = routeSteps.getOrNull(step)
            if (s != null) {
                val maneuverWp = s.way_points.getOrNull(0)
                val targetPt = if (maneuverWp != null && maneuverWp in points.indices) points[maneuverWp] else current
                trackUserCamera(targetPt, currentBearing)
                speakCurrentInstruction()
            }
        }
    }

    fun route() {
        if (destination.isBlank() || loading) return
        scope.launch {
            loading = true
            error = null
            summary = null
            routeSteps = emptyList()
            points = emptyList()
            navigating = false
            step = 0
            offRouteCount = 0
            try {
                if (!ors) error("Configura OPENROUTESERVICE_API_KEY en Secrets.")
                val result = withContext(Dispatchers.IO) {
                    val place = OpenRouteServiceClient.api
                        .geocode(BuildConfig.OPENROUTESERVICE_API_KEY, destination.trim(), 1)
                        .features
                        .firstOrNull() ?: error("No encontré ese destino.")
                    val c = place.geometry.coordinates
                    if (c.size < 2) error("El destino no tiene coordenadas válidas.")
                    val feature = OpenRouteServiceClient.api.route(
                        profile.first,
                        BuildConfig.OPENROUTESERVICE_API_KEY,
                        "${current.longitude},${current.latitude}",
                        "${c[0]},${c[1]}",
                        true
                    ).features.firstOrNull() ?: error("No se pudo calcular la ruta.")
                    feature to LatLng(c[1], c[0])
                }
                destinationPoint = result.second
                summary = result.first.properties.summary
                val segments = result.first.properties.segments
                routeSteps = segments.flatMap { it.steps }
                points = result.first.geometry.coordinates.mapNotNull {
                    if (it.size >= 2) LatLng(it[1], it[0]) else null
                }
                redraw()
            } catch (e: Exception) {
                error = e.message ?: "No se pudo calcular la ruta."
            } finally {
                loading = false
            }
        }
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { m ->
                map = m
                m.cameraPosition = CameraPosition.Builder().target(current).zoom(14.0).build()
                m.setStyle(Style.Builder().fromUri(STYLE)) {
                    redraw()
                }
                m.addOnMapClickListener { p ->
                    if (!navigating) {
                        destinationPoint = p
                        destination = String.format(Locale.getDefault(), "%.5f, %.5f", p.latitude, p.longitude)
                        if (ors) {
                            scope.launch {
                                try {
                                    val r = withContext(Dispatchers.IO) {
                                        OpenRouteServiceClient.api.reverseGeocode(
                                            BuildConfig.OPENROUTESERVICE_API_KEY,
                                            p.longitude,
                                            p.latitude,
                                            1
                                        )
                                    }
                                    r.features.firstOrNull()?.properties?.label?.let {
                                        destination = it
                                    }
                                } catch (_: Exception) {}
                                route()
                            }
                        } else {
                            redraw()
                        }
                    }
                    true
                }
            }
        }
    }

    DisposableEffect(owner, mapView) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            if (!navigating) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.size(8.dp))
                            Text("Mapas y rutas", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ChevronLeft, "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Capa 1: Mapa a pantalla completa
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Capa 2: Si ESTÁ NAVEGANDO, mostrar única y exclusivamente el HUD inmersivo de conducción
            if (navigating) {
                // HUD Superior: Indicación activa con avance automático
                Surface(
                    color = Color(0xFF064E3B),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    val currentStep = routeSteps.getOrNull(step)
                    val instructionText = currentStep?.instruction?.ifBlank { "Continúa recto por la vía" } ?: "Continúa por la ruta"
                    val stepDist = if (distanceToNextManeuver > 0f) {
                        if (distanceToNextManeuver >= 1000) String.format(Locale.getDefault(), "En %.1f km", distanceToNextManeuver / 1000.0)
                        else "En ${distanceToNextManeuver.roundToInt()} m"
                    } else currentStep?.let {
                        if (it.distance >= 1000) String.format(Locale.getDefault(), "En %.1f km", it.distance / 1000.0)
                        else "En ${it.distance.roundToInt()} m"
                    } ?: ""

                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF10B981), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getManeuverIcon(instructionText),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF34D399), CircleShape)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "AVANCE AUTOMÁTICO",
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    if (routeSteps.isNotEmpty()) {
                                        Text(
                                            "Paso ${step + 1} de ${routeSteps.size}",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Indicador GPS en vivo
                                Surface(
                                    color = if (isRecalculating) Color(0xFFEAB308).copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    if (isRecalculating) Color(0xFFFACC15) else Color(0xFF34D399),
                                                    CircleShape
                                                )
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (isRecalculating) "Recalculando..." else "GPS Activo",
                                            color = if (isRecalculating) Color(0xFFFEF08A) else Color(0xFFA7F3D0),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Botón de repetir audio TTS
                                IconButton(
                                    onClick = ::speakCurrentInstruction,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "Repetir indicación por voz",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (stepDist.isNotBlank()) {
                            Text(
                                text = stepDist,
                                color = Color(0xFFA7F3D0),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = instructionText,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        // Controles manuales opcionales de respaldo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (step > 0) step--
                                },
                                enabled = step > 0,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ArrowBack, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Anterior", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (step < routeSteps.lastIndex) {
                                        step++
                                    } else {
                                        navigating = false
                                        step = 0
                                        redraw()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text(
                                    if (step < routeSteps.lastIndex) "Siguiente" else "¡Llegaste!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (step < routeSteps.lastIndex) Icons.Default.ArrowForward else Icons.Default.CheckCircle,
                                    null,
                                    Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Botón flotante para recentrar la cámara en el punto azul del usuario
                FloatingActionButton(
                    onClick = {
                        trackUserCamera(current, currentBearing)
                    },
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 145.dp)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recentrar en mi ubicación")
                }

                // HUD Inferior: Métricas de viaje y botón para finalizar navegación
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.96f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        summary?.let { s ->
                            val d = String.format(Locale.getDefault(), "%.1f km", s.distance / 1000.0)
                            val t = "${(s.duration / 60.0).roundToInt()} min"
                            val a = remember(s) {
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(
                                    Date(System.currentTimeMillis() + s.duration.toLong() * 1000L)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(t, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Tiempo restante", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(d, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Distancia", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(a, color = Color(0xFF10B981), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text("Llegada estimada", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                navigating = false
                                step = 0
                                redraw()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Finalizar navegación", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Capa 2 (Alternativa): Modo normal de búsqueda, cálculo de ruta y selección de destino
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Chip superior de ubicación actual
                    Surface(
                        color = if (real) Color(0xFF14532D).copy(alpha = 0.95f) else Color(0xFF1E293B).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MyLocation, null, tint = if (real) Color(0xFF4ADE80) else Color(0xFF38BDF8))
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (real) "Tu ubicación actual (Punto Azul GPS)" else "Ubicación inicial",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    locationText,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Panel inferior de destino y cálculo de ruta
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = destination,
                                onValueChange = { destination = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Destino", color = Color.White.copy(0.8f)) },
                                placeholder = { Text("Toca el mapa o escribe un destino", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF475569)
                                )
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box {
                                    FilledTonalButton(onClick = { menu = true }) {
                                        Text(profile.second)
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        profiles.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.second) },
                                                onClick = {
                                                    profile = p
                                                    menu = false
                                                    if (destination.isNotBlank()) route()
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                Button(
                                    onClick = ::route,
                                    enabled = destination.isNotBlank() && !loading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Directions, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Calcular ruta")
                                    }
                                }
                            }

                            error?.let {
                                Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            }

                            summary?.let { s ->
                                val distanceStr = String.format(Locale.getDefault(), "%.1f km", s.distance / 1000.0)
                                val durationStr = "${(s.duration / 60.0).roundToInt()} min"
                                val etaStr = remember(s) {
                                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(
                                        Date(System.currentTimeMillis() + s.duration.toLong() * 1000L)
                                    )
                                }

                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "$distanceStr • $durationStr",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                "${profile.second} • Llega: $etaStr",
                                                color = Color(0xFF10B981),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                navigating = true
                                                step = 0
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF10B981),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(Icons.Default.Navigation, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Navegar", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (routeSteps.isNotEmpty()) {
                                Text(
                                    "Pasos calculados (${routeSteps.size}):",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                                LazyColumn(
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.height(100.dp)
                                ) {
                                    items(routeSteps) { st ->
                                        Text(
                                            "• ${st.instruction}",
                                            color = Color.White.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
