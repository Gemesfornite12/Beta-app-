package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Inline chat file-preview routes. HTML and office archives are deliberately not opened as executable/rendered content. */
enum class ChatFilePreviewKind { PDF, TEXT, UNSUPPORTED }

fun resolveChatFilePreviewKind(fileName: String, url: String = "", contentType: String = ""): ChatFilePreviewKind {
    val mime = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    if (mime == "application/pdf") return ChatFilePreviewKind.PDF
    if (mime == "text/plain" || mime == "text/markdown" || mime == "text/x-markdown") {
        return ChatFilePreviewKind.TEXT
    }
    // If the server provides a meaningful, incompatible MIME type, do not trust a misleading suffix.
    val knownMime = mime.isNotBlank() && mime != "application/octet-stream" && mime != "binary/octet-stream"
    if (knownMime) return ChatFilePreviewKind.UNSUPPORTED

    val candidates = listOf(fileName, url.substringBefore('#').substringBefore('?').substringAfterLast('/'))
    val extension = candidates.firstNotNullOfOrNull { candidate ->
        val clean = candidate.substringBefore('#').substringBefore('?').substringAfterLast('/')
        clean.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?.lowercase(Locale.ROOT)
    }
    return when (extension) {
        "pdf" -> ChatFilePreviewKind.PDF
        "txt", "md", "markdown" -> ChatFilePreviewKind.TEXT
        else -> ChatFilePreviewKind.UNSUPPORTED
    }
}

private data class DownloadedChatPreview(val file: File, val contentType: String)

