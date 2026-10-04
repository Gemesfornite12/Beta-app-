package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.example.data.youtube.YouTubeClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Format milliseconds to MM:SS string.
 */
private fun formatMillisToTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}


internal data class FittedVideoSize(val width: Int, val height: Int)

/** Fits portrait or landscape video inside a viewport without cropping or distorting it. */
internal fun fitVideoIntoBounds(videoWidth: Int, videoHeight: Int, boundsWidth: Int, boundsHeight: Int): FittedVideoSize {
    if (boundsWidth <= 0 || boundsHeight <= 0) return FittedVideoSize(0, 0)
    val sourceWidth = videoWidth.takeIf { it > 0 } ?: 16
    val sourceHeight = videoHeight.takeIf { it > 0 } ?: 9
    val scale = minOf(boundsWidth.toFloat() / sourceWidth, boundsHeight.toFloat() / sourceHeight)
    return FittedVideoSize(
        width = (sourceWidth * scale).roundToInt().coerceIn(1, boundsWidth),
        height = (sourceHeight * scale).roundToInt().coerceIn(1, boundsHeight)
    )
}

/** VideoView otherwise sits in a fixed 16:9 Compose box; measure it to the source ratio inside the full viewer. */
private class AspectFitVideoView(context: Context) : VideoView(context) {
    private var sourceWidth = 0
    private var sourceHeight = 0

    fun updateSourceSize(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val boundsWidth = MeasureSpec.getSize(widthMeasureSpec)
        val boundsHeight = MeasureSpec.getSize(heightMeasureSpec)
        val fitted = fitVideoIntoBounds(sourceWidth, sourceHeight, boundsWidth, boundsHeight)
        setMeasuredDimension(fitted.width, fitted.height)
    }
}

/**
 * Reusable VideoPlayer composable for YouTube and standard video content (MP4, WebM, 3GP, local URI).
 * Renders video thumbnails and badges, handing YouTube taps to the full-screen
 * in-chat viewer and standard-video taps to the app's normal video player.
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    channelTitle: String? = null,
    thumbnailUrl: String? = null,
    showActionButtons: Boolean = true,
    onLaunchOverlay: ((videoId: String, title: String) -> Unit)? = null,
    onLaunchStandardVideo: ((videoUrl: String, title: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val videoId = remember(videoUrl) {
        YouTubeClient.extractVideoId(videoUrl)
    }
    val isYouTube = videoId != null

    // High quality thumbnail calculation
    val resolvedThumbnail = remember(thumbnailUrl, videoId, videoUrl) {
        if (!thumbnailUrl.isNullOrBlank()) {
            thumbnailUrl
        } else if (videoId != null) {
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else {
            null
        }
    }

    val primaryColor = if (isYouTube) Color(0xFFFF0000) else Color(0xFF6366F1)
    val badgeLabel = if (isYouTube) "YOUTUBE" else "VIDEO HD"

    // Pulse animation for play button overlay
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (isYouTube && videoId != null && onLaunchOverlay != null) {
                    onLaunchOverlay(videoId, title ?: "Video de YouTube")
                } else if (!isYouTube && onLaunchStandardVideo != null) {
                    onLaunchStandardVideo(videoUrl, title ?: "Video")
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            val uri = Uri.parse(videoUrl)
                            if (isYouTube) {
                                data = uri
                            } else {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Abriendo enlace de video...", Toast.LENGTH_SHORT).show()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No se pudo abrir el video", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .testTag(if (videoId != null) "video_player_$videoId" else "video_player_standard")
    ) {
        Column {
            // Thumbnail container with 16:9 aspect ratio and play button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (!resolvedThumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = resolvedThumbnail,
                        contentDescription = title ?: "Miniatura de video",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "Miniatura no disponible",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                // Platform Badge (YOUTUBE or VIDEO HD)
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = primaryColor,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isYouTube) Icons.Default.PlayArrow else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = badgeLabel,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // In-App Overlay indicator badge
                if ((isYouTube && onLaunchOverlay != null) || (!isYouTube && onLaunchStandardVideo != null)) {
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "En Chat",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Center Play Button with Glow
                Surface(
                    shape = CircleShape,
                    color = primaryColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(52.dp)
                        .scale(pulseScale)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir Video",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom title overlay inside thumbnail if provided
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Optional Action Bar & Metadata Footer
            if (showActionButtons) {
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (!channelTitle.isNullOrBlank()) {
                                Text(
                                    text = if (isYouTube) "📺 $channelTitle" else "🎥 $channelTitle",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (videoId != null) {
                                Text(
                                    text = "ID: $videoId",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "Video estándar (MP4/WebM)",
                                    color = Color(0xFFC7D2FE),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy Link Action
                            IconButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Video Link", videoUrl)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar enlace",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Open External Video Action
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            val uri = Uri.parse(videoUrl)
                                            if (isYouTube) {
                                                data = uri
                                            } else {
                                                setDataAndType(uri, "video/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "No se pudo abrir la aplicación externa", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = if (isYouTube) "Abrir en YouTube" else "Abrir en reproductor externo",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Primary Play Button (Overlay or Launch)
                            TextButton(
                                onClick = {
                                    if (isYouTube && videoId != null && onLaunchOverlay != null) {
                                        onLaunchOverlay(videoId, title ?: "Video de YouTube")
                                    } else if (!isYouTube && onLaunchStandardVideo != null) {
                                        onLaunchStandardVideo(videoUrl, title ?: "Video")
                                    } else {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                val uri = Uri.parse(videoUrl)
                                                if (isYouTube) data = uri
                                                else {
                                                    setDataAndType(uri, "video/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_play_video")
                            ) {
                                Text(
                                    text = if ((isYouTube && onLaunchOverlay != null) || (!isYouTube && onLaunchStandardVideo != null)) "Ver en Chat ▶" else "Ver ▶",
                                    fontSize = 11.sp,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern, full-featured Standard Video Player Dialog.
 * Plays generic MP4, WebM, 3GP, local URIs and HTTP video streams with hardware acceleration,
 * custom play/pause/seek controls, volume mute toggle, duration timer, and fallback options.
 */
