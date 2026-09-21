package com.example.ui.screens.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.example.data.service.NavigationForegroundService
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.maps.MapTileCacheManager
import com.example.data.maps.PreCacheProgress
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
import java.util.Calendar
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
 * Formatea la hora en formato estándar de 12 horas con indicador AM / PM (ej: 7:27 PM).
 * Garantiza que nunca se muestre en formato militar/24 horas.
 */
private fun formatTime12Hour(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (hour24 >= 12) "PM" else "AM"
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
}

/**
 * Modelo para los idiomas del motor de voz TTS.
 */
data class TtsLanguage(
    val code: String,
    val name: String,
    val flag: String,
    val locale: Locale,
    val recalculatingText: String
)

val availableTtsLanguages = listOf(
    TtsLanguage("es", "Español", "🇪🇸", Locale("es", "ES"), "Recalculando ruta..."),
    TtsLanguage("en", "English", "🇺🇸", Locale.US, "Recalculating route..."),
    TtsLanguage("fr", "Français", "🇫🇷", Locale.FRANCE, "Recalcul de l'itinéraire..."),
    TtsLanguage("pt", "Português", "🇵🇹", Locale("pt", "PT"), "Recalculando rota..."),
    TtsLanguage("it", "Italiano", "🇮🇹", Locale.ITALY, "Ricalcolo del percorso..."),
    TtsLanguage("de", "Deutsch", "🇩🇪", Locale.GERMANY, "Route wird neu berechnet...")
)

/**
 * Modos de ahorro de batería para optimizar el consumo del chip GPS y el motor TTS.
 */
enum class BatterySaverMode(val title: String, val shortLabel: String, val description: String) {
    AUTO("Automático (≤20%)", "Auto", "Se activa solo si la carga baja del 20%"),
    ON("Siempre activado", "Activo", "Fuerza ahorro: GPS cada 5s y voz espaciada"),
    OFF("Desactivado", "Desactivado", "Frecuencia máxima (GPS cada 1s y voz continua)")
}

/**
 * Traduce y adapta instrucciones de navegación según el idioma configurado
 * para el motor de voz TTS y la interfaz de usuario.
 */
private fun translateInstruction(instruction: String, langCode: String): String {
    return when (langCode) {
        "es" -> translateInstructionToSpanish(instruction)
        "fr" -> translateInstructionToFrench(instruction)
        "pt" -> translateInstructionToPortuguese(instruction)
        "it" -> translateInstructionToItalian(instruction)
        "de" -> translateInstructionToGerman(instruction)
        else -> instruction
    }
}

private fun translateInstructionToSpanish(instruction: String): String {
    var text = instruction
    val replacements = listOf(
        Regex("(?i)\\bHead\\s+northeast\\b") to "Dirígete al noreste",
        Regex("(?i)\\bHead\\s+northwest\\b") to "Dirígete al noroeste",
        Regex("(?i)\\bHead\\s+southeast\\b") to "Dirígete al sureste",
        Regex("(?i)\\bHead\\s+southwest\\b") to "Dirígete al suroeste",
        Regex("(?i)\\bHead\\s+north\\b") to "Dirígete al norte",
        Regex("(?i)\\bHead\\s+south\\b") to "Dirígete al sur",
        Regex("(?i)\\bHead\\s+east\\b") to "Dirígete al este",
        Regex("(?i)\\bHead\\s+west\\b") to "Dirígete al oeste",
        Regex("(?i)\\bTurn\\s+sharp\\s+left\\b") to "Gira pronunciadamente a la izquierda",
        Regex("(?i)\\bTurn\\s+sharp\\s+right\\b") to "Gira pronunciadamente a la derecha",
        Regex("(?i)\\bTurn\\s+slight\\s+left\\b") to "Gira levemente a la izquierda",
        Regex("(?i)\\bTurn\\s+slight\\s+right\\b") to "Gira levemente a la derecha",
        Regex("(?i)\\bTurn\\s+left\\b") to "Gira a la izquierda",
        Regex("(?i)\\bTurn\\s+right\\b") to "Gira a la derecha",
        Regex("(?i)\\bKeep\\s+left\\b") to "Mantente a la izquierda",
        Regex("(?i)\\bKeep\\s+right\\b") to "Mantente a la derecha",
        Regex("(?i)\\bContinue\\s+straight\\b") to "Continúa recto",
        Regex("(?i)\\bContinue\\b") to "Continúa",
        Regex("(?i)\\bonto\\b") to "hacia",
        Regex("(?i)\\bon\\b") to "por",
        Regex("(?i)\\btoward\\b") to "hacia",
        Regex("(?i)\\bArrive\\s+at\\s+your\\s+destination\\b") to "Llegada a tu destino",
        Regex("(?i)\\bYou\\s+have\\s+arrived\\b") to "Has llegado a tu destino",
        Regex("(?i)\\bRoundabout\\b") to "Rotonda"
    )
    for ((regex, replacement) in replacements) {
        text = text.replace(regex, replacement)
    }
    return text
}

