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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val OSM_FALLBACK_STYLE = "https://demotiles.maplibre.org/style.json"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val defaultLocation = remember { LatLng(9.9951797, -84.1403642) }
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var currentLocation by remember { mutableStateOf(defaultLocation) }
    var locationLabel by remember { mutableStateOf("Buscando tu ubicación...") }
    var hasRealLocation by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }
    var selectedProfile by remember { mutableStateOf(profiles.first()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<OrsRouteSummary?>(null) }
    var instructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var navigationMode by remember { mutableStateOf(false) }
    var navigationStep by remember { mutableStateOf(0) }

    val tts = remember {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    ttsInstance?.language = Locale("es", "ES")
                } catch (_: Exception) {}
            }
        }
        ttsInstance
    }

    val orsReady = BuildConfig.OPENROUTESERVICE_API_KEY.isNotBlank() &&
        BuildConfig.OPENROUTESERVICE_API_KEY != "MY_OPENROUTESERVICE_API_KEY"

    fun updateMapVisuals() {
        val map = mapLibreMapInstance ?: return

        userMarker?.let { map.removeMarker(it) }
        userMarker = map.addMarker(
            MarkerOptions()
                .position(currentLocation)
                .title(if (hasRealLocation) "Tu ubicación (GPS)" else "Ubicación inicial")
                .snippet(locationLabel)
        )

        destinationPoint?.let { dest ->
            destMarker?.let { map.removeMarker(it) }
            destMarker = map.addMarker(
                MarkerOptions()
                    .position(dest)
                    .title("Destino")
                    .snippet(destination)
            )
        }

        routePolyline?.let { map.removePolyline(it) }
        if (routePoints.isNotEmpty()) {
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(android.graphics.Color.parseColor("#10B981"))
                    .width(6f)
            )
            try {
                val boundsBuilder = LatLngBounds.Builder()
                boundsBuilder.include(currentLocation)
                routePoints.forEach { boundsBuilder.include(it) }
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120),
                    1000
                )
            } catch (_: Exception) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14.0))
            }
        }
    }

    fun updateLocation(location: Location) {
        val point = LatLng(location.latitude, location.longitude)
        currentLocation = point
        hasRealLocation = true
        locationLabel = String.format(Locale.getDefault(), "%.5f, %.5f", location.latitude, location.longitude)
        updateMapVisuals()

        if (orsReady) {
            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        OpenRouteServiceClient.api.reverseGeocode(
                            BuildConfig.OPENROUTESERVICE_API_KEY,
                            point.longitude,
                            point.latitude,
                            1
                        )
                    }
                    response.features.firstOrNull()?.properties?.label?.let {
                        locationLabel = it
                        updateMapVisuals()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {}
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateLocation(location)
            }
        }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 3000L, 5f, listener)
                    locationManager.getLastKnownLocation(provider)?.let { updateLocation(it) }
                }
            }
        } catch (_: SecurityException) {
            locationLabel = "No se pudo acceder a la ubicación"
        }
        onDispose {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.shutdown()
        }
    }

    LaunchedEffect(navigationMode, navigationStep, instructions) {
        if (navigationMode) {
            instructions.getOrNull(navigationStep)?.let {
                tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "omnistudio-navigation")
            }
        }
    }

    fun calculateRoute() {
        if (destination.isBlank() || loading) return
        scope.launch {
            loading = true
            errorText = null
            summary = null
            instructions = emptyList()
            routePoints = emptyList()
            navigationMode = false
            navigationStep = 0
            try {
                if (!orsReady) error("Configura OPENROUTESERVICE_API_KEY en Secrets.")
                val (feature, endPt) = withContext(Dispatchers.IO) {
                    val place = OpenRouteServiceClient.api
                        .geocode(BuildConfig.OPENROUTESERVICE_API_KEY, destination.trim(), 1)
                        .features
                        .firstOrNull() ?: error("No encontré ese destino.")
                    val c = place.geometry.coordinates
                    if (c.size < 2) error("El destino no tiene coordenadas válidas.")
                    val destLatLng = LatLng(c[1], c[0])
                    val result = OpenRouteServiceClient.api.route(
                        selectedProfile.first,
                        BuildConfig.OPENROUTESERVICE_API_KEY,
                        "${currentLocation.longitude},${currentLocation.latitude}",
                        "${c[0]},${c[1]}",
                        true
                    )
                    val feat = result.features.firstOrNull() ?: error("No se pudo calcular la ruta.")
                    Pair(feat, destLatLng)
                }
                destinationPoint = endPt
                summary = feature.properties.summary
                instructions = feature.properties.segments.flatMap { it.steps.map { step -> step.instruction } }
                val coords = feature.geometry.coordinates
                if (coords.isNotEmpty()) {
                    routePoints = coords.mapNotNull { pt ->
                        if (pt.size >= 2) LatLng(pt[1], pt[0]) else null
                    }
                }
                updateMapVisuals()
            } catch (e: Exception) {
                errorText = e.message ?: "No se pudo calcular la ruta."
            } finally {
                loading = false
            }
        }
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                mapLibreMapInstance = map
                map.cameraPosition = CameraPosition.Builder()
                    .target(currentLocation)
                    .zoom(14.0)
                    .build()

                // Cargar estilo OpenFreeMap Liberty con fallback a Demotiles
                map.setStyle(Style.Builder().fromUri(OPENFREEMAP_STYLE_URL)) {
                    updateMapVisuals()
                }

                map.addOnMapClickListener { tapped ->
                    destinationPoint = tapped
                    destination = String.format(Locale.getDefault(), "%.5f, %.5f", tapped.latitude, tapped.longitude)
                    if (orsReady) {
                        scope.launch {
                            try {
                                val rev = withContext(Dispatchers.IO) {
                                    OpenRouteServiceClient.api.reverseGeocode(
                                        BuildConfig.OPENROUTESERVICE_API_KEY,
                                        tapped.longitude,
                                        tapped.latitude,
                                        1
                                    )
                                }
                                rev.features.firstOrNull()?.properties?.label?.let { destination = it }
                            } catch (_: Exception) {}
                            calculateRoute()
                        }
                    } else {
                        updateMapVisuals()
                    }
                    true
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
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
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(
                color = if (hasRealLocation) Color(0xFF14532D) else Color(0xFF334155),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text("Tu ubicación actual", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(locationLabel, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (navigationMode && instructions.isNotEmpty()) {
                Surface(color = Color(0xFF064E3B), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Navegación activa", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            instructions.getOrNull(navigationStep) ?: "Ruta iniciada",
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                if (navigationStep < instructions.lastIndex) navigationStep++
                            }) {
                                Text("Siguiente indicación")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { navigationMode = false }) {
                                Text("Finalizar")
                            }
                        }
                    }
                }
            }

            Surface(color = Color(0xFF1E293B), shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Destino") },
                        placeholder = { Text("Ej. Parque Central de Heredia o toca el mapa") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Button(onClick = { profileMenuOpen = true }) {
                                Text(selectedProfile.second)
                            }
                            DropdownMenu(profileMenuOpen, { profileMenuOpen = false }) {
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.second) },
                                        onClick = {
                                            selectedProfile = profile
                                            profileMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = ::calculateRoute,
                            enabled = destination.isNotBlank() && !loading
                        ) {
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Calcular ruta")
                            }
                        }
                    }
                    Text("Inicio: $locationLabel", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    errorText?.let { Text(it, color = Color(0xFFFCA5A5), modifier = Modifier.padding(top = 6.dp)) }
                    summary?.let { s ->
                        val distance = String.format(Locale.getDefault(), "%.1f km", s.distance / 1000.0)
                        val duration = "${(s.duration / 60.0).roundToInt()} min"
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$distance • $duration • ${selectedProfile.second}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                navigationMode = true
                                navigationStep = 0
                            }) {
                                Text("Navegar")
                            }
                        }
                    }
                    if (instructions.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.height(110.dp)
                        ) {
                            items(instructions) {
                                Text("• $it", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
