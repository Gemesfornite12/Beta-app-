package com.example.ui.screens.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
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

private val orsRouteProfiles = listOf(
    OrsProfiles.DRIVING_CAR to "Automóvil",
    OrsProfiles.FOOT_WALKING to "Caminar",
    OrsProfiles.CYCLING_REGULAR to "Bicicleta",
    OrsProfiles.CYCLING_ELECTRIC to "Bicicleta eléctrica",
    OrsProfiles.FOOT_HIKING to "Senderismo",
    OrsProfiles.WHEELCHAIR to "Silla de ruedas"
)

private const val OSM_STANDARD_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {
      "id": "osm-tiles",
      "type": "raster",
      "source": "osm",
      "minzoom": 0,
      "maxzoom": 19
    }
  ]
}
"""

private const val MAPLIBRE_DEMO_STYLE = "https://demotiles.maplibre.org/style.json"

enum class MapLayer(val label: String) {
    OSM_STANDARD("OpenStreetMap"),
    MAPLIBRE_VECTOR("MapLibre Vector")
}

enum class TapMode {
    SET_ORIGIN,
    SET_DESTINATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.OSM_STANDARD) }
    var layerMenuOpen by remember { mutableStateOf(false) }

    var originMarker by remember { mutableStateOf<Marker?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var activePolyline by remember { mutableStateOf<Polyline?>(null) }

    var originText by remember { mutableStateOf("") }
    var originPoint by remember { mutableStateOf<LatLng?>(null) }

    var destinationText by remember { mutableStateOf("") }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }

    var tapMode by remember { mutableStateOf(TapMode.SET_DESTINATION) }

    var selectedProfile by remember { mutableStateOf(orsRouteProfiles.first()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var routeSummary by remember { mutableStateOf<OrsRouteSummary?>(null) }
    var instructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchDeviceGpsLocation(onFound: (LatLng) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            try {
                if (lm.isProviderEnabled(p)) {
                    val loc = lm.getLastKnownLocation(p)
                    if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                        best = loc
                    }
                }
            } catch (_: Exception) {}
        }

        if (best != null) {
            onFound(LatLng(best.latitude, best.longitude))
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    onFound(LatLng(loc.latitude, loc.longitude))
                }
                @Deprecated("Deprecated")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try {
                val p = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                lm.requestSingleUpdate(p, listener, null)
            } catch (_: Exception) {}
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            fetchDeviceGpsLocation { gpsLoc ->
                originPoint = gpsLoc
                originText = "Mi ubicación actual (GPS)"
                mapLibreMapInstance?.let { map ->
                    originMarker?.let { map.removeMarker(it) }
                    originMarker = map.addMarker(
                        MarkerOptions()
                            .position(gpsLoc)
                            .title("Origen (GPS)")
                            .snippet(originText)
                    )
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(gpsLoc, 14.0))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            fetchDeviceGpsLocation { gpsLoc ->
                originPoint = gpsLoc
                originText = "Mi ubicación actual (GPS)"
                mapLibreMapInstance?.let { map ->
                    originMarker?.let { map.removeMarker(it) }
                    originMarker = map.addMarker(
                        MarkerOptions()
                            .position(gpsLoc)
                            .title("Origen (GPS)")
                            .snippet(originText)
                    )
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(gpsLoc, 13.5))
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val orsKeyAvailable = BuildConfig.OPENROUTESERVICE_API_KEY.isNotBlank() &&
        BuildConfig.OPENROUTESERVICE_API_KEY != "MY_OPENROUTESERVICE_API_KEY"

    fun updateMapMarkersAndRoute() {
        val map = mapLibreMapInstance ?: return

        originPoint?.let { start ->
            originMarker?.let { map.removeMarker(it) }
            originMarker = map.addMarker(
                MarkerOptions()
                    .position(start)
                    .title("Origen")
                    .snippet(originText.ifBlank { "Punto de partida" })
            )
        }

        destinationPoint?.let { dest ->
            destinationMarker?.let { map.removeMarker(it) }
            destinationMarker = map.addMarker(
                MarkerOptions()
                    .position(dest)
                    .title("Destino")
                    .snippet(destinationText.ifBlank { "Punto de llegada" })
            )
        }

        activePolyline?.let { map.removePolyline(it) }
        if (routePoints.isNotEmpty()) {
            activePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(android.graphics.Color.parseColor("#10B981"))
                    .width(6f)
            )
            try {
                val boundsBuilder = LatLngBounds.Builder()
                originPoint?.let { boundsBuilder.include(it) }
                destinationPoint?.let { boundsBuilder.include(it) }
                routePoints.forEach { boundsBuilder.include(it) }
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120),
                    1000
                )
            } catch (_: Exception) {}
        }
    }

    fun applyStyleToMap(map: MapLibreMap, layer: MapLayer) {
        val builder = when (layer) {
            MapLayer.OSM_STANDARD -> Style.Builder().fromJson(OSM_STANDARD_STYLE_JSON)
            MapLayer.MAPLIBRE_VECTOR -> Style.Builder().fromUri(MAPLIBRE_DEMO_STYLE)
        }
        map.setStyle(builder) {
            updateMapMarkersAndRoute()
        }
    }

    fun calculateRoute() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            routeError = null
            routeSummary = null
            instructions = emptyList()
            routePoints = emptyList()

            try {
                if (!orsKeyAvailable) {
                    routeError = "Configura OPENROUTESERVICE_API_KEY en los Secrets de compilación."
                    return@launch
                }

                // Resolver punto de origen si solo hay texto
                var start = originPoint
                if (start == null) {
                    if (originText.isBlank()) {
                        routeError = "Por favor define un punto de origen (escribe un lugar, usa tu GPS o toca en el mapa)."
                        return@launch
                    }
                    val startGeo = withContext(Dispatchers.IO) {
                        OpenRouteServiceClient.api.geocode(BuildConfig.OPENROUTESERVICE_API_KEY, originText.trim(), 1)
                            .features.firstOrNull() ?: error("No encontré la ubicación de origen '$originText'.")
                    }
                    val c = startGeo.geometry.coordinates
                    if (c.size < 2) error("Coordenadas de origen inválidas.")
                    start = LatLng(c[1], c[0])
                    originPoint = start
                }

                // Resolver punto de destino si solo hay texto
                var dest = destinationPoint
                if (dest == null) {
                    if (destinationText.isBlank()) {
                        routeError = "Por favor define un destino."
                        return@launch
                    }
                    val destGeo = withContext(Dispatchers.IO) {
                        OpenRouteServiceClient.api.geocode(BuildConfig.OPENROUTESERVICE_API_KEY, destinationText.trim(), 1)
                            .features.firstOrNull() ?: error("No encontré el destino '$destinationText'.")
                    }
                    val c = destGeo.geometry.coordinates
                    if (c.size < 2) error("Coordenadas de destino inválidas.")
                    dest = LatLng(c[1], c[0])
                    destinationPoint = dest
                }

                val result = withContext(Dispatchers.IO) {
                    OpenRouteServiceClient.api.route(
                        profile = selectedProfile.first,
                        apiKey = BuildConfig.OPENROUTESERVICE_API_KEY,
                        start = "${start.longitude},${start.latitude}",
                        end = "${dest.longitude},${dest.latitude}",
                        instructions = true
                    )
                }

                val feature = result.features.firstOrNull() ?: error("No se pudo calcular la ruta.")
                routeSummary = feature.properties.summary
                instructions = feature.properties.segments.flatMap { segment ->
                    segment.steps.map { step -> step.instruction }
                }

                val coords = feature.geometry.coordinates
                if (coords.isNotEmpty()) {
                    routePoints = coords.mapNotNull { pt ->
                        if (pt.size >= 2) LatLng(pt[1], pt[0]) else null
                    }
                }
                updateMapMarkersAndRoute()
            } catch (error: Exception) {
                routeError = error.message ?: "No se pudo calcular la ruta."
            } finally {
                isLoading = false
            }
        }
    }

    fun onMapTapped(point: LatLng) {
        if (isLoading) return
        scope.launch {
            isLoading = true
            routeError = null
            try {
                val label = if (orsKeyAvailable) {
                    withContext(Dispatchers.IO) {
                        try {
                            val rev = OpenRouteServiceClient.api.reverseGeocode(
                                apiKey = BuildConfig.OPENROUTESERVICE_API_KEY,
                                longitude = point.longitude,
                                latitude = point.latitude,
                                size = 1
                            )
                            rev.features.firstOrNull()?.properties?.label
                        } catch (_: Exception) { null }
                    }
                } else null

                val resolvedName = label ?: String.format(Locale.getDefault(), "%.5f, %.5f", point.latitude, point.longitude)

                if (tapMode == TapMode.SET_ORIGIN) {
                    originPoint = point
                    originText = resolvedName
                    tapMode = TapMode.SET_DESTINATION // auto avanzar al destino
                    updateMapMarkersAndRoute()
                    if (destinationPoint != null) {
                        calculateRoute()
                    }
                } else {
                    destinationPoint = point
                    destinationText = resolvedName
                    updateMapMarkersAndRoute()
                    if (originPoint != null) {
                        calculateRoute()
                    }
                }
            } catch (error: Exception) {
                routeError = error.message ?: "Error al ubicar el punto."
            } finally {
                isLoading = false
            }
        }
    }

    fun useCurrentGpsAsOrigin() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        isLoading = true
        fetchDeviceGpsLocation { loc ->
            isLoading = false
            originPoint = loc
            originText = "Mi ubicación (GPS)"
            mapLibreMapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 14.5), 800)
            updateMapMarkersAndRoute()
            if (destinationPoint != null) {
                calculateRoute()
            }
        }
    }

    fun swapOriginAndDestination() {
        val tempText = originText
        val tempPoint = originPoint
        originText = destinationText
        originPoint = destinationPoint
        destinationText = tempText
        destinationPoint = tempPoint
        updateMapMarkersAndRoute()
        if (originPoint != null && destinationPoint != null) {
            calculateRoute()
        }
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                mapLibreMapInstance = map
                // Vista inicial centrada en la ubicación disponible o general
                val initTarget = originPoint ?: LatLng(9.9951797, -84.1403642)
                map.cameraPosition = CameraPosition.Builder()
                    .target(initTarget)
                    .zoom(12.5)
                    .build()

                applyStyleToMap(map, selectedLayer)

                map.addOnMapClickListener { tapped ->
                    onMapTapped(tapped)
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
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("MapLibre + OpenStreetMap", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Rutas libres sin ubicación fija", fontSize = 11.sp, color = Color.LightGray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { layerMenuOpen = true }) {
                            Icon(Icons.Default.Layers, contentDescription = "Capas del mapa", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = layerMenuOpen,
                            onDismissRequest = { layerMenuOpen = false }
                        ) {
                            MapLayer.values().forEach { layer ->
                                DropdownMenuItem(
                                    text = { Text(layer.label) },
                                    onClick = {
                                        selectedLayer = layer
                                        layerMenuOpen = false
                                        mapLibreMapInstance?.let { applyStyleToMap(it, layer) }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Contenedor del mapa
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )

                // Selector de modo de toque en el mapa (Origen vs Destino)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.92f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tocar mapa fija:", fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(start = 8.dp, end = 6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (tapMode == TapMode.SET_ORIGIN) Color(0xFF10B981) else Color.Transparent)
                                .clickable { tapMode = TapMode.SET_ORIGIN }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("📍 Origen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (tapMode == TapMode.SET_ORIGIN) Color.White else Color.Gray)
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (tapMode == TapMode.SET_DESTINATION) Color(0xFF3B82F6) else Color.Transparent)
                                .clickable { tapMode = TapMode.SET_DESTINATION }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("🏁 Destino", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (tapMode == TapMode.SET_DESTINATION) Color.White else Color.Gray)
                        }
                    }
                }

                // Botón GPS flotante (usar ubicación del dispositivo)
                FloatingActionButton(
                    onClick = ::useCurrentGpsAsOrigin,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(46.dp),
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF10B981)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Usar mi ubicación GPS actual", modifier = Modifier.size(22.dp))
                }
            }

            // Panel inferior de configuración de ruta
            Surface(color = Color(0xFF1E293B), shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    // Campo de Origen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = originText,
                            onValueChange = {
                                originText = it
                                originPoint = null // se resolverá por geocoding
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("📍 Origen") },
                            placeholder = { Text("Lugar de partida o usa el GPS") },
                            trailingIcon = {
                                IconButton(onClick = ::useCurrentGpsAsOrigin) {
                                    Icon(Icons.Default.GpsFixed, contentDescription = "Detectar GPS", tint = Color(0xFF10B981))
                                }
                            }
                        )
                        IconButton(
                            onClick = ::swapOriginAndDestination,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Intercambiar origen y destino", tint = Color.LightGray)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Campo de Destino
                    OutlinedTextField(
                        value = destinationText,
                        onValueChange = {
                            destinationText = it
                            destinationPoint = null // se resolverá por geocoding
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("🏁 Destino") },
                        placeholder = { Text("Ej. Aeropuerto, Parque o toca en el mapa") }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Perfiles de transporte y botón Calcular
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Button(onClick = { profileMenuOpen = true }) {
                                Text(selectedProfile.second)
                            }
                            DropdownMenu(
                                expanded = profileMenuOpen,
                                onDismissRequest = { profileMenuOpen = false }
                            ) {
                                orsRouteProfiles.forEach { profile ->
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
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text("Calcular ruta", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    routeError?.let {
                        Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }

                    routeSummary?.let { summary ->
                        val distance = String.format(Locale.getDefault(), "%.1f km", summary.distance / 1000.0)
                        val duration = "${(summary.duration / 60.0).roundToInt()} min"
                        Text(
                            "$distance • $duration • ${selectedProfile.second}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (instructions.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.height(100.dp)
                        ) {
                            items(instructions) { instruction ->
                                Text("• $instruction", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