@Composable
fun StandardVideoPlayerDialog(
    videoUrl: String,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlaybackSafely() {
        try {
            val vView = videoViewRef
            if (vView != null) {
                if (vView.isPlaying) {
                    vView.pause()
                }
                vView.stopPlayback()
            }
        } catch (_: Exception) {}
        try {
            mediaPlayerRef?.reset()
        } catch (_: Exception) {}
    }

    // Coroutine to poll playback progress
    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isActive && isPlaying && !isUserSeeking) {
            val vView = videoViewRef
            if (vView != null && vView.isPlaying) {
                currentPositionMs = vView.currentPosition.toLong()
                val dur = vView.duration.toLong()
                if (dur > 0) durationMs = dur
            }
            delay(250)
        }
    }

    // Auto-hide controls after 3.5 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    Dialog(
        onDismissRequest = {
            stopPlaybackSafely()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable {
                    stopPlaybackSafely()
                    onDismiss()
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.6f)),
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 550.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
                    .testTag("standard_video_player_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with title and top actions
                    Surface(
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF6366F1),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = title.ifBlank { "Reproduciendo video" },
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Video HD • Reproductor Nativo",
                                        color = Color(0xFF818CF8),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Open External App
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(videoUrl), "video/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "No se encontró otra app compatible", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = "Abrir con reproductor externo",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Close Button
                                IconButton(
                                    onClick = {
                                        stopPlaybackSafely()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_close_standard_video_dialog")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar reproductor",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Video View Area with Interactive Controls Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                            .clickable { showControls = !showControls },
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    try {
                                        setVideoURI(Uri.parse(videoUrl))
                                    } catch (e: Exception) {
                                        hasError = true
                                        errorMessage = e.localizedMessage ?: "Error al cargar video"
                                    }

                                    setOnPreparedListener { mp ->
                                        mediaPlayerRef = mp
                                        isBuffering = false
                                        durationMs = mp.duration.toLong()
                                        mp.isLooping = isLooping
                                        if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                                        start()
                                        isPlaying = true
                                    }

                                    setOnInfoListener { _, what, _ ->
                                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                                            isBuffering = true
                                        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                                            isBuffering = false
                                        }
                                        true
                                    }

                                    setOnCompletionListener {
                                        isPlaying = false
                                        showControls = true
                                    }

                                    setOnErrorListener { mp, what, _ ->
                                        isBuffering = false
                                        hasError = true
                                        errorMessage = "No se pudo decodificar el formato de video (Código: $what)"
                                        try {
                                            if (mp != null && mp.isPlaying) {
                                                mp.pause()
                                            }
                                            mp?.reset()
                                        } catch (_: Exception) {}
                                        true
                                    }

                                    videoViewRef = this
                                }
                            },
                            update = { vView ->
                                videoViewRef = vView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Buffering indicator
                        if (isBuffering && !hasError) {
                            CircularProgressIndicator(
                                color = Color(0xFF6366F1),
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        // Error Banner with Fallback
                        if (hasError) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⚠️ Formato no compatible en vista previa",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = errorMessage.ifBlank { "Intenta abrir el video en una app externa (VLC, Galería o Fotos)." },
                                        color = Color(0xFFFECACA),
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(videoUrl), "video/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "No se pudo abrir", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text("Abrir con app externa ▶", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Controls Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showControls && !hasError,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                            ) {
                                // Center Big Play/Pause/Replay Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF6366F1).copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .align(Alignment.Center)
                                        .clickable {
                                            val vView = videoViewRef
                                            if (vView != null) {
                                                if (vView.isPlaying) {
                                                    vView.pause()
                                                    isPlaying = false
                                                } else {
                                                    if (currentPositionMs >= durationMs && durationMs > 0) {
                                                        vView.seekTo(0)
                                                        currentPositionMs = 0
                                                    }
                                                    vView.start()
                                                    isPlaying = true
                                                }
                                            }
                                        }
                                        .testTag("btn_center_play_pause")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else if (currentPositionMs >= durationMs && durationMs > 0) Icons.Default.Replay else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // Bottom Controls Bar
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        // Slider Scrubber
                                        val displayPos = if (isUserSeeking) seekPositionMs else currentPositionMs.toFloat()
                                        val maxDur = if (durationMs > 0) durationMs.toFloat() else 1f

                                        Slider(
                                            value = displayPos.coerceIn(0f, maxDur),
                                            onValueChange = { newVal ->
                                                isUserSeeking = true
                                                seekPositionMs = newVal
                                            },
                                            onValueChangeFinished = {
                                                isUserSeeking = false
                                                videoViewRef?.seekTo(seekPositionMs.toInt())
                                                currentPositionMs = seekPositionMs.toLong()
                                            },
                                            valueRange = 0f..maxDur,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF6366F1),
                                                activeTrackColor = Color(0xFF6366F1),
                                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(24.dp)
                                                .testTag("slider_video_seek")
                                        )

                                        // Time display and playback toggles
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        val vView = videoViewRef
                                                        if (vView != null) {
                                                            if (vView.isPlaying) {
                                                                vView.pause()
                                                                isPlaying = false
                                                            } else {
                                                                if (currentPositionMs >= durationMs && durationMs > 0) {
                                                                    vView.seekTo(0)
                                                                    currentPositionMs = 0
                                                                }
                                                                vView.start()
                                                                isPlaying = true
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(4.dp))

                                                Text(
                                                    text = "${formatMillisToTime(if (isUserSeeking) seekPositionMs.toLong() else currentPositionMs)} / ${formatMillisToTime(durationMs)}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Mute Toggle
                                                IconButton(
                                                    onClick = {
                                                        isMuted = !isMuted
                                                        val mp = mediaPlayerRef
                                                        if (isMuted) {
                                                            mp?.setVolume(0f, 0f)
                                                        } else {
                                                            mp?.setVolume(1f, 1f)
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                        contentDescription = "Silenciar / Activar sonido",
                                                        tint = if (isMuted) Color(0xFFF87171) else Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Loop Toggle
                                                IconButton(
                                                    onClick = {
                                                        isLooping = !isLooping
                                                        mediaPlayerRef?.isLooping = isLooping
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Repeat,
                                                        contentDescription = "Repetición continua",
                                                        tint = if (isLooping) Color(0xFF818CF8) else Color(0xFF94A3B8),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar inside Overlay
                    Surface(
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 Toca el video para pausar o adelantar",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )

                            TextButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Video URL", videoUrl)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar Video URL", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            stopPlaybackSafely()
        }
    }
}

/** Full-screen, in-app player for video attachments opened from a chat message. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatVideoViewerDialog(
    videoUrl: String,
    title: String,
    caption: String? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit = {},
    onShare: () -> Unit = {},
    onReply: () -> Unit = {}
) {
    val context = LocalContext.current
    var isLoading by remember(videoUrl) { mutableStateOf(true) }
    var errorMessage by remember(videoUrl) { mutableStateOf<String?>(null) }
    var showMediaMenu by remember { mutableStateOf(false) }
    var videoViewRef by remember(videoUrl) { mutableStateOf<VideoView?>(null) }

    Dialog(
        onDismissRequest = {
            try { videoViewRef?.stopPlayback() } catch (_: Exception) {}
            onDismiss()
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize().testTag("chat_video_viewer")) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color(0xFF111111))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            try { videoViewRef?.stopPlayback() } catch (_: Exception) {}
                            onDismiss()
                        },
                        modifier = Modifier.size(44.dp).testTag("btn_close_video_viewer")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al chat", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Video • Reproductor en chat", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(40.dp).testTag("btn_save_video_viewer")) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar video", tint = Color.White)
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp).testTag("btn_share_video_viewer")) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir video", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showMediaMenu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMediaMenu, onDismissRequest = { showMediaMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Copiar enlace") },
                                onClick = {
                                    showMediaMenu = false
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Video URL", videoUrl))
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (errorMessage == null) {
                        AndroidView(
                            factory = { ctx ->
                                val fittedVideo = AspectFitVideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnPreparedListener { mediaPlayer ->
                                        updateSourceSize(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                                        isLoading = false
                                        start()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        isLoading = false
                                        errorMessage = "Este video no se puede reproducir en este dispositivo (código $what/$extra)."
                                        true
                                    }
                                    try {
                                        setVideoURI(Uri.parse(videoUrl))
                                    } catch (error: Exception) {
                                        isLoading = false
                                        errorMessage = error.localizedMessage ?: "No se pudo cargar el video."
                                    }
                                    videoViewRef = this
                                }
                                FrameLayout(ctx).apply {
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    addView(
                                        fittedVideo,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            Gravity.CENTER
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize().testTag("chat_video_surface")
                        )
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(38.dp))
                    } else {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                }

                Surface(color = Color(0xFF111111), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(caption?.takeIf(String::isNotBlank) ?: title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onReply, modifier = Modifier.testTag("btn_reply_video_viewer")) {
                            Text("Responder", color = Color(0xFF93C5FD), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            try { videoViewRef?.stopPlayback() } catch (_: Exception) {}
            videoViewRef = null
        }
    }
}

/** Full-screen, in-app photo/GIF viewer for media opened from a chat message. */
@Composable
fun ChatImageViewerDialog(
    imageUrl: String,
    title: String,
    caption: String? = null,
    mediaType: String = "image",
    onDismiss: () -> Unit,
    onSave: () -> Unit = {},
    onShare: () -> Unit = {},
    onReply: () -> Unit = {}
) {
    val context = LocalContext.current
    var showMediaMenu by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize().testTag("dialog_media_preview")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color(0xFF111111))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).testTag("btn_close_preview_x")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al chat", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = when (mediaType) {
                                "gif" -> "GIF"
                                "sticker" -> "Sticker"
                                else -> "Foto"
                            },
                            color = Color(0xFF9CA3AF),
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(40.dp).testTag("btn_save_image_viewer")) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar foto", tint = Color.White)
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp).testTag("btn_share_image_viewer")) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir foto", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showMediaMenu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMediaMenu, onDismissRequest = { showMediaMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Copiar enlace") },
                                onClick = {
                                    showMediaMenu = false
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Media URL", imageUrl))
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl.replace("http://", "https://"),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(color = Color(0xFF111111), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = caption?.takeIf(String::isNotBlank) ?: title,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onReply, modifier = Modifier.testTag("btn_reply_image_viewer")) {
                            Text("Responder", color = Color(0xFF93C5FD), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Builds a YouTube embed URL configured to stay in the app's WebView. */
private const val YOUTUBE_APP_ORIGIN = "https://costalso2029.dpdns.org"

internal fun buildYouTubeEmbedUrl(videoId: String, origin: String = YOUTUBE_APP_ORIGIN): String =
    "https://www.youtube.com/embed/$videoId?autoplay=0&playsinline=1&controls=1&rel=0&modestbranding=1&enablejsapi=1&origin=${Uri.encode(origin)}"

internal fun youtubePlayerErrorMessage(rawCode: String): String = when (rawCode.substringBefore('-').toIntOrNull()) {
    2 -> "YouTube recibió un identificador de video no válido (error $rawCode)."
    5 -> "El reproductor HTML5 no pudo reproducir este video (error $rawCode)."
    100 -> "El video fue eliminado, es privado o no está disponible (error $rawCode)."
    101, 150 -> "El propietario no permite reproducir este video dentro de otras apps (error $rawCode)."
    152 -> "YouTube indicó que este video no está disponible en el reproductor incrustado (error $rawCode). No se abrirá otra app ni se evitará la restricción."
    153 -> "YouTube rechazó la solicitud porque no recibió un Referer válido (error $rawCode)."
    else -> "YouTube no pudo reproducir este video en el visor incrustado (error $rawCode). No se abrió otra app."
}

/** Full-screen YouTube player for chat messages; the WebView is created only after the thumbnail is tapped. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatYouTubeViewerDialog(
    videoId: String,
    title: String,
    thumbnailUrl: String? = null,
    caption: String? = null,
    onDismiss: () -> Unit,
    onShare: () -> Unit = {},
    onReply: () -> Unit = {}
) {
    val context = LocalContext.current
    val videoUrl = remember(videoId) { "https://www.youtube.com/watch?v=$videoId" }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var isPlayerReady by remember(videoId) { mutableStateOf(false) }
    var hasStarted by remember(videoId) { mutableStateOf(false) }
    var playerError by remember(videoId) { mutableStateOf<String?>(null) }
    var isWebFullscreen by remember(videoId) { mutableStateOf(false) }
    var showMediaMenu by remember { mutableStateOf(false) }
    var webViewRef by remember(videoId) { mutableStateOf<WebView?>(null) }
    val posterUrl = remember(videoId, thumbnailUrl) {
        thumbnailUrl?.takeIf(String::isNotBlank) ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }
    val embedHtml = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <meta name="referrer" content="origin">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body, #player { width: 100%; height: 100%; overflow: hidden; background: #000; }
                iframe { width: 100%; height: 100%; border: 0; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                window.omniPlayer = null;
                window.onYouTubeIframeAPIReady = function() {
                    window.omniPlayer = new YT.Player('player', {
                        videoId: '$videoId',
                        playerVars: {
                            autoplay: 0,
                            playsinline: 1,
                            controls: 1,
                            rel: 0,
                            modestbranding: 1,
                            enablejsapi: 1,
                            origin: '$YOUTUBE_APP_ORIGIN'
                        },
                        events: {
                            onReady: function() { console.log('omnistudio-player-ready'); },
                            onError: function(event) { console.log('omnistudio-player-error:' + event.data); },
                            onStateChange: function(event) { console.log('omnistudio-player-state:' + event.data); }
                        }
                    });
                };
            </script>
            <script src="https://www.youtube.com/iframe_api"></script>
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(videoId, isPlayerReady, playerError) {
        delay(20_000L)
        if (!isPlayerReady && playerError == null) {
            isLoading = false
            playerError = "El reproductor incrustado de YouTube no terminó de inicializarse. Revisa la conexión o la versión de Android System WebView."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize().testTag("chat_youtube_viewer")) {
            // WebView owns the full dialog canvas; chrome floats over it instead of shrinking the player to 16:9.
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val host = FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) }
                        var customView: View? = null
                        var customViewCallback: WebChromeClient.CustomViewCallback? = null
                        val webView = WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                    val message = consoleMessage.message()
                                    when {
                                        message == "omnistudio-player-ready" -> {
                                            isPlayerReady = true
                                            isLoading = false
                                            playerError = null
                                        }
                                        message.startsWith("omnistudio-player-error:") -> {
                                            val code = message.substringAfter(':').trim().take(24).ifBlank { "desconocido" }
                                            playerError = youtubePlayerErrorMessage(code)
                                            isLoading = false
                                        }
                                        message.startsWith("omnistudio-player-state:") -> {
                                            when (message.substringAfter(':').toIntOrNull()) {
                                                1 -> { hasStarted = true; isLoading = false; playerError = null }
                                                2, 0 -> isLoading = false
                                                3 -> isLoading = true
                                            }
                                        }
                                    }
                                    return if (message.startsWith("omnistudio-player-")) true else super.onConsoleMessage(consoleMessage)
                                }

                                override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                                    if (customView != null) {
                                        callback.onCustomViewHidden()
                                        return
                                    }
                                    customView = view
                                    customViewCallback = callback
                                    isWebFullscreen = true
                                    host.addView(
                                        view,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    this@apply.visibility = View.GONE
                                }

                                override fun onHideCustomView() {
                                    customView?.let(host::removeView)
                                    customView = null
                                    this@apply.visibility = View.VISIBLE
                                    isWebFullscreen = false
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    val hostName = uri.host.orEmpty().lowercase()
                                    val path = uri.path.orEmpty()
                                    val youtubeHost = hostName == "youtube.com" || hostName.endsWith(".youtube.com")
                                    val watchPage = youtubeHost && (
                                        hostName == "m.youtube.com" || path == "/watch" ||
                                            path.startsWith("/shorts/") || path.startsWith("/live/")
                                        )
                                    if (watchPage) {
                                        playerError = "YouTube indicó que este video no está disponible en el reproductor incrustado. No se abrió m.youtube.com ni otra app."
                                        isLoading = false
                                        return true
                                    }
                                    if (uri.scheme != "https" && uri.scheme != "http") return true
                                    val appHost = YOUTUBE_APP_ORIGIN.removePrefix("https://").substringBefore('/')
                                    if (request.isForMainFrame && hostName != appHost) return true
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                }
                            }
                        }
                        host.addView(
                            webView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        webView.loadDataWithBaseURL("$YOUTUBE_APP_ORIGIN/", embedHtml, "text/html", "UTF-8", null)
                        webViewRef = webView
                        host
                    },
                    modifier = Modifier.fillMaxSize().testTag("webview_youtube_player")
                )

                if (playerError != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().background(Color.Black.copy(alpha = 0.86f)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(playerError.orEmpty(), color = Color.White, fontSize = 14.sp)
                        TextButton(
                            onClick = {
                                playerError = null
                                isPlayerReady = false
                                hasStarted = false
                                isLoading = true
                                webViewRef?.reload()
                            },
                            modifier = Modifier.testTag("btn_retry_youtube_viewer")
                        ) { Text("Reintentar", color = Color(0xFF93C5FD)) }
                    }
                } else if (!hasStarted) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = "Miniatura de $title",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().background(Color.Black)
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(enabled = isPlayerReady) {
                                hasStarted = true
                                isLoading = true
                                webViewRef?.evaluateJavascript("window.omniPlayer && window.omniPlayer.playVideo();", null)
                            }
                            .testTag("btn_start_youtube_viewer"),
                        shape = CircleShape,
                        color = Color(0xFFDC2626).copy(alpha = if (isPlayerReady) 0.95f else 0.55f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir en YouTube", tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                    }
                    if (isLoading) {
                        Text(
                            "Preparando reproductor incrustado…",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center).padding(top = 94.dp)
                        )
                    }
                } else if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.align(Alignment.Center).size(36.dp))
                }

                if (!isWebFullscreen) Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp).testTag("btn_close_youtube_viewer")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al chat", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("YouTube • Reproducción en OmniStudio", color = Color(0xFF9CA3AF), fontSize = 10.sp)
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp).testTag("btn_share_youtube_viewer")) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir video", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showMediaMenu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMediaMenu, onDismissRequest = { showMediaMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Copiar enlace") },
                                onClick = {
                                    showMediaMenu = false
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("YouTube URL", videoUrl))
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }

                if (!isWebFullscreen) Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(caption?.takeIf(String::isNotBlank) ?: title, color = Color.White, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
                        TextButton(onClick = onReply, modifier = Modifier.testTag("btn_reply_youtube_viewer")) {
                            Text("Responder", color = Color(0xFF93C5FD), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            webViewRef?.let { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            }
            webViewRef = null
        }
    }
}



