package com.example.ui.screens.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
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
import kotlin.math.roundToInt

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

    var navigating by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(0) }

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

        marker?.let { m.removeMarker(it) }
        marker = m.addMarker(
            MarkerOptions()
                .position(current)
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
                    .color(android.graphics.Color.parseColor("#10B981"))
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

    fun animateToStep(stepIndex: Int) {
        val m = map ?: return
        val currentStepObj = routeSteps.getOrNull(stepIndex)
        val coordIndex = currentStepObj?.way_points?.firstOrNull()
        val target = if (coordIndex != null && coordIndex in points.indices) {
            points[coordIndex]
        } else {
            points.getOrNull(stepIndex) ?: current
        }

        try {
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(17.5)
                        .tilt(45.0)
                        .build()
                ),
                800
            )
        } catch (_: Exception) {}
    }

    fun speakCurrentInstruction() {
        routeSteps.getOrNull(step)?.let {
            if (it.instruction.isNotBlank()) {
                tts?.speak(it.instruction, TextToSpeech.QUEUE_FLUSH, null, "omnistudio-nav")
            }
        }
    }

    fun update(loc: Location) {
        current = LatLng(loc.latitude, loc.longitude)
        real = true
        locationText = String.format(Locale.getDefault(), "%.5f, %.5f", loc.latitude, loc.longitude)
        redraw()
        if (ors) {
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

    DisposableEffect(permission) {
        if (!permission) return@DisposableEffect onDispose {}
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) = update(l)
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 3000L, 5f, listener)
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

    // TTS y movimiento de cámara cuando cambia el paso de navegación
    LaunchedEffect(navigating, step) {
        if (navigating && routeSteps.isNotEmpty()) {
            animateToStep(step)
            speakCurrentInstruction()
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
        // Contenedor principal: El mapa es SIEMPRE la capa de fondo fija (sin reparenting)
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

            // Capa 2: Si ESTÁ NAVEGANDO, mostrar única y exclusivamente el modo inmersivo HUD
            if (navigating) {
                // HUD Superior: Indicación de maniobra, paso actual, siguiente / anterior y voz
                Surface(
                    color = Color(0xFF064E3B),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    val currentStep = routeSteps.getOrNull(step)
                    val instructionText = currentStep?.instruction?.ifBlank { "Sigue por la vía" } ?: "Continúa por la ruta"
                    val stepDistance = currentStep?.let {
                        if (it.distance >= 1000) String.format(Locale.getDefault(), "%.1f km", it.distance / 1000.0)
                        else "${it.distance.roundToInt()} m"
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
                                        .size(36.dp)
                                        .background(Color(0xFF10B981), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getManeuverIcon(instructionText),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "NAVEGACIÓN ACTIVA",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    if (routeSteps.isNotEmpty()) {
                                        Text(
                                            "Paso ${step + 1} de ${routeSteps.size}",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
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
                                    contentDescription = "Repetir voz",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = instructionText,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        )

                        if (stepDistance.isNotBlank()) {
                            Text(
                                text = "Distancia de este tramo: $stepDistance",
                                color = Color(0xFFA7F3D0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Botones de control de paso
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
                                Text("Anterior")
                            }

                            Button(
                                onClick = {
                                    if (step < routeSteps.lastIndex) {
                                        step++
                                    } else {
                                        // Último paso completado
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
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    if (step < routeSteps.lastIndex) Icons.Default.ArrowForward else Icons.Default.Place,
                                    null,
                                    Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // HUD Inferior: Métricas de viaje y botón para finalizar navegación
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.96f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    shadowElevation = 14.dp,
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
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(
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
                            Icon(Icons.Default.MyLocation, null, tint = if (real) Color(0xFF4ADE80) else Color.White)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (real) "Tu ubicación actual (GPS)" else "Ubicación inicial",
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
                                                profile.second,
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
