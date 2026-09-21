package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ChatMessage

/**
 * Paleta de Emojis Principales y Categorías Completas para Reacciones
 */
private val QUICK_REACTION_EMOJIS: List<String> = listOf(
    "👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "🎉", "👏", "🚀", "💯", "✨"
)

private val EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    Pair("Populares", listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "🎉", "👏", "🚀", "💯", "✨", "👀", "🥳", "🙌", "😍")),
    Pair("Caritas", listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "☺️", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😓", "😭", "😱", "😖", "😣", "😞", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬")),
    Pair("Gestos", listOf("👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤙", "💪", "🦾", "✍️", "🙏", "🤝", "👏", "🙌", "👐", "🤲")),
    Pair("Corazones", listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "💌", "💋", "🫂")),
    Pair("Celebración", listOf("🔥", "⚡", "⭐", "🌟", "✨", "💥", "💯", "💢", "💨", "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🥈", "🥉", "🎯", "🚀", "🛸", "💡", "💎", "👑", "🦄", "🎵", "🎶", "🎧", "🎙️", "🍾", "🍻", "🥂", "🍕", "🍔", "🍿", "☕"))
)

/**
 * Diálogo flotante de menú contextual activado por Long-Press en un mensaje del chat.
 * Ofrece la barra de reacciones emoji interactivas, catálogo expandible, y acciones de mensaje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageReactionMenuDialog(
    message: ChatMessage,
    isMe: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopyText: () -> Unit,
    onReply: () -> Unit,
    onShowDeliveryStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showAllEmojis by remember { mutableStateOf(false) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    // Parsear reacciones actuales en el mensaje
    val currentReactions = remember(message.reactions) {
        if (message.reactions.isBlank()) emptyList()
        else message.reactions.split(",").filter { it.isNotBlank() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .testTag("dialog_message_reaction_menu")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Encabezado con título e info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF6366F1).copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                                        contentDescription = null,
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Reaccionar al mensaje",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "De: ${message.senderName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // BARRA RÁPIDA DE EMOJIS (Floating Reaction Bar)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mostrar los primeros 6 emojis más frecuentes con animación de pulsación
                            QUICK_REACTION_EMOJIS.take(6).forEach { emoji ->
                                val isSelected = currentReactions.contains(emoji)
                                EmojiReactionButton(
                                    emoji = emoji,
                                    isSelected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onReact(emoji)
                                        onDismiss()
                                    }
                                )
                            }

                            // Botón para desplegar todos los emojis
                            Surface(
                                shape = CircleShape,
                                color = if (showAllEmojis) Color(0xFF4F46E5) else Color(0xFF334155),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clickable { showAllEmojis = !showAllEmojis }
                                    .testTag("btn_expand_all_emojis")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (showAllEmojis) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                                        contentDescription = "Ver más emojis",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // VISTA EXPANDIDA DE EMOJIS POR CATEGORÍA
                    AnimatedVisibility(
                        visible = showAllEmojis,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            // Pestañas de categorías
                            ScrollableTabRow(
                                selectedTabIndex = selectedCategoryIndex,
                                edgePadding = 4.dp,
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                EMOJI_CATEGORIES.forEachIndexed { index, pair ->
                                    val catName = pair.first
                                    Tab(
                                        selected = selectedCategoryIndex == index,
                                        onClick = { selectedCategoryIndex = index },
                                        text = {
                                            Text(
                                                text = catName,
                                                fontSize = 12.sp,
                                                fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedCategoryIndex == index) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Grid / FlowRow de Emojis seleccionables
                            val currentCategoryEmojis: List<String> = EMOJI_CATEGORIES.getOrNull(selectedCategoryIndex)?.second ?: emptyList()
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                            ) {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    currentCategoryEmojis.forEach { emoji ->
                                        val isSelected = currentReactions.contains(emoji)
                                        EmojiReactionButton(
                                            emoji = emoji,
                                            isSelected = isSelected,
                                            size = 36,
                                            fontSize = 18,
                                            onClick = {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                onReact(emoji)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // RESUMEN / VISTA PREVIA DEL MENSAJE SELECCIONADO
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(36.dp)
                                    .background(Color(0xFF38BDF8), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.senderName,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (message.text.isNotBlank()) message.text else "Mensaje multimedia",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // REACCIONES ACTIVAS (si tiene)
                    if (currentReactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reacciones actuales:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                currentReactions.distinct().forEach { emoji ->
                                    val count = currentReactions.count { it == emoji }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF334155),
                                        border = BorderStroke(1.dp, Color(0xFF475569)),
                                        modifier = Modifier
                                            .clickable {
                                                onReact(emoji)
                                                onDismiss()
                                            }
                                    ) {
                                        Text(
                                            text = if (count > 1) "$emoji $count" else emoji,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(10.dp))

                    // ACCIONES CONTEXTUALES DEL MENSAJE
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Copiar texto
                        if (message.text.isNotBlank()) {
                            ContextMenuItem(
                                icon = Icons.Default.ContentCopy,
                                title = "Copiar texto",
                                subtitle = "Copia el contenido al portapapeles",
                                tint = Color(0xFF38BDF8),
                                onClick = {
                                    onCopyText()
                                    onDismiss()
                                }
                            )
                        }

                        // 2. Responder / Citar
                        ContextMenuItem(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            title = "Responder",
                            subtitle = "Citar mensaje en el campo de texto",
                            tint = Color(0xFF818CF8),
                            onClick = {
                                onReply()
                                onDismiss()
                            }
                        )

                        // 3. Ver Estado de Entrega (Enviado, Entregado, Visto)
                        ContextMenuItem(
                            icon = Icons.Default.Info,
                            title = "Detalles del mensaje",
                            subtitle = "Estado en tiempo real y marcas de tiempo",
                            tint = Color(0xFF34D399),
                            onClick = {
                                onShowDeliveryStatus()
                                onDismiss()
                            }
                        )

                        // 4. Eliminar mensaje (si soy el remitente)
                        if (isMe) {
                            ContextMenuItem(
                                icon = Icons.Default.Delete,
                                title = "Eliminar mensaje",
                                subtitle = "Remover de la conversación",
                                tint = Color(0xFFF87171),
                                isDestructive = true,
                                onClick = {
                                    onDelete()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Botón individual de emoji con escala interactiva y resaltado
 */
