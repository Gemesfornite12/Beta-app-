package com.example.ui.screens.social

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private enum class SocialPhotoFilter(val label: String) {
    ORIGINAL("Original"), MONOCHROME("B/N"), SEPIA("Sepia")
}

private fun matrixFor(filter: SocialPhotoFilter): FloatArray? = when (filter) {
    SocialPhotoFilter.ORIGINAL -> null
    SocialPhotoFilter.MONOCHROME -> floatArrayOf(
        0.213f, 0.715f, 0.072f, 0f, 0f,
        0.213f, 0.715f, 0.072f, 0f, 0f,
        0.213f, 0.715f, 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    SocialPhotoFilter.SEPIA -> floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

private fun composeColorFilter(filter: SocialPhotoFilter): ColorFilter? =
    matrixFor(filter)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/** Applies a real still-photo filter to the saved image without cropping or changing its aspect ratio. */
private fun applyStillPhotoFilter(context: Context, uri: Uri, filter: SocialPhotoFilter): Uri {
    val values = matrixFor(filter) ?: return uri
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: error("No se pudo abrir la foto capturada.")
    val filtered = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(values))
    }
    Canvas(filtered).drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, filtered.width, filtered.height), paint)
    source.recycle()
    val output = File(context.cacheDir, "social-filter-${UUID.randomUUID()}.jpg")
    output.outputStream().use { stream ->
        check(filtered.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "No se pudo guardar la foto editada." }
    }
    filtered.recycle()
    return Uri.fromFile(output)
}

@Composable
internal fun SocialCameraDialog(
    enableAudio: Boolean,
    videoStorageAllowed: Boolean,
    onDismiss: () -> Unit,
    onCapture: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    var controllerRef by remember { mutableStateOf<LifecycleCameraController?>(null) }
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        }
    }
    var flashEnabled by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedPhoto by remember { mutableStateOf<Uri?>(null) }
    var photoFilter by remember { mutableStateOf(SocialPhotoFilter.ORIGINAL) }
    var message by remember { mutableStateOf<String?>(null) }
    val latestRecording by rememberUpdatedState(activeRecording)

    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        controllerRef = controller
        onDispose {
            latestRecording?.stop()
            controller.unbind()
            controllerRef = null
        }
    }

    val flashAvailable = controllerRef?.cameraInfo?.hasFlashUnit() == true
    val cameraExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    Dialog(
        onDismissRequest = { if (!recording) latestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color(0xFF05070D), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cámara de Social", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(if (videoMode) "Video" else "Foto", color = Color(0xFFF9A8D4), fontSize = 13.sp)
                    Button(
                        onClick = { if (!recording && capturedPhoto == null) videoMode = !videoMode },
                        enabled = !recording && capturedPhoto == null && (videoMode || videoStorageAllowed),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202A3A))
                    ) { Text("Cambiar") }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val photo = capturedPhoto
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "Vista previa de la foto",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = composeColorFilter(photoFilter)
                        )
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                            factory = { viewContext ->
                                PreviewView(viewContext).also { previewView ->
                                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                    previewView.controller = controller
                                }
                            },
                            update = { it.controller = controller }
                        )
                        if (recording) {
                            Text("● REC", color = Color.Red, modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
                        }
                        if (isCapturing) CircularProgressIndicator(color = Color(0xFFF472B6))
                    }
                }

                if (capturedPhoto != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        SocialPhotoFilter.entries.forEach { filter ->
                            Button(
                                onClick = { photoFilter = filter },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (photoFilter == filter) Color(0xFFE1306C) else Color(0xFF202A3A)
                                )
                            ) { Text(filter.label, color = Color.White) }
                        }
                    }
                    Text("Filtro aplicado a la foto · conserva su proporción", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { capturedPhoto = null; photoFilter = SocialPhotoFilter.ORIGINAL }) { Text("Repetir") }
                        Button(
                            enabled = !isCapturing,
                            onClick = {
                                val source = capturedPhoto ?: return@Button
                                isCapturing = true
                                CoroutineScope(Dispatchers.IO).launch {
                                    val output = runCatching { applyStillPhotoFilter(context, source, photoFilter) }
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        isCapturing = false
                                        output.onSuccess { onCapture(it, "image/jpeg") }
                                            .onFailure { message = it.message ?: "No se pudo aplicar el filtro." }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C))
                        ) { Text("Usar foto") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                flashEnabled = !flashEnabled
                                controller.imageCaptureFlashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                controller.cameraControl?.enableTorch(flashEnabled)
                            },
                            enabled = flashAvailable && !isCapturing,
                            colors = ButtonDefaults.buttonColors(containerColor = if (flashEnabled) Color(0xFFE1306C) else Color(0xFF202A3A))
                        ) { Text(if (flashEnabled) "Flash encendido" else "Flash apagado") }
                        IconButton(
                            onClick = {
                                if (videoMode) {
                                    if (recording) {
                                        activeRecording?.stop()
                                    } else {
                                        val values = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, "omnistudio-social-${UUID.randomUUID()}.mp4")
                                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/OmniStudio")
                                            }
                                        }
                                        val options = MediaStoreOutputOptions.Builder(
                                            context.contentResolver,
                                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                        ).setContentValues(values).build()
                                        recording = true
                                        activeRecording = controller.startRecording(
                                            options,
                                            AudioConfig.create(enableAudio),
                                            cameraExecutor
                                        ) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Finalize -> {
                                                    recording = false
                                                    activeRecording = null
                                                    if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                                                        onCapture(event.outputResults.outputUri, "video/mp4")
                                                    } else {
                                                        message = "No se pudo finalizar el video (${event.error})."
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        }
                                    }
                                } else {
                                    val photoFile = File(context.cacheDir, "social-camera-${UUID.randomUUID()}.jpg")
                                    val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                    isCapturing = true
                                    controller.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            capturedPhoto = Uri.fromFile(photoFile)
                                        }
                                        override fun onError(error: ImageCaptureException) {
                                            isCapturing = false
                                            message = error.message ?: "No se pudo tomar la foto."
                                        }
                                    })
                                }
                            },
                            enabled = !isCapturing,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Surface(color = if (recording) Color.Red else Color.White, shape = RoundedCornerShape(50), modifier = Modifier.size(54.dp)) {}
                        }
                        Button(onClick = { if (!recording) latestDismiss() }, enabled = !recording) { Text("Cerrar") }
                    }
                    if (videoMode && !enableAudio) {
                        Text("El permiso de micrófono no está disponible; el video se grabará sin sonido.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                    if (!videoStorageAllowed) {
                        Text("En Android 9 o anterior se requiere permiso de almacenamiento para grabar videos.", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
                message?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }
            }
        }
    }
}