@Composable
fun ChatFilePreviewDialog(
    fileName: String,
    url: String? = null,
    document: DocumentItem? = null,
    onDismiss: () -> Unit,
    onDownload: ((url: String, title: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var downloaded by remember(url) { mutableStateOf<DownloadedChatPreview?>(null) }
    var loading by remember(url) { mutableStateOf(false) }
    var loadError by remember(url) { mutableStateOf<String?>(null) }
    var retryToken by remember(url) { mutableIntStateOf(0) }
    var fileText by remember(url) { mutableStateOf<String?>(null) }
    var textLoading by remember(url) { mutableStateOf(false) }
    var textError by remember(url) { mutableStateOf<String?>(null) }
    var mimeHint by remember(url) { mutableStateOf("") }

    val initialKind = remember(fileName, url) { resolveChatFilePreviewKind(fileName, url.orEmpty()) }
    val fetchedContentType = downloaded?.contentType.orEmpty().takeIf {
        it.substringBefore(';').trim().lowercase(Locale.ROOT) !in setOf("", "application/octet-stream", "binary/octet-stream")
    } ?: mimeHint
    val fetchedKind = remember(fileName, url, fetchedContentType) {
        resolveChatFilePreviewKind(fileName, url.orEmpty(), fetchedContentType)
    }

    LaunchedEffect(url, retryToken, document) {
        if (url.isNullOrBlank() || document != null) return@LaunchedEffect
        loading = true
        loadError = null
        downloaded = null
        mimeHint = ""
        try {
            if (initialKind == ChatFilePreviewKind.UNSUPPORTED) {
                mimeHint = withContext(Dispatchers.IO) { detectPreviewMime(url) }
                if (resolveChatFilePreviewKind(fileName, url, mimeHint) == ChatFilePreviewKind.UNSUPPORTED) return@LaunchedEffect
            }
            downloaded = withContext(Dispatchers.IO) { downloadPreviewFile(context, url) }
        } catch (error: Exception) {
            loadError = error.message?.take(180) ?: "No se pudo cargar la vista previa."
        } finally {
            loading = false
        }
    }

    DisposableEffect(downloaded?.file) {
        val currentFile = downloaded?.file
        onDispose { currentFile?.delete() }
    }

    LaunchedEffect(downloaded?.file, fetchedKind) {
        val file = downloaded?.file ?: return@LaunchedEffect
        if (fetchedKind != ChatFilePreviewKind.TEXT) return@LaunchedEffect
        textLoading = true
        textError = null
        fileText = null
        try {
            fileText = withContext(Dispatchers.IO) {
                file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText().take(MAX_TEXT_CHARS) }
            }
        } catch (error: Exception) {
            textError = error.message?.take(180) ?: "No se pudo leer el texto."
        } finally {
            textLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().testTag("chat_file_preview"),
            color = Color(0xFF0B1220)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111827))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_chat_file_preview_close")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar vista previa", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(fileName.ifBlank { "Archivo adjunto" }, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = when {
                                document != null -> "Documento de OmniStudio"
                                fetchedKind == ChatFilePreviewKind.PDF -> "Vista previa PDF"
                                fetchedKind == ChatFilePreviewKind.TEXT -> "Vista previa de texto"
                                else -> "Detalles del archivo"
                            },
                            color = Color(0xFF94A3B8), fontSize = 11.sp
                        )
                    }
                    if (!url.isNullOrBlank() && onDownload != null) {
                        IconButton(onClick = { onDownload(url, fileName) }, modifier = Modifier.testTag("btn_chat_file_preview_download")) {
                            Icon(Icons.Default.Download, contentDescription = "Descargar archivo", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                when {
                    document != null -> DocumentContentPreview(document)
                    fetchedKind == ChatFilePreviewKind.PDF -> {
                        when {
                            loading -> PreviewLoading("Cargando PDF…")
                            loadError != null -> PreviewError(loadError.orEmpty(), onRetry = { retryToken++ })
                            downloaded?.file != null -> PdfFilePreview(downloaded!!.file)
                            else -> PreviewLoading("Preparando PDF…")
                        }
                    }
                    fetchedKind == ChatFilePreviewKind.TEXT -> {
                        when {
                            loading || textLoading -> PreviewLoading("Cargando archivo de texto…")
                            loadError != null -> PreviewError(loadError.orEmpty(), onRetry = { retryToken++ })
                            textError != null -> PreviewError(textError.orEmpty(), onRetry = { retryToken++ })
                            fileText != null -> TextFilePreview(fileText.orEmpty(), wasTruncated = fileText!!.length >= MAX_TEXT_CHARS)
                            else -> PreviewLoading("Preparando vista previa…")
                        }
                    }
                    else -> UnsupportedFileDetails(
                        fileName = fileName,
                        url = url,
                        onDownload = if (!url.isNullOrBlank() && onDownload != null) ({ onDownload(url, fileName) }) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentContentPreview(document: DocumentItem) {
    val content = remember(document.content, document.slidesJson, document.docType) {
        if (document.docType != DocumentType.SLIDE || document.content.isNotBlank()) {
            document.content
        } else {
            runCatching {
                val slides = JSONArray(document.slidesJson)
                buildString {
                    for (index in 0 until slides.length()) {
                        val slide = slides.optJSONObject(index) ?: continue
                        append("Diapositiva ${index + 1}\n")
                        append(slide.optString("title").takeIf(String::isNotBlank).orEmpty())
                        val subtitle = slide.optString("subtitle").takeIf(String::isNotBlank)
                        if (subtitle != null) append("\n$subtitle")
                        val table = slide.optString("tableData").takeIf(String::isNotBlank)
                        if (table != null) append("\n$table")
                        append("\n\n")
                    }
                }
            }.getOrDefault("")
        }
    }
    if (content.isBlank()) {
        UnsupportedFileDetails(fileName = document.title, url = null, onDownload = null, note = "Este documento no tiene texto guardado para mostrar aquí.")
    } else {
        TextFilePreview(content, wasTruncated = false, tag = "chat_file_preview_document")
    }
}

@Composable
private fun TextFilePreview(text: String, wasTruncated: Boolean, tag: String = "chat_file_preview_text") {
    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag(tag)
    ) {
        if (wasTruncated) {
            Text("Vista parcial: el archivo supera el límite de lectura de 1 MB.", color = Color(0xFFFBBF24), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp)
        }
        Text(
            text = text,
            color = Color(0xFFE2E8F0),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)
        )
    }
}

@Composable
private fun PdfFilePreview(file: File) {
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var rendered by remember(file, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(file, pageIndex) { mutableStateOf<String?>(null) }

    LaunchedEffect(file, pageIndex) {
        renderError = null
        rendered = null
        try {
            val result = withContext(Dispatchers.IO) {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(descriptor).use { renderer ->
                    val count = renderer.pageCount
                    require(count > 0) { "El PDF no contiene páginas." }
                    val safeIndex = pageIndex.coerceIn(0, count - 1)
                    renderer.openPage(safeIndex).use { page ->
                        val width = 1400
                        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Pair(bitmap, count)
                    }
                }
            }
            rendered = result.first
            pageCount = result.second
            if (pageIndex >= result.second) pageIndex = result.second - 1
        } catch (error: Exception) {
            renderError = error.message?.take(180) ?: "No se pudo mostrar este PDF."
        }
    }

    DisposableEffect(rendered) {
        val bitmap = rendered
        onDispose { bitmap?.recycle() }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().testTag("chat_file_preview_pdf")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                Icon(Icons.Default.NavigateBefore, contentDescription = "Página anterior", tint = if (pageIndex > 0) Color.White else Color.Gray)
            }
            Text(if (pageCount > 0) "Página ${pageIndex + 1} de $pageCount" else "PDF", color = Color.White, fontSize = 13.sp)
            IconButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageCount > 0 && pageIndex < pageCount - 1) {
                Icon(Icons.Default.NavigateNext, contentDescription = "Página siguiente", tint = if (pageCount > 0 && pageIndex < pageCount - 1) Color.White else Color.Gray)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                renderError != null -> Text("No se pudo abrir el PDF: ${renderError.orEmpty()}", color = Color(0xFFFCA5A5), modifier = Modifier.padding(24.dp))
                rendered == null -> CircularProgressIndicator(color = Color(0xFF38BDF8))
                else -> Image(
                    bitmap = rendered!!.asImageBitmap(),
                    contentDescription = "Página ${pageIndex + 1} del PDF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun UnsupportedFileDetails(fileName: String, url: String?, onDownload: (() -> Unit)?, note: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp).testTag("chat_file_preview_unsupported"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(16.dp))
        Text(fileName.ifBlank { "Archivo adjunto" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(note ?: "Este formato no tiene un visor integrado. El archivo no se abrirá en otra aplicación.", color = Color(0xFFCBD5E1), fontSize = 14.sp)
        if (url != null) {
            Spacer(Modifier.height(8.dp))
            Text("Puedes descargarlo desde aquí cuando lo necesites.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        if (onDownload != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onDownload, modifier = Modifier.testTag("btn_chat_file_preview_download_details")) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("  Descargar archivo")
            }
        }
    }
}

@Composable
private fun PreviewLoading(label: String) {
    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF38BDF8))
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color(0xFFCBD5E1))
        }
    }
}

@Composable
private fun PreviewError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color(0xFFFCA5A5))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Reintentar vista previa") }
    }
}