private fun translateInstructionToFrench(instruction: String): String {
    var text = instruction
    val replacements = listOf(
        Regex("(?i)\\bHead\\s+northeast\\b") to "Dirigez-vous vers le nord-est",
        Regex("(?i)\\bHead\\s+northwest\\b") to "Dirigez-vous vers le nord-ouest",
        Regex("(?i)\\bHead\\s+southeast\\b") to "Dirigez-vous vers le sud-est",
        Regex("(?i)\\bHead\\s+southwest\\b") to "Dirigez-vous vers le sud-ouest",
        Regex("(?i)\\bHead\\s+north\\b") to "Dirigez-vous vers le nord",
        Regex("(?i)\\bHead\\s+south\\b") to "Dirigez-vous vers le sud",
        Regex("(?i)\\bHead\\s+east\\b") to "Dirigez-vous vers l'est",
        Regex("(?i)\\bHead\\s+west\\b") to "Dirigez-vous vers l'ouest",
        Regex("(?i)\\bTurn\\s+sharp\\s+left\\b") to "Tournez fortement à gauche",
        Regex("(?i)\\bTurn\\s+sharp\\s+right\\b") to "Tournez fortement à droite",
        Regex("(?i)\\bTurn\\s+slight\\s+left\\b") to "Tournez légèrement à gauche",
        Regex("(?i)\\bTurn\\s+slight\\s+right\\b") to "Tournez légèrement à droite",
        Regex("(?i)\\bTurn\\s+left\\b") to "Tournez à gauche",
        Regex("(?i)\\bTurn\\s+right\\b") to "Tournez à droite",
        Regex("(?i)\\bKeep\\s+left\\b") to "Serrez à gauche",
        Regex("(?i)\\bKeep\\s+right\\b") to "Serrez à droite",
        Regex("(?i)\\bContinue\\s+straight\\b") to "Continuez tout droit",
        Regex("(?i)\\bContinue\\b") to "Continuez",
        Regex("(?i)\\bonto\\b") to "sur",
        Regex("(?i)\\bon\\b") to "sur",
        Regex("(?i)\\btoward\\b") to "vers",
        Regex("(?i)\\bArrive\\s+at\\s+your\\s+destination\\b") to "Arrivée à destination",
        Regex("(?i)\\bYou\\s+have\\s+arrived\\b") to "Vous êtes arrivé à destination",
        Regex("(?i)\\bRoundabout\\b") to "Rond-point"
    )
    for ((regex, replacement) in replacements) {
        text = text.replace(regex, replacement)
    }
    return text
}