@Composable
fun EmojiReactionButton(
    emoji: String,
    isSelected: Boolean,
    size: Int = 42,
    fontSize: Int = 22,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.35f else if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "emojiScale"
    )

    Surface(
        shape = CircleShape,
        color = if (isSelected) Color(0xFF4F46E5).copy(alpha = 0.35f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF818CF8)) else null,
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("btn_reaction_emoji_$emoji")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = emoji,
                fontSize = fontSize.sp
            )
        }
    }
}

/**
 * Item de acción contextual dentro del menú de mensaje
 */
@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tint.copy(alpha = 0.15f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isDestructive) Color(0xFFF87171) else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Insignia visual interactiva de Reacciones en la burbuja de mensaje
 */
@Composable
fun MessageReactionsRow(
    reactionsString: String,
    isMe: Boolean,
    onReactClick: (String) -> Unit,
    onOpenReactionMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reactionsList = remember(reactionsString) {
        if (reactionsString.isBlank()) emptyList()
        else reactionsString.split(",").filter { it.isNotBlank() }
    }

    if (reactionsList.isEmpty()) return

    val distinctEmojis = remember(reactionsList) { reactionsList.distinct() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Píldoras de reacciones agregadas
        distinctEmojis.forEach { emoji ->
            val count = reactionsList.count { it == emoji }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF475569)),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onReactClick(emoji) }
                    .testTag("reaction_badge_$emoji")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 12.sp)
                    if (count > 1) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = count.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }

        // Botón pequeño para añadir otra reacción
        Surface(
            shape = CircleShape,
            color = Color(0xFF334155).copy(alpha = 0.7f),
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenReactionMenu)
                .testTag("btn_add_reaction_pill")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir reacción",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
