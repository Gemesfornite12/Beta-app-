package com.example.ui.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OmniViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enumeración de rutas basadas en Hash (#) para la gestión del enrutador HashRouter.
 */
enum class HashRoute(
    val hash: String,
    val routeKey: String,
    val displayName: String,
    val shortName: String,
    val icon: ImageVector,
    val accentColor: Color,
    val description: String
) {
    HOME(
        hash = "#/home",
        routeKey = "home",
        displayName = "Inicio Cloud",
        shortName = "Inicio",
        icon = Icons.Default.Home,
        accentColor = Color(0xFF6366F1),
        description = "Dashboard principal y archivos recientes"
    ),
    DOC_EDITOR(
        hash = "#/editor",
        routeKey = "doc_editor",
        displayName = "Editor de Documentos",
        shortName = "Editor",
        icon = Icons.Default.Description,
        accentColor = Color(0xFF38BDF8),
        description = "Editor enriquecido y diapositivas estilo WPS"
    ),
    MUSIC_STUDIO(
        hash = "#/music",
        routeKey = "music_studio",
        displayName = "Suite de Creación Musical",
        shortName = "Música",
        icon = Icons.Default.MusicNote,
        accentColor = Color(0xFFA855F7),
        description = "Secuenciador 16 pasos y sintetizador de audio"
    ),
    CHAT(
        hash = "#/chat",
        routeKey = "chat",
        displayName = "Interfaz de Chat",
        shortName = "Chat",
        icon = Icons.Default.Forum,
        accentColor = Color(0xFF10B981),
        description = "Mensajería en tiempo real y colaboración"
    ),
    PROFILE(
        hash = "#/profile",
        routeKey = "profile",
        displayName = "Perfil de Usuario",
        shortName = "Perfil",
        icon = Icons.Default.Person,
        accentColor = Color(0xFFF59E0B),
        description = "Información de cuenta y almacenamiento cloud"
    ),
    AUTH(
        hash = "#/auth",
        routeKey = "auth",
        displayName = "Acceso Cloud",
        shortName = "Acceso",
        icon = Icons.Default.Lock,
        accentColor = Color(0xFFEC4899),
        description = "Inicio de sesión y recuperación de clave"
    );

    companion object {
        fun fromHash(rawHash: String?): HashRoute {
            if (rawHash.isNullOrBlank()) return HOME
            val normalized = rawHash.trim().lowercase()
                .removePrefix("https://omnistudio.cloud")
                .removePrefix("omnistudio://app")
                .trim()

            return when {
                normalized.contains("editor") || normalized.contains("doc") -> DOC_EDITOR
                normalized.contains("music") || normalized.contains("beat") || normalized.contains("studio") -> MUSIC_STUDIO
                normalized.contains("chat") || normalized.contains("msg") -> CHAT
                normalized.contains("profile") || normalized.contains("user") -> PROFILE
                normalized.contains("auth") || normalized.contains("login") -> AUTH
                normalized.contains("home") -> HOME
                else -> HOME
            }
        }
    }
}

/**
 * Controlador de navegación HashRouter que implementa un historial de rutas
 * utilizando fragmentos Hash (#/editor, #/music, #/chat) para gestionar transiciones
 * de estado instantáneas entre módulos.
 */
class HashRouter(initialRoute: HashRoute = HashRoute.HOME) {

    private val _history = MutableStateFlow<List<HashRoute>>(listOf(initialRoute))
    val history: StateFlow<List<HashRoute>> = _history.asStateFlow()

    private val _currentRoute = MutableStateFlow(initialRoute)
    val currentRoute: StateFlow<HashRoute> = _currentRoute.asStateFlow()

    private val _currentHash = MutableStateFlow(initialRoute.hash)
    val currentHash: StateFlow<String> = _currentHash.asStateFlow()

    fun push(route: HashRoute) {
        if (_currentRoute.value == route) return
        val updated = _history.value.toMutableList().apply { add(route) }
        _history.value = updated
        _currentRoute.value = route
        _currentHash.value = route.hash
    }

    fun push(hashString: String) {
        val route = HashRoute.fromHash(hashString)
        push(route)
    }

    fun replace(route: HashRoute) {
        val updated = _history.value.toMutableList()
        if (updated.isNotEmpty()) {
            updated[updated.lastIndex] = route
        } else {
            updated.add(route)
        }
        _history.value = updated
        _currentRoute.value = route
        _currentHash.value = route.hash
    }

    fun replace(hashString: String) {
        val route = HashRoute.fromHash(hashString)
        replace(route)
    }

    fun pop(): Boolean {
        val current = _history.value
        if (current.size > 1) {
            val updated = current.dropLast(1)
            _history.value = updated
            val newTop = updated.last()
            _currentRoute.value = newTop
            _currentHash.value = newTop.hash
            return true
        }
        return false
    }

    fun canPop(): Boolean = _history.value.size > 1

    fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            push("#/$fragment")
        } else {
            val path = uri.path
            if (!path.isNullOrBlank()) {
                push(path)
            }
        }
    }

    fun getShareableUrl(route: HashRoute = _currentRoute.value): String {
        return "https://omnistudio.cloud/${route.hash}"
    }
}

