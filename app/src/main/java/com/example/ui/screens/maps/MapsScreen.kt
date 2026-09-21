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
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.api.OpenRouteServiceClient
import com.example.data.api.OrsProfiles
import com.example.data.api.OrsRouteSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultLocation = Position(latitude = 9.9951797, longitude = -84.1403642)
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        initialCameraPosition = CameraPosition(target = defaultLocation, zoom = 12.0)
    )
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    var currentLocation by remember { mutableStateOf(defaultLocation) }
    var locationLabel by remember { mutableStateOf("Buscando tu ubicación...") }
    var hasRealLocation by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(profiles.first()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<OrsRouteSummary?>(null) }
    var instructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var navigationMode by remember { mutableStateOf(false) }
    var navigationStep by remember { mutableStateOf(0) }
    val tts = remember { TextToSpeech(context, null) }
    val orsReady = BuildConfig.OPENROUTESERVICE_API_KEY.isNotBlank() && BuildConfig.OPENROUTESERVICE_API_KEY != "MY_OPENROUTESERVICE_API_KEY"

    fun updateLocation(location: Location) {
        val point = Position(latitude = location.latitude, longitude = location.longitude)
        currentLocation = point
        hasRealLocation = true
        locationLabel = String.format(Locale.getDefault(), "%.5f, %.5f", location.latitude, location.longitude)
        if (orsReady) scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    OpenRouteServiceClient.api.reverseGeocode(BuildConfig.OPENROUTESERVICE_API_KEY, point.longitude, point.latitude, 1)
                }
                response.features.firstOrNull()?.properties?.label?.let { locationLabel = it }
            } catch (_: Exception) { }
        }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val listener = object : LocationListener { override fun onLocationChanged(location: Location) { updateLocation(location) } }
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 3000L, 5f, listener)
                    locationManager.getLastKnownLocation(provider)?.let { updateLocation(it) }
                }
            }
        } catch (_: SecurityException) { locationLabel = "No se pudo acceder a la ubicación" }
        onDispose { locationManager.removeUpdates(listener) }
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(currentLocation) {
        mapState.animateCamera(CameraUpdate(target = currentLocation, zoom = 15.0))
    }
    LaunchedEffect(navigationMode, navigationStep, instructions) {
        if (navigationMode) instructions.getOrNull(navigationStep)?.let {
            tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "omnistudio-navigation")
        }
    }

    fun calculateRoute() {
        if (destination.isBlank() || loading) return
        scope.launch {
            loading = true; errorText = null; summary = null; instructions = emptyList(); navigationMode = false; navigationStep = 0
            try {
                if (!orsReady) error("Configura OPENROUTESERVICE_API_KEY en Secrets.")
                val result = withContext(Dispatchers.IO) {
                    val place = OpenRouteServiceClient.api.geocode(BuildConfig.OPENROUTESERVICE_API_KEY, destination.trim(), 1).features.firstOrNull() ?: error("No encontré ese destino.")
                    val c = place.geometry.coordinates
                    if (c.size < 2) error("El destino no tiene coordenadas válidas.")
                    OpenRouteServiceClient.api.route(selectedProfile.first, BuildConfig.OPENROUTESERVICE_API_KEY, "${currentLocation.longitude},${currentLocation.latitude}", "${c[0]},${c[1]}", true)
                }
                val feature = result.features.firstOrNull() ?: error("No se pudo calcular la ruta.")
                summary = feature.properties.summary
                instructions = feature.properties.segments.flatMap { it.steps.map { step -> step.instruction } }
            } catch (e: Exception) { errorText = e.message ?: "No se pudo calcular la ruta." } finally { loading = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Map, null, tint = Color(0xFF10B981)); Spacer(Modifier.size(8.dp)); Text("Mapas y rutas", fontWeight = FontWeight.Bold) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }, containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(color = if (hasRealLocation) Color(0xFF14532D) else Color(0xFF334155), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, null, tint = Color.White); Spacer(Modifier.size(8.dp)); Column {
                        Text("Tu ubicación actual", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(locationLabel, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { MaplibreMap(Modifier.fillMaxSize(), state = mapState) }
            if (navigationMode && instructions.isNotEmpty()) {
                Surface(color = Color(0xFF064E3B), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Navegación activa", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(instructions.getOrNull(navigationStep) ?: "Ruta iniciada", color = Color.White, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { if (navigationStep < instructions.lastIndex) navigationStep++ }) { Text("Siguiente indicación") }
                            Spacer(Modifier.width(8.dp)); Button(onClick = { navigationMode = false }) { Text("Finalizar") }
                        }
                    }
                }
            }
            Surface(color = Color(0xFF1E293B), shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    OutlinedTextField(destination, { destination = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Destino") }, placeholder = { Text("Ej. Parque Central de Heredia") })
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Button(onClick = { profileMenuOpen = true }) { Text(selectedProfile.second) }
                            DropdownMenu(profileMenuOpen, { profileMenuOpen = false }) { profiles.forEach { profile -> DropdownMenuItem(text = { Text(profile.second) }, onClick = { selectedProfile = profile; profileMenuOpen = false }) } }
                        }
                        Spacer(Modifier.width(8.dp)); Button(onClick = ::calculateRoute, enabled = destination.isNotBlank() && !loading) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Calcular ruta") }
                    }
                    Text("Inicio: $locationLabel", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    errorText?.let { Text(it, color = Color(0xFFFCA5A5), modifier = Modifier.padding(top = 6.dp)) }
                    summary?.let { s ->
                        val distance = String.format(Locale.getDefault(), "%.1f km", s.distance / 1000.0); val duration = "${(s.duration / 60.0).roundToInt()} min"
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("$distance • $duration • ${selectedProfile.second}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Button(onClick = { navigationMode = true; navigationStep = 0 }) { Text("Navegar") }
                        }
                    }
                    if (instructions.isNotEmpty()) LazyColumn(contentPadding = PaddingValues(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(110.dp)) { items(instructions) { Text("• $it", color = Color.White, style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}
