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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startPoint = LatLng(9.9951797, -84.1403642)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startPoint, 12f)
    }

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

    var destination by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(orsRouteProfiles.first()) }
    var profileMenuOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var routeSummary by remember { mutableStateOf<OrsRouteSummary?>(null) }
    var instructions by remember { mutableStateOf<List<String>>(emptyList()) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var destinationPoint by remember { mutableStateOf<LatLng?>(null) }

    val mapsKeyAvailable = BuildConfig.MAPS_API_KEY.isNotBlank() &&
        BuildConfig.MAPS_API_KEY != "MY_MAPS_API_KEY"
    val orsKeyAvailable = BuildConfig.OPENROUTESERVICE_API_KEY.isNotBlank() &&
        BuildConfig.OPENROUTESERVICE_API_KEY != "MY_OPENROUTESERVICE_API_KEY"

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
                
                // Parse coordinates for polyline: ORS returns [longitude, latitude]
                val coords = feature.geometry.coordinates
                if (coords.isNotEmpty()) {
                    routePoints = coords.mapNotNull { pt ->
                        if (pt.size >= 2) LatLng(pt[1], pt[0]) else null
                    }
                    val lastPt = coords.last()
                    if (lastPt.size >= 2) {
                        destinationPoint = LatLng(lastPt[1], lastPt[0])
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(lastPt[1], lastPt[0]), 13f)
                    }
                }
            } catch (error: Exception) {
                routeError = error.message ?: "No se pudo calcular la ruta."
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(Modifier.size(8.dp))
                        Text("Mapas y rutas", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Volver")
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mapsKeyAvailable) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(
                            mapType = MapType.NORMAL,
                            isMyLocationEnabled = hasLocationPermission
                        ),
                        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
                    ) {
                        Marker(
                            state = MarkerState(startPoint),
                            title = "Inicio",
                            snippet = "San Francisco, Heredia"
                        )
                        destinationPoint?.let { dest ->
                            Marker(
                                state = MarkerState(dest),
                                title = "Destino",
                                snippet = destination
                            )
                        }
                        if (routePoints.isNotEmpty()) {
                            Polyline(
                                points = routePoints,
                                color = Color(0xFF10B981),
                                width = 10f
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Text("El mapa visual requiere configuración de mapas.", color = Color.White)
                        Text("Las rutas gratuitas usan OpenRouteService.", color = Color.Gray)
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
                        placeholder = { Text("Ej. Parque Central de Heredia") }
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
                        "Inicio predeterminado: San Francisco, Heredia",
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