private fun detectPreviewMime(url: String): String {
    val parsedUrl = runCatching { URL(url) }.getOrNull() ?: return ""
    if (parsedUrl.protocol != "https" && parsedUrl.protocol != "http") return ""
    val connection = runCatching { parsedUrl.openConnection() as HttpURLConnection }.getOrNull() ?: return ""
    return try {
        connection.instanceFollowRedirects = true
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 OmniStudio")
        connection.connect()
        if (connection.responseCode !in 200..299) return ""
        connection.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
    } catch (_: Exception) {
        ""
    } finally {
        connection.disconnect()
    }
}

private suspend fun downloadPreviewFile(context: Context, url: String): DownloadedChatPreview {
    val parsedUrl = URL(url)
    require(parsedUrl.protocol == "https" || parsedUrl.protocol == "http") { "Este enlace no admite vista previa segura." }
    val connection = parsedUrl.openConnection() as HttpURLConnection
    var output: File? = null
    try {
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 OmniStudio")
        connection.connect()
        val status = connection.responseCode
        require(status in 200..299) { "El servidor respondió con HTTP $status." }
        val declaredSize = connection.contentLengthLong
        require(declaredSize <= MAX_PREVIEW_BYTES) { "El archivo supera el límite de vista previa de 20 MB." }
        val targetFile = File.createTempFile("chat-preview-", ".bin", context.cacheDir)
        output = targetFile
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(targetFile).use { sink ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_PREVIEW_BYTES) { "El archivo supera el límite de vista previa de 20 MB." }
                    sink.write(buffer, 0, read)
                }
            }
        }
        return DownloadedChatPreview(targetFile, connection.contentType.orEmpty())
    } catch (error: Exception) {
        output?.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

private const val MAX_PREVIEW_BYTES = 20L * 1024L * 1024L
private const val MAX_TEXT_CHARS = 1_000_000