private fun translateInstructionToPortuguese(instruction: String): String {
    var text = instruction
    val replacements = listOf(
        Regex("(?i)\\bHead\\s+northeast\\b") to "Siga para o nordeste",
        Regex("(?i)\\bHead\\s+northwest\\b") to "Siga para o noroeste",
        Regex("(?i)\\bHead\\s+southeast\\b") to "Siga para o sudeste",
        Regex("(?i)\\bHead\\s+southwest\\b") to "Siga para o sudoeste",
        Regex("(?i)\\bHead\\s+north\\b") to "Siga para o norte",
        Regex("(?i)\\bHead\\s+south\\b") to "Siga para o sul",
        Regex("(?i)\\bHead\\s+east\\b") to "Siga para o leste",
        Regex("(?i)\\bHead\\s+west\\b") to "Siga para o oeste",
        Regex("(?i)\\bTurn\\s+sharp\\s+left\\b") to "Vire acentuadamente à esquerda",
        Regex("(?i)\\bTurn\\s+sharp\\s+right\\b") to "Vire acentuadamente à direita",
        Regex("(?i)\\bTurn\\s+slight\\s+left\\b") to "Vire suavemente à esquerda",
        Regex("(?i)\\bTurn\\s+slight\\s+right\\b") to "Vire suavemente à direita",
        Regex("(?i)\\bTurn\\s+left\\b") to "Vire à esquerda",
        Regex("(?i)\\bTurn\\s+right\\b") to "Vire à direita",
        Regex("(?i)\\bKeep\\s+left\\b") to "Mantenha-se à esquerda",
        Regex("(?i)\\bKeep\\s+right\\b") to "Mantenha-se à direita",
        Regex("(?i)\\bContinue\\s+straight\\b") to "Continue em frente",
        Regex("(?i)\\bContinue\\b") to "Continue",
        Regex("(?i)\\bonto\\b") to "em direção a",
        Regex("(?i)\\bon\\b") to "por",
        Regex("(?i)\\btoward\\b") to "em direção a",
        Regex("(?i)\\bArrive\\s+at\\s+your\\s+destination\\b") to "Chegada ao destino",
        Regex("(?i)\\bYou\\s+have\\s+arrived\\b") to "Você chegou ao destino",
        Regex("(?i)\\bRoundabout\\b") to "Rotatória"
    )
    for ((regex, replacement) in replacements) {
        text = text.replace(regex, replacement)
    }
    return text
}

private fun translateInstructionToItalian(instruction: String): String {
    var text = instruction
    val replacements = listOf(
        Regex("(?i)\\bHead\\s+northeast\\b") to "Procedi verso nord-est",
        Regex("(?i)\\bHead\\s+northwest\\b") to "Procedi verso nord-ovest",
        Regex("(?i)\\bHead\\s+southeast\\b") to "Procedi verso sud-est",
        Regex("(?i)\\bHead\\s+southwest\\b") to "Procedi verso sud-ovest",
        Regex("(?i)\\bHead\\s+north\\b") to "Procedi verso nord",
        Regex("(?i)\\bHead\\s+south\\b") to "Procedi verso sud",
        Regex("(?i)\\bHead\\s+east\\b") to "Procedi verso est",
        Regex("(?i)\\bHead\\s+west\\b") to "Procedi verso ovest",
        Regex("(?i)\\bTurn\\s+sharp\\s+left\\b") to "Svolta a gomito a sinistra",
        Regex("(?i)\\bTurn\\s+sharp\\s+right\\b") to "Svolta a gomito a destra",
        Regex("(?i)\\bTurn\\s+slight\\s+left\\b") to "Tieni leggermente a sinistra",
        Regex("(?i)\\bTurn\\s+slight\\s+right\\b") to "Tieni leggermente a destra",
        Regex("(?i)\\bTurn\\s+left\\b") to "Svolta a sinistra",
        Regex("(?i)\\bTurn\\s+right\\b") to "Svolta a destra",
        Regex("(?i)\\bKeep\\s+left\\b") to "Mantieniti a sinistra",
        Regex("(?i)\\bKeep\\s+right\\b") to "Mantieniti a destra",
        Regex("(?i)\\bContinue\\s+straight\\b") to "Continua dritto",
        Regex("(?i)\\bContinue\\b") to "Continua",
        Regex("(?i)\\bonto\\b") to "verso",
        Regex("(?i)\\bon\\b") to "su",
        Regex("(?i)\\btoward\\b") to "verso",
        Regex("(?i)\\bArrive\\s+at\\s+your\\s+destination\\b") to "Arrivo a destinazione",
        Regex("(?i)\\bYou\\s+have\\s+arrived\\b") to "Sei arrivato a destinazione",
        Regex("(?i)\\bRoundabout\\b") to "Rotatoria"
    )
    for ((regex, replacement) in replacements) {
        text = text.replace(regex, replacement)
    }
    return text
}

