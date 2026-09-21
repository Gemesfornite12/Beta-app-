package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import com.example.data.youtube.YouTubeClient

/**
 * Reusable VideoPlayer composable for YouTube and standard video content.
 * Renders video thumbnail, badges, playback controls, and handles launching
 * the in-app overlay player or external intent.
 */
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    channelTitle: String? = null,
    thumbnailUrl: String? = null,
    showActionButtons: Boolean = true,
    onLaunchOverlay: ((videoId: String, title: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val videoId = remember(videoUrl) {
        YouTubeClient.extractVideoId(videoUrl)
    }

    // High quality thumbnail calculation
    val resolvedThumbnail = remember(thumbnailUrl, videoId) {
        if (!thumbnailUrl.isNullOrBlank()) {
            thumbnailUrl
        } else if (videoId != null) {
            "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        } else {
            "https://images.unsplash.com/photo-1611162617474-5b21e879e113?w=800&q=80"
        }
    }

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
        border = BorderStroke(1.dp, Color(0xFFFF0000).copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (videoId != null && onLaunchOverlay != null) {
                    onLaunchOverlay(videoId, title ?: "Video de YouTube")
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No se pudo abrir el video", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .testTag(if (videoId != null) "video_player_$videoId" else "video_player_card")
    ) {
        Column {
            // Thumbnail container with 16:9 aspect ratio and play button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = resolvedThumbnail,
                    contentDescription = title ?: "Miniatura de video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )

                // YouTube Platform Badge
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color(0xFFFF0000),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "YOUTUBE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // In-App Overlay indicator badge
                if (onLaunchOverlay != null && videoId != null) {
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

                // Center Play Button with Red Ripple Glow
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFF0000),
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
                                    text = "📺 $channelTitle",
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
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy Link Action
                            IconButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("YouTube Link", videoUrl)
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

                            // Open External YouTube Action
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Abrir en YouTube",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Primary Play Button (Overlay or Launch)
                            TextButton(
                                onClick = {
                                    if (videoId != null && onLaunchOverlay != null) {
                                        onLaunchOverlay(videoId, title ?: "Video de YouTube")
                                    } else {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_play_video")
                            ) {
                                Text(
                                    text = if (onLaunchOverlay != null) "Ver en Chat ▶" else "Ver ▶",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF0000),
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
 * Overlay Player Dialog for YouTube Videos.
 * Plays the YouTube video directly inside an interactive, draggable/resizable
 * modal overlay in the chat screen using a hardware-accelerated WebView iframe embed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeOverlayPlayerDialog(
    videoId: String,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlayerLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val videoUrl = "https://www.youtube.com/watch?v=$videoId"

    // Responsive embed HTML with YouTube Iframe API
    val embedHtml = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; background: #000; }
                body, html { width: 100%; height: 100%; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                .video-container { position: relative; width: 100%; height: 100%; }
                iframe { width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <div class="video-container">
                <iframe 
                    src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&fs=1&rel=0&modestbranding=1&enablejsapi=1" 
                    frameborder="0" 
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                    allowfullscreen>
                </iframe>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    Dialog(
        onDismissRequest = {
            webViewRef?.destroy()
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
                    webViewRef?.destroy()
                    onDismiss()
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.5.dp, Color(0xFFFF0000).copy(alpha = 0.6f)),
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 550.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {}
                    .testTag("youtube_overlay_player_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with title and controls
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
                                    color = Color(0xFFFF0000),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
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
                                        text = "YouTube Player • En Chat",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Open in YouTube App
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "No se pudo abrir", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = "Abrir en App",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Close Button
                                IconButton(
                                    onClick = {
                                        webViewRef?.destroy()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_close_youtube_overlay")
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

                    // Video Player Screen 16:9 WebView Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isPlayerLoading = false
                                        }
                                    }
                                    loadDataWithBaseURL(
                                        "https://www.youtube.com",
                                        embedHtml,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                    webViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isPlayerLoading) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF0000),
                                modifier = Modifier.size(36.dp)
                            )
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
                                text = "💡 Puedes interactuar con el chat mientras reproduces",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )

                            TextButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("YouTube Link", videoUrl)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar Link", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            webViewRef?.destroy()
        }
    }
}
