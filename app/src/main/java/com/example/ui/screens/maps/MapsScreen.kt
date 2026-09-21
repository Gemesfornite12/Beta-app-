package com.example.ui.screens.maps

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val startPoint = remember { LatLng(9.9951797, -84.1403642) }

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.OSM_STANDARD) }
    var layerMenuOpen by remember { mutableStateOf(false) }

    var originMarker by remember { mutableStateOf<Marker?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var activePolyline by remember { mutableStateOf<Polyline?>(null) }

    var destination by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(orsRouteProfiles.first()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var routeSummary by remember { mutableStateOf<OrsRouteSummary?>(null) }
    var instructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val orsKeyAvailable = BuildConfig.OPENROUTESERVICE_API_KEY.isNotBlank() &&
        BuildConfig.OPENROUTESERVICE_API_KEY != "MY_OPENROUTESERVICE_API_KEY"

    fun applyStyleToMap(map: MapLibreMap, layer: MapLayer) {
        val builder = when (layer) {
            MapLayer.OSM_STANDARD -> Style.Builder().fromJson(OSM_STANDARD_STYLE_JSON)
            MapLayer.MAPLIBRE_VECTOR -> Style.Builder().fromUri(MAPLIBRE_DEMO_STYLE)
        }
        map.setStyle(builder) {
            originMarker?.let { map.removeMarker(it) }
            originMarker = map.addMarker(
                MarkerOptions()
                    .position(startPoint)
                    .title("Inicio")
                    .snippet("San Francisco, Heredia")
            )
            destinationPoint?.let { dest ->
                destinationMarker?.let { map.removeMarker(it) }
                destinationMarker = map.addMarker(
                    MarkerOptions()
                        .position(dest)
                        .title("Destino")
                        .snippet(destination)
                )
            }
            if (routePoints.isNotEmpty()) {
                activePolyline?.let { map.removePolyline(it) }
                activePolyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(routePoints)
                        .color(android.graphics.Color.parseColor("#10B981"))
                        .width(6f)
                )
            }
        }
    }

    fun updateMapRouteAndDestination(dest: LatLng, points: List<LatLng>) {
        mapLibreMapInstance?.let { map ->
            destinationMarker?.let { map.removeMarker(it) }
            destinationMarker = map.addMarker(
                MarkerOptions()
                    .position(dest)
                    .title("Destino")
                    .snippet(destination)
            )

            activePolyline?.let { map.removePolyline(it) }
            if (points.isNotEmpty()) {
                activePolyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(android.graphics.Color.parseColor("#10B981"))
                        .width(6f)
                )
                try {
                    val boundsBuilder = LatLngBounds.Builder()
                    boundsBuilder.include(startPoint)
                    points.forEach { boundsBuilder.include(it) }
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120),
                        1000
                    )
                } catch (_: Exception) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 13.0))
                }
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 14.0))
            }
        }
    }

    fun calculateRoute() {
        if (destination.isBlank() || isLoading) return
        scope.launch {
            isLoading = true
            routeError = null
            routeSummary = null
            instructions = emptyList()
            routePoints = emptyList()
            destinationPoint = null
            try {
                if (!orsKeyAvailable) {
                    routeError = "Configura OPENROUTESERVICE_API_KEY en los Secrets de compilación."
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    val place = OpenRouteServiceClient.api
                        .geocode(BuildConfig.OPENROUTESERVICE_API_KEY, destination.trim(), 1)
                        .features
                        .firstOrNull()
                        ?: error("No encontré ese destino.")
                    val coordinates = place.geometry.coordinates
                    if (coordinates.size < 2) error("El destino no tiene coordenadas válidas.")
                    val end = "${coordinates[0]},${coordinates[1]}"
                    OpenRouteServiceClient.api.route(
                        profile = selectedProfile.first,
                        apiKey = BuildConfig.OPENROUTESERVICE_API_KEY,
                        start = "${startPoint.longitude},${startPoint.latitude}",
                        end = end,
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
                    val pts = coords.mapNotNull { pt ->
                        if (pt.size >= 2) LatLng(pt[1], pt[0]) else null
                    }
                    routePoints = pts
                    val lastPt = coords.last()
                    if (lastPt.size >= 2) {
                        val endLatLng = LatLng(lastPt[1], lastPt[0])
                        destinationPoint = endLatLng
                        updateMapRouteAndDestination(endLatLng, pts)
                    }
                }
            } catch (error: Exception) {
                routeError = error.message ?: "No se pudo calcular la ruta."
            } finally {
                isLoading = false
            }
        }
    }

    fun onMapTapped(point: LatLng) {
        if (isLoading) return
        destinationPoint = point
        scope.launch {
            isLoading = true
            routeError = null
            routeSummary = null
            instructions = emptyList()
            routePoints = emptyList()
            try {
                if (!orsKeyAvailable) {
                    destination = String.format(Locale.getDefault(), "%.5f, %.5f", point.latitude, point.longitude)
                    routeError = "Configura OPENROUTESERVICE_API_KEY en los Secrets de compilación."
                    updateMapRouteAndDestination(point, emptyList())
                    return@launch
                }
                val label = withContext(Dispatchers.IO) {
                    try {
                        val rev = OpenRouteServiceClient.api.reverseGeocode(
                            apiKey = BuildConfig.OPENROUTESERVICE_API_KEY,
                            longitude = point.longitude,
                            latitude = point.latitude,
                            size = 1
                        )
                        rev.features.firstOrNull()?.properties?.label
                    } catch (_: Exception) {
                        null
                    }
                }
                destination = label ?: String.format(Locale.getDefault(), "%.5f, %.5f", point.latitude, point.longitude)

                val result = withContext(Dispatchers.IO) {
                    OpenRouteServiceClient.api.route(
                        profile = selectedProfile.first,
                        apiKey = BuildConfig.OPENROUTESERVICE_API_KEY,
                        start = "${startPoint.longitude},${startPoint.latitude}",
                        end = "${point.longitude},${point.latitude}",
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
                    val pts = coords.mapNotNull { pt ->
                        if (pt.size >= 2) LatLng(pt[1], pt[0]) else null
                    }
                    routePoints = pts
                    updateMapRouteAndDestination(point, pts)
                } else {
                    updateMapRouteAndDestination(point, emptyList())
                }
            } catch (error: Exception) {
                routeError = error.message ?: "No se pudo calcular la ruta al punto seleccionado."
                updateMapRouteAndDestination(point, emptyList())
            } finally {
                isLoading = false
            }
        }
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).apply {
            getMapAsync { map ->
                mapLibreMapInstance = map
                map.cameraPosition = CameraPosition.Builder()
                    .target(startPoint)
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
                            Text("MapLibre + OpenStreetMap", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Mapas y rutas libres de Google", fontSize = 11.sp, color = Color.LightGray)
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )

                // Quick recenter button
                FloatingActionButton(
                    onClick = {
                        mapLibreMapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(startPoint, 13.0),
                            800
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(44.dp),
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF10B981)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Centrar en inicio", modifier = Modifier.size(20.dp))
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
                        placeholder = { Text("Ej. Parque Central o toca en el mapa") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = ::calculateRoute,
                            enabled = destination.isNotBlank() && !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Calcular ruta")
                            }
                        }
                    }
                    Text(
                        "Inicio: San Francisco, Heredia • Toca en el mapa OpenStreetMap para fijar destino",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    routeError?.let {
                        Text(it, color = Color(0xFFFCA5A5), modifier = Modifier.padding(top = 6.dp))
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
                            modifier = Modifier.height(110.dp)
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
