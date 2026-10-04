package com.example.ui.screens.chat

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun VoiceNoteMessagePlayer(url: String, caption: String) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var isLoading by remember(url) { mutableStateOf(false) }
    DisposableEffect(url) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(
            onClick = {
                val active = player
                if (active != null) {
                    runCatching {
                        if (active.isPlaying) { active.pause(); isPlaying = false }
                        else { active.start(); isPlaying = true }
                    }.onFailure { isPlaying = false }
                } else {
                    isLoading = true
                    runCatching {
                        MediaPlayer().also { created ->
                            created.setDataSource(url)
                            created.setOnPreparedListener { ready -> isLoading = false; isPlaying = true; ready.start() }
                            created.setOnCompletionListener { finished -> finished.seekTo(0); isPlaying = false }
                            created.setOnErrorListener { _, _, _ -> isLoading = false; isPlaying = false; true }
                            player = created
                            created.prepareAsync()
                        }
                    }.onFailure { isLoading = false }
                }
            },
            modifier = Modifier.testTag("msg_voice_note_play")
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar nota de voz" else "Reproducir nota de voz",
                tint = Color(0xFFA5B4FC),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (isLoading) "Cargando nota de voz…" else caption,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
    }
}