private fun translateInstructionToGerman(instruction: String): String {
    var text = instruction
    val replacements = listOf(
        Regex("(?i)\\bHead\\s+northeast\\b") to "Fahren Sie nach Nordosten",
        Regex("(?i)\\bHead\\s+northwest\\b") to "Fahren Sie nach Nordwesten",
        Regex("(?i)\\bHead\\s+southeast\\b") to "Fahren Sie nach Südosten",
        Regex("(?i)\\bHead\\s+southwest\\b") to "Fahren Sie nach Südwesten",
        Regex("(?i)\\bHead\\s+north\\b") to "Fahren Sie nach Norden",
        Regex("(?i)\\bHead\\s+south\\b") to "Fahren Sie nach Süden",
        Regex("(?i)\\bHead\\s+east\\b") to "Fahren Sie nach Osten",
        Regex("(?i)\\bHead\\s+west\\b") to "Fahren Sie nach Westen",
        Regex("(?i)\\bTurn\\s+sharp\\s+left\\b") to "Scharf links abbiegen",
        Regex("(?i)\\bTurn\\s+sharp\\s+right\\b") to "Scharf rechts abbiegen",
        Regex("(?i)\\bTurn\\s+slight\\s+left\\b") to "Leicht links abbiegen",
        Regex("(?i)\\bTurn\\s+slight\\s+right\\b") to "Leicht rechts abbiegen",
        Regex("(?i)\\bTurn\\s+left\\b") to "Biegen Sie links ab",
        Regex("(?i)\\bTurn\\s+right\\b") to "Biegen Sie rechts ab",
        Regex("(?i)\\bKeep\\s+left\\b") to "Links halten",
        Regex("(?i)\\bKeep\\s+right\\b") to "Rechts halten",
        Regex("(?i)\\bContinue\\s+straight\\b") to "Geradeaus weiterfahren",
        Regex("(?i)\\bContinue\\b") to "Weiterfahren",
        Regex("(?i)\\bonto\\b") to "auf",
        Regex("(?i)\\bon\\b") to "auf",
        Regex("(?i)\\btoward\\b") to "in Richtung",
        Regex("(?i)\\bArrive\\s+at\\s+your\\s+destination\\b") to "Ankunft am Ziel",
        Regex("(?i)\\bYou\\s+have\\s+arrived\\b") to "Sie haben Ihr Ziel erreicht",
        Regex("(?i)\\bRoundabout\\b") to "Kreisverkehr"
    )
    for ((regex, replacement) in replacements) {
        text = text.replace(regex, replacement)
    }
    return text
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
    var voiceEnabled by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf(availableTtsLanguages[0]) } // default: Español 🇪🇸
    var hudLanguageMenuOpen by remember { mutableStateOf(false) }
    var prepLanguageMenuOpen by remember { mutableStateOf(false) }

    // Estados de Batería y Modo Ahorro
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var batterySaverMode by remember { mutableStateOf(BatterySaverMode.AUTO) }
    var batteryMenuOpen by remember { mutableStateOf(false) }
    var prepBatteryMenuOpen by remember { mutableStateOf(false) }

    var lastTtsSpokenTime by remember { mutableLongStateOf(0L) }
    var lastTtsSpokenInstruction by remember { mutableStateOf("") }

    // Estados de Caché y Modo Sin Conexión para MapLibre
    var cacheSizeMb by remember { mutableDoubleStateOf(0.0) }
    var preCacheProgress by remember { mutableStateOf(PreCacheProgress()) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var isNetworkConnected by remember { mutableStateOf(true) }
    var autoCleanDays by remember { mutableIntStateOf(15) }
    var cleanNoticeMessage by remember { mutableStateOf<String?>(null) }
    var isClearingCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        autoCleanDays = MapTileCacheManager.getAutoCleanDays(context)
        cacheSizeMb = MapTileCacheManager.getCacheSizeMb(context)
        isNetworkConnected = MapTileCacheManager.isNetworkAvailable(context)
    }

    // Detección reactiva en tiempo real del nivel de carga y estado de conexión
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryLevel = (level * 100) / scale
                    }
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    // El modo ahorro se activa si:
    // 1. AUTO: la batería baja del 20% y el dispositivo no está enchufado al cargador.
    // 2. ON: activado manualmente por el usuario.
    val isBatterySaverActive by remember {
        derivedStateOf {
            when (batterySaverMode) {
                BatterySaverMode.AUTO -> batteryLevel <= 20 && !isCharging
                BatterySaverMode.ON -> true
                BatterySaverMode.OFF -> false
            }
        }
    }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val result = instance?.setLanguage(selectedLanguage.locale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        instance?.language = Locale.getDefault()
                    }
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    instance?.setAudioAttributes(audioAttributes)
                    instance?.setSpeechRate(0.92f)
                    instance?.setPitch(1.02f)
                } catch (_: Exception) {}
            }
        }
        instance
    }

    // Sincronizar el idioma del motor TTS cuando el usuario elija otro en el selector
    LaunchedEffect(selectedLanguage) {
        try {
            val result = tts?.setLanguage(selectedLanguage.locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
        } catch (_: Exception) {}
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

    fun speakInstruction(text: String, force: Boolean = false) {
        if (!voiceEnabled && !force) return
        if (text.isNotBlank()) {
            val now = System.currentTimeMillis()

            // Modo Ahorro de Batería: Reduce la frecuencia de locución TTS para conservar batería
            if (isBatterySaverActive && !force) {
                val isSameInstruction = text == lastTtsSpokenInstruction
                val elapsedMs = now - lastTtsSpokenTime

                // Evitar repetir la misma indicación en menos de 20 segundos
                if (isSameInstruction && elapsedMs < 20000L) {
                    return
                }

                // Espaciar anuncios al menos 14 segundos si la maniobra todavía está lejos (> 80m)
                if (elapsedMs < 14000L && distanceToNextManeuver > 80f) {
                    return
                }
            }

            val translated = translateInstruction(text, selectedLanguage.code)
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.let { am ->
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                }
            } catch (_: Exception) {}

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, params, "omnistudio-nav")
            lastTtsSpokenTime = now
            lastTtsSpokenInstruction = text
        }
    }

    fun speakCurrentInstruction(force: Boolean = false) {
        routeSteps.getOrNull(step)?.let {
            speakInstruction(it.instruction, force)
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
                speakInstruction(selectedLanguage.recalculatingText)
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

    // Suscripción al sensor GPS real con frecuencia adaptativa según modo ahorro de batería
    DisposableEffect(permission, isBatterySaverActive) {
        if (!permission) return@DisposableEffect onDispose {}
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                update(l)
            }
        }
        try {
            // Modo Normal: 1000ms (1s), 1 metro
            // Modo Ahorro: 5000ms (5s), 10 metros -> Reduce un 80% las interrupciones del chip GPS y el gasto de batería
            val minTimeMs = if (isBatterySaverActive) 5000L else 1000L
            val minDistanceM = if (isBatterySaverActive) 10f else 1f

            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, minTimeMs, minDistanceM, listener)
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

    // Observar si el usuario presionó "Finalizar navegación" desde la notificación persistente
    LaunchedEffect(Unit) {
        NavigationForegroundService.stopRequested.collect { requested ->
            if (requested) {
                navigating = false
                step = 0
                NavigationForegroundService.clearStopRequest()
            }
        }
    }

    // Observar si el usuario cambió el modo de voz (Silenciar / Activar voz) desde la notificación
    LaunchedEffect(Unit) {
        NavigationForegroundService.toggleVoiceRequested.collect { requested ->
            if (requested) {
                voiceEnabled = !voiceEnabled
                if (!voiceEnabled) {
                    tts?.stop()
                } else {
                    speakCurrentInstruction(force = true)
                }
                NavigationForegroundService.clearToggleVoiceRequest()
            }
        }
    }

    // Mantener la navegación activa en segundo plano mediante Foreground Service y notificaciones en vivo
    LaunchedEffect(step, distanceToNextManeuver, summary, navigating, voiceEnabled, selectedLanguage, isBatterySaverActive) {
        if (navigating) {
            val s = routeSteps.getOrNull(step)
            val currentInstruction = s?.let { translateInstruction(it.instruction, selectedLanguage.code) } ?: "Continúa por la ruta"
            val stepDist = if (distanceToNextManeuver > 0f) {
                if (distanceToNextManeuver >= 1000) String.format(Locale.getDefault(), "En %.1f km", distanceToNextManeuver / 1000.0)
                else "En ${distanceToNextManeuver.roundToInt()} m"
            } else s?.let {
                if (it.distance >= 1000) String.format(Locale.getDefault(), "En %.1f km", it.distance / 1000.0)
                else "En ${it.distance.roundToInt()} m"
            } ?: ""
            val title = if (stepDist.isNotBlank()) "$stepDist: $currentInstruction" else currentInstruction
            val etaInfo = summary?.let { sum ->
                val d = String.format(Locale.getDefault(), "%.1f km", sum.distance / 1000.0)
                val t = "${(sum.duration / 60.0).roundToInt()} min"
                val a = formatTime12Hour(System.currentTimeMillis() + sum.duration.toLong() * 1000L)
                "$d • $t • Llegada: $a"
            } ?: "Navegación activa"

            NavigationForegroundService.startOrUpdate(
                context,
                title,
                etaInfo,
                isVoiceActive = voiceEnabled,
                isBatterySaverActive = isBatterySaverActive
            )
        } else {
            NavigationForegroundService.stop(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!navigating) {
                NavigationForegroundService.stop(context)
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
        MapTileCacheManager.initialize(context.applicationContext)
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
            try {
                when (e) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            } catch (_: Throwable) {}
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
                    actions = {
                        IconButton(
                            onClick = {
                                cacheSizeMb = MapTileCacheManager.getCacheSizeMb(context)
                                isNetworkConnected = MapTileCacheManager.isNetworkAvailable(context)
                                showCacheDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = if (!isNetworkConnected) Icons.Default.CloudDone else Icons.Default.Storage,
                                contentDescription = "Gestión de Caché y Modo Offline",
                                tint = if (!isNetworkConnected) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
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
                    val rawInstruction = currentStep?.instruction?.ifBlank { "Continúa recto por la vía" } ?: "Continúa por la ruta"
                    val instructionText = translateInstruction(rawInstruction, selectedLanguage.code)
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
                                    shape = RoundedCornerShape(16.dp)
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

                                Spacer(Modifier.width(6.dp))

                                // Indicador de Batería y Selector de Modo Ahorro
                                Box {
                                    Surface(
                                        onClick = { batteryMenuOpen = true },
                                        color = if (isBatterySaverActive) Color(0xFFD97706).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isCharging) "⚡${batteryLevel}%"
                                                       else if (isBatterySaverActive) "🪫 ${batteryLevel}% Ahorro"
                                                       else "🔋 ${batteryLevel}%",
                                                color = if (isBatterySaverActive) Color(0xFFFDE68A) else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = batteryMenuOpen,
                                        onDismissRequest = { batteryMenuOpen = false }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text("Ahorro de batería", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                "Carga actual: $batteryLevel% ${if (isCharging) "(Cargando)" else ""}",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                if (isBatterySaverActive) "• Ahorro ACTIVO: GPS cada 5s y voz moderada"
                                                else "• Modo estándar: GPS continuo cada 1s",
                                                fontSize = 10.sp,
                                                color = if (isBatterySaverActive) Color(0xFFEAB308) else Color(0xFF10B981)
                                            )
                                        }
                                        BatterySaverMode.values().forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                mode.title,
                                                                fontWeight = if (batterySaverMode == mode) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (batterySaverMode == mode) Color(0xFF10B981) else Color.Unspecified
                                                            )
                                                            if (batterySaverMode == mode && isBatterySaverActive) {
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("• ACTIVO", color = Color(0xFFEAB308), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Text(mode.description, fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                },
                                                onClick = {
                                                    batterySaverMode = mode
                                                    batteryMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Dos botones arriba: Voz activa vs Voz silenciada
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Botón 1: 🔊 Voz activa
                            Surface(
                                onClick = {
                                    voiceEnabled = true
                                    speakCurrentInstruction(force = true)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (voiceEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Voz activa",
                                        tint = if (voiceEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Voz activa",
                                        color = if (voiceEnabled) Color.White else Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        fontWeight = if (voiceEnabled) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }

                            // Botón 2: 🔇 Voz silenciada
                            Surface(
                                onClick = {
                                    voiceEnabled = false
                                    tts?.stop()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (!voiceEnabled) Color(0xFFEF4444) else Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = "Voz silenciada",
                                        tint = if (!voiceEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Voz silenciada",
                                        color = if (!voiceEnabled) Color.White else Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.sp,
                                        fontWeight = if (!voiceEnabled) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Selector de Idioma TTS en HUD de Navegación
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp)
                        ) {
                            Surface(
                                onClick = { hudLanguageMenuOpen = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "🌐 Idioma TTS:",
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "${selectedLanguage.flag} ${selectedLanguage.name}",
                                            color = Color(0xFFA7F3D0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "Cambiar ▾",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = hudLanguageMenuOpen,
                                onDismissRequest = { hudLanguageMenuOpen = false }
                            ) {
                                availableTtsLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(lang.flag, fontSize = 16.sp)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    lang.name,
                                                    fontWeight = if (selectedLanguage.code == lang.code) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedLanguage.code == lang.code) Color(0xFF10B981) else Color.Unspecified
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedLanguage = lang
                                            hudLanguageMenuOpen = false
                                            try {
                                                tts?.setLanguage(lang.locale)
                                            } catch (_: Exception) {}
                                            // Si está silenciado, permanece en silencio absoluto sin emitir sonido.
                                            // Si tiene volumen activo, reproduce la indicación en el nuevo idioma.
                                            if (voiceEnabled) {
                                                speakCurrentInstruction(force = true)
                                            }
                                        }
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
                                formatTime12Hour(System.currentTimeMillis() + s.duration.toLong() * 1000L)
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
                                NavigationForegroundService.stop(context)
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

                                Spacer(Modifier.width(8.dp))

                                // Selector de idioma TTS para la ruta
                                Box {
                                    FilledTonalButton(onClick = { prepLanguageMenuOpen = true }) {
                                        Text("${selectedLanguage.flag} ${selectedLanguage.name}")
                                    }
                                    DropdownMenu(expanded = prepLanguageMenuOpen, onDismissRequest = { prepLanguageMenuOpen = false }) {
                                        availableTtsLanguages.forEach { lang ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(lang.flag, fontSize = 16.sp)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            lang.name,
                                                            fontWeight = if (selectedLanguage.code == lang.code) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (selectedLanguage.code == lang.code) Color(0xFF10B981) else Color.Unspecified
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedLanguage = lang
                                                    prepLanguageMenuOpen = false
                                                    try {
                                                        tts?.setLanguage(lang.locale)
                                                    } catch (_: Exception) {}
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Selector de Modo Ahorro de Batería en preparación de ruta
                                Box {
                                    FilledTonalButton(
                                        onClick = { prepBatteryMenuOpen = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            if (isCharging) "⚡${batteryLevel}%"
                                            else if (isBatterySaverActive) "🪫 ${batteryLevel}%"
                                            else "🔋 ${batteryLevel}%"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = prepBatteryMenuOpen,
                                        onDismissRequest = { prepBatteryMenuOpen = false }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text("Modo Ahorro de Batería", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                "Carga actual: $batteryLevel% ${if (isCharging) "(Cargando)" else ""}",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                if (isBatterySaverActive) "• Ahorro ACTIVO (GPS 5s, voz moderada)"
                                                else "• Modo estándar (GPS 1s continuo)",
                                                fontSize = 10.sp,
                                                color = if (isBatterySaverActive) Color(0xFFEAB308) else Color(0xFF10B981)
                                            )
                                        }
                                        BatterySaverMode.values().forEach { mode ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                mode.title,
                                                                fontWeight = if (batterySaverMode == mode) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (batterySaverMode == mode) Color(0xFF10B981) else Color.Unspecified
                                                            )
                                                            if (batterySaverMode == mode && isBatterySaverActive) {
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("• ACTIVO", color = Color(0xFFEAB308), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        Text(mode.description, fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                },
                                                onClick = {
                                                    batterySaverMode = mode
                                                    prepBatteryMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

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
                                    formatTime12Hour(System.currentTimeMillis() + s.duration.toLong() * 1000L)
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

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    if (!preCacheProgress.isDownloading && points.isNotEmpty()) {
                                                        MapTileCacheManager.preCacheRouteTiles(context, points, STYLE) { progress ->
                                                            preCacheProgress = progress
                                                            if (progress.isComplete) {
                                                                cacheSizeMb = MapTileCacheManager.getCacheSizeMb(context)
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                if (preCacheProgress.isDownloading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = Color(0xFF38BDF8),
                                                        strokeWidth = 2.dp
                                                    )
                                                } else if (preCacheProgress.isComplete) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Ruta guardada offline",
                                                        tint = Color(0xFF10B981)
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Download,
                                                        contentDescription = "Descargar ruta para uso sin conexión",
                                                        tint = Color(0xFF38BDF8)
                                                    )
                                                }
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

    // Diálogo de Gestión de Caché y Modo Offline de MapLibre
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            containerColor = Color(0xFF1E293B),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(Modifier.width(8.dp))
                    Text("Caché y Modo Offline", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Estado de Red
                    Surface(
                        color = if (isNetworkConnected) Color(0xFF064E3B).copy(alpha = 0.6f) else Color(0xFF7C2D12).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isNetworkConnected) Icons.Default.CloudDone else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (isNetworkConnected) Color(0xFF34D399) else Color(0xFFF87171),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isNetworkConnected) "Conectividad activa (Almacenamiento automático en curso)"
                                else "Modo sin conexión activo (Usando baldosas locales)",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Espacio en Disco Utilizado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Espacio de baldosas en disco:", fontSize = 13.sp, color = Color.LightGray)
                        Text(
                            String.format(Locale.getDefault(), "%.2f MB", cacheSizeMb),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    // Estado de descarga de ruta
                    if (preCacheProgress.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Descargando baldosas de ruta...", fontSize = 12.sp, color = Color(0xFF38BDF8))
                                Text("${preCacheProgress.percentage}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                            LinearProgressIndicator(
                                progress = { preCacheProgress.percentage / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = Color(0xFF38BDF8),
                                trackColor = Color(0xFF334155)
                            )
                            if (preCacheProgress.totalTiles > 0) {
                                Text(
                                    "${preCacheProgress.completedTiles} de ${preCacheProgress.totalTiles} baldosas guardadas",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else if (preCacheProgress.isComplete) {
                        Surface(
                            color = Color(0xFF065F46).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ruta completamente lista para navegar sin conexión", fontSize = 12.sp, color = Color(0xFFA7F3D0))
                            }
                        }
                    } else if (preCacheProgress.errorMessage != null) {
                        Text(
                            "Nota: ${preCacheProgress.errorMessage}",
                            fontSize = 11.sp,
                            color = Color(0xFFFCA5A5)
                        )
                    }

                    // Botón para pre-descargar la ruta actual si existe
                    if (points.isNotEmpty() && !preCacheProgress.isDownloading) {
                        OutlinedButton(
                            onClick = {
                                MapTileCacheManager.preCacheRouteTiles(context, points, STYLE) { progress ->
                                    preCacheProgress = progress
                                    if (progress.isComplete) {
                                        cacheSizeMb = MapTileCacheManager.getCacheSizeMb(context)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar baldosas de la ruta actual", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        }
                    }

                    // Configuración de Eliminación Automática por Días
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Auto-eliminar descargas tras:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Selector de días (7, 15, 30, 60, Nunca)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val options = listOf(
                                7 to "7 d",
                                15 to "15 d",
                                30 to "30 d",
                                60 to "60 d",
                                -1 to "Nunca"
                            )

                            options.forEach { (days, label) ->
                                val isSelected = autoCleanDays == days
                                Surface(
                                    onClick = {
                                        autoCleanDays = days
                                        MapTileCacheManager.setAutoCleanDays(context, days)
                                        cleanNoticeMessage = null
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF334155),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color.LightGray
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (autoCleanDays > 0)
                                "Las descargas de más de $autoCleanDays días se depuran automáticamente al iniciar la app."
                            else
                                "Las descargas permanecerán guardadas de forma indefinida.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        if (autoCleanDays > 0) {
                            OutlinedButton(
                                onClick = {
                                    MapTileCacheManager.cleanExpiredDownloads(context) { deleted ->
                                        cacheSizeMb = MapTileCacheManager.getCacheSizeMb(context)
                                        cleanNoticeMessage = if (deleted > 0) {
                                            "Se depuraron $deleted ruta(s) de más de $autoCleanDays días"
                                        } else {
                                            "No se encontraron descargas que superen $autoCleanDays días"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Limpiar descargas expiradas ahora", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        if (cleanNoticeMessage != null) {
                            Text(
                                cleanNoticeMessage ?: "",
                                fontSize = 11.sp,
                                color = Color(0xFF34D399)
                            )
                        }
                    }

                    // Botón para vaciar caché
                    OutlinedButton(
                        onClick = {
                            if (!isClearingCache) {
                                isClearingCache = true
                                cleanNoticeMessage = "Liberando espacio y compactando..."
                                MapTileCacheManager.clearCache(context) {
                                    cacheSizeMb = MapTileCacheManager.getCacheSizeMb(context)
                                    preCacheProgress = PreCacheProgress()
                                    cleanNoticeMessage = "Almacenamiento vaciado con éxito"
                                    isClearingCache = false
                                }
                            }
                        },
                        enabled = !isClearingCache,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444),
                            disabledContentColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isClearingCache) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFEF4444),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Vaciando almacenamiento...", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color(0xFFEF4444))
                            Spacer(Modifier.width(6.dp))
                            Text("Vaciar todo el almacenamiento en caché", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCacheDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    )
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}