val LocalHashRouter = staticCompositionLocalOf<HashRouter> {
    error("HashRouter no ha sido provisto en el CompositionLocal")
}

@Composable
fun rememberHashRouter(initialRoute: HashRoute = HashRoute.HOME): HashRouter {
    return remember { HashRouter(initialRoute) }
}

/**
 * Barra de Navegación HashRouter Dock:
 * Permite conmutar directamente con un solo toque entre el Editor de Documentos,
 * la Suite de Creación Musical y la Interfaz de Chat, exhibiendo la ruta Hash activa (#).
 */
@Composable
fun HashRouterDock(
    hashRouter: HashRouter,
    viewModel: OmniViewModel,
    modifier: Modifier = Modifier
) {
    val currentRoute by hashRouter.currentRoute.collectAsState()
    val currentHash by hashRouter.currentHash.collectAsState()
    var showInspectorDialog by remember { mutableStateOf(false) }

    // No mostrar la barra en la pantalla de autenticación
    if (currentRoute == HashRoute.AUTH) return

    val primaryDestinations = listOf(
        HashRoute.HOME,
        HashRoute.DOC_EDITOR,
        HashRoute.MUSIC_STUDIO,
        HashRoute.CHAT
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Chip flotante de estado HashRouter con la URL actual
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, currentRoute.accentColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showInspectorDialog = true }
                    .testTag("chip_hash_router_inspector")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "Hash Router",
                        tint = currentRoute.accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "HashRouter: omnistudio.cloud/$currentHash",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(currentRoute.accentColor)
                    )
                }
            }
        }

        // Dock principal con los 3 pilares clave + Home
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                primaryDestinations.forEach { dest ->
                    val isSelected = currentRoute == dest
                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) dest.accentColor.copy(alpha = 0.2f) else Color.Transparent,
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        label = "dock_bg"
                    )
                    val animatedContentColor by animateColorAsState(
                        targetValue = if (isSelected) dest.accentColor else Color(0xFF94A3B8),
                        animationSpec = tween(250),
                        label = "dock_text"
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = animatedBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                when (dest) {
                                    HashRoute.DOC_EDITOR -> viewModel.ensureDocumentForEditor()
                                    HashRoute.MUSIC_STUDIO -> viewModel.ensureAudioProjectForStudio()
                                    else -> {}
                                }
                                hashRouter.push(dest)
                            }
                            .testTag("dock_tab_${dest.routeKey}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.displayName,
                                tint = animatedContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dest.shortName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = animatedContentColor
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInspectorDialog) {
        HashRouteInspectorDialog(
            hashRouter = hashRouter,
            viewModel = viewModel,
            onDismiss = { showInspectorDialog = false }
        )
    }
}

/**
 * Diálogo interactivo del HashRouter para inspeccionar el historial de rutas,
 * navegar directamente ingresando un fragmento hash (#/editor, #/music, #/chat)
 * o copiar el enlace directo.
 */
@Composable
fun HashRouteInspectorDialog(
    hashRouter: HashRouter,
    viewModel: OmniViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentHash by hashRouter.currentHash.collectAsState()
    val history by hashRouter.history.collectAsState()
    var inputHash by remember { mutableStateOf(currentHash) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF6366F1).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AltRoute, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Gestor HashRouter", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Navegación por fragmentos URI", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Introduce o selecciona una ruta Hash para conmutar directamente:",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputHash,
                    onValueChange = { inputHash = it },
                    label = { Text("Ruta Hash (#/...)") },
                    leadingIcon = {
                        Icon(Icons.Default.Tag, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_hash_route"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Destinos Clave:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(6.dp))

                val quickRoutes = listOf(
                    HashRoute.DOC_EDITOR,
                    HashRoute.MUSIC_STUDIO,
                    HashRoute.CHAT,
                    HashRoute.HOME,
                    HashRoute.PROFILE
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickRoutes.forEach { r ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (inputHash == r.hash) r.accentColor.copy(alpha = 0.3f) else Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, r.accentColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    inputHash = r.hash
                                    when (r) {
                                        HashRoute.DOC_EDITOR -> viewModel.ensureDocumentForEditor()
                                        HashRoute.MUSIC_STUDIO -> viewModel.ensureAudioProjectForStudio()
                                        else -> {}
                                    }
                                    hashRouter.push(r)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(r.icon, contentDescription = null, tint = r.accentColor, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(r.hash, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Pila de Historial:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        history.forEachIndexed { idx, r ->
                            Text(
                                text = r.hash,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (idx == history.lastIndex) r.accentColor else Color(0xFF64748B),
                                fontWeight = if (idx == history.lastIndex) FontWeight.Bold else FontWeight.Normal
                            )
                            if (idx < history.lastIndex) {
                                Text(" -> ", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = HashRoute.fromHash(inputHash)
                    when (target) {
                        HashRoute.DOC_EDITOR -> viewModel.ensureDocumentForEditor()
                        HashRoute.MUSIC_STUDIO -> viewModel.ensureAudioProjectForStudio()
                        else -> {}
                    }
                    hashRouter.push(target)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_hash_route")
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Navegar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OmniStudio Hash URL", hashRouter.getShareableUrl())
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "URL Hash copiada al portapapeles", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar URL", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        }
    )
}
