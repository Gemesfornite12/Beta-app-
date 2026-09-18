package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.example.data.firebase.ChatNotificationManager
import com.example.data.firebase.FcmTokenManager
import com.example.ui.navigation.HashRoute
import com.example.ui.navigation.HashRouter
import com.example.ui.navigation.HashRouterDock
import com.example.ui.navigation.LocalHashRouter
import com.example.ui.navigation.rememberHashRouter
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.docs.DocEditorScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.music.MusicStudioScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.OmniViewModel

class MainActivity : ComponentActivity() {
  private val viewModel: OmniViewModel by viewModels()
  private var currentIntentUri by mutableStateOf<Uri?>(null)
  private var pendingPushChannelId by mutableStateOf<String?>(null)

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    Log.d("MainActivity", "POST_NOTIFICATIONS permission result: $isGranted")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    currentIntentUri = intent?.data

    // Inicializar canales de notificación y registrar ciclo de vida
    ChatNotificationManager.createNotificationChannels(applicationContext)
    ChatNotificationManager.isAppInForeground = true

    // Extraer canal si la actividad se lanzó desde un toque en notificación push
    intent?.getStringExtra(ChatNotificationManager.EXTRA_CHANNEL_ID)?.let { chId ->
      pendingPushChannelId = chId
    }

    // Solicitar permiso POST_NOTIFICATIONS en Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    // Inicializar FCM y sincronizar token del dispositivo
    FcmTokenManager.initialize(applicationContext, "gonzalez24029@gmail.com")

    setContent {
      val isDarkTheme by viewModel.isDarkTheme.collectAsState()
      MyApplicationTheme(darkTheme = isDarkTheme) {
        OmniStudioApp(
          viewModel = viewModel,
          initialUri = currentIntentUri,
          pendingPushChannelId = pendingPushChannelId,
          onClearPendingPushChannel = { pendingPushChannelId = null }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    currentIntentUri = intent.data

    intent.getStringExtra(ChatNotificationManager.EXTRA_CHANNEL_ID)?.let { chId ->
      pendingPushChannelId = chId
    }
  }

  override fun onStart() {
    super.onStart()
    ChatNotificationManager.isAppInForeground = true
  }

  override fun onResume() {
    super.onResume()
    ChatNotificationManager.isAppInForeground = true
  }

  override fun onPause() {
    super.onPause()
    ChatNotificationManager.isAppInForeground = false
  }

  override fun onStop() {
    super.onStop()
    ChatNotificationManager.isAppInForeground = false
  }
}

@Composable
fun OmniStudioApp(
  viewModel: OmniViewModel,
  initialUri: Uri? = null,
  pendingPushChannelId: String? = null,
  onClearPendingPushChannel: () -> Unit = {}
) {
  val authState by viewModel.authUiState.collectAsState()
  val initialRoute = if (authState.isLoggedIn) HashRoute.HOME else HashRoute.AUTH
  val hashRouter = rememberHashRouter(initialRoute = initialRoute)
  val currentRoute by hashRouter.currentRoute.collectAsState()

  // Sincronizar estado de sesión con HashRouter
  LaunchedEffect(authState.isLoggedIn) {
    if (!authState.isLoggedIn && currentRoute != HashRoute.AUTH) {
      hashRouter.replace(HashRoute.AUTH)
    } else if (authState.isLoggedIn && currentRoute == HashRoute.AUTH) {
      hashRouter.replace(HashRoute.HOME)
    }
  }

  // Manejar Deep Links y fragmentos Hash (#/editor, #/music, #/chat)
  LaunchedEffect(initialUri) {
    if (initialUri != null) {
      hashRouter.handleDeepLink(initialUri)
    }
  }

  // Manejar navegación directa a canal de chat al tocar una notificación push
  LaunchedEffect(pendingPushChannelId) {
    if (!pendingPushChannelId.isNullOrBlank()) {
      viewModel.loadChannelMessages(pendingPushChannelId)
      hashRouter.push(HashRoute.CHAT)
      onClearPendingPushChannel()
    }
  }

  // Manejar botón Atrás del sistema mediante la pila del HashRouter
  BackHandler(enabled = hashRouter.canPop()) {
    hashRouter.pop()
  }

  CompositionLocalProvider(LocalHashRouter provides hashRouter) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = Color(0xFF0F172A),
      bottomBar = {
        if (authState.isLoggedIn && currentRoute != HashRoute.AUTH) {
          HashRouterDock(
            hashRouter = hashRouter,
            viewModel = viewModel
          )
        }
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = innerPadding.calculateBottomPadding())
      ) {
        AnimatedContent(
          targetState = currentRoute,
          transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 40)) +
              scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
              .togetherWith(fadeOut(animationSpec = tween(150)))
          },
          label = "hash_router_transition"
        ) { route ->
          when (route) {
            HashRoute.AUTH -> {
              AuthScreen(
                viewModel = viewModel,
                onAuthSuccess = {
                  hashRouter.replace(HashRoute.HOME)
                }
              )
            }

            HashRoute.HOME -> {
              HomeScreen(
                viewModel = viewModel,
                onOpenDocEditor = {
                  viewModel.ensureDocumentForEditor()
                  hashRouter.push(HashRoute.DOC_EDITOR)
                },
                onOpenMusicStudio = {
                  viewModel.ensureAudioProjectForStudio()
                  hashRouter.push(HashRoute.MUSIC_STUDIO)
                },
                onOpenChat = {
                  hashRouter.push(HashRoute.CHAT)
                },
                onOpenProfile = {
                  hashRouter.push(HashRoute.PROFILE)
                }
              )
            }

            HashRoute.DOC_EDITOR -> {
              DocEditorScreen(
                viewModel = viewModel,
                onBack = {
                  if (!hashRouter.pop()) hashRouter.push(HashRoute.HOME)
                },
                onShareToChat = {
                  hashRouter.push(HashRoute.CHAT)
                }
              )
            }

            HashRoute.MUSIC_STUDIO -> {
              MusicStudioScreen(
                viewModel = viewModel,
                onBack = {
                  if (!hashRouter.pop()) hashRouter.push(HashRoute.HOME)
                },
                onShareToChat = {
                  hashRouter.push(HashRoute.CHAT)
                }
              )
            }

            HashRoute.CHAT -> {
              ChatScreen(
                viewModel = viewModel,
                onBack = {
                  if (!hashRouter.pop()) hashRouter.push(HashRoute.HOME)
                },
                onOpenDoc = { doc ->
                  viewModel.openDocument(doc)
                  hashRouter.push(HashRoute.DOC_EDITOR)
                },
                onOpenAudio = { audio ->
                  viewModel.openAudioProject(audio)
                  hashRouter.push(HashRoute.MUSIC_STUDIO)
                }
              )
            }

            HashRoute.PROFILE -> {
              ProfileScreen(
                viewModel = viewModel,
                onBack = {
                  if (!hashRouter.pop()) hashRouter.push(HashRoute.HOME)
                },
                onLogout = {
                  viewModel.logout()
                  hashRouter.replace(HashRoute.AUTH)
                }
              )
            }
          }
        }
      }
    }
  }
}


