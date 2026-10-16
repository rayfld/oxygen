package app.oxygen.features.chat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oxygen.core.model.Attachment
import app.oxygen.ui.components.OxChip
import app.oxygen.ui.components.OxDropdownItem
import app.oxygen.ui.components.OxDropdownMenu
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.OxMenuAction
import app.oxygen.ui.components.oxClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import coil3.compose.AsyncImage
import java.io.File
@Composable
fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (String) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPdf: () -> Unit,
    onVoiceClick: () -> Unit,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modelLabel: String?,
    onModelClick: () -> Unit,
    onModelLongClick: () -> Unit = {},
    webSearch: Boolean,
    onToggleWebSearch: () -> Unit,
    onWebLongClick: () -> Unit = {},
    toolsActiveCount: Int,
    toolsAvailable: Boolean,
    onToolsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    val haptics = LocalHapticFeedback.current
    val canSend = (input.isNotBlank() || attachments.isNotEmpty()) && !streaming
    Column(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
    ) {
        if (attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                items(attachments, key = { it.id }) { attachment ->
                    Box {
                        when (attachment.kind) {
                            Attachment.Kind.IMAGE -> AsyncImage(
                                model = File(attachment.uri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Attachment.Kind.PDF -> Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.container)
                                    .border(1.dp, c.border, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                                    Text("PDF", style = OxType.labelSmall, color = c.textMuted)
                                }
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(c.text.copy(alpha = 0.75f))
                                .oxClickable { onRemoveAttachment(attachment.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = c.background, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (modelLabel != null) {
                OxChip(
                    label = modelLabel,
                    icon = Icons.Rounded.KeyboardArrowDown,
                    onClick = onModelClick,
                    onLongClick = onModelLongClick,
                )
            }
            OxChip(
                label = "Web",
                icon = Icons.Rounded.Language,
                selected = webSearch,
                onClick = onToggleWebSearch,
                onLongClick = onWebLongClick,
            )
            if (toolsAvailable) {
                OxChip(
                    label = "Tools",
                    icon = Icons.Rounded.Build,
                    selected = toolsActiveCount > 0,
                    onClick = onToolsClick,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(22.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = OxType.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 7,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text("Message", style = OxType.bodyLarge, color = c.textMuted)
                        }
                        inner()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttachButton(onPickImage = onPickImage, onTakePhoto = onTakePhoto, onPickPdf = onPickPdf)
                AnimatedVisibility(
                    visible = input.isBlank() && attachments.isEmpty() && !streaming,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    OxIconButton(Icons.Rounded.Mic, "Voice input", onVoiceClick, size = 34.dp, iconSize = 18.dp, tint = c.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (streaming || canSend) c.primary else c.containerHigh)
                        .oxClickable(enabled = streaming || canSend) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (streaming) onStop() else onSend()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (streaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                        contentDescription = if (streaming) "Stop" else "Send",
                        tint = if (streaming || canSend) c.onPrimary else c.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
@Composable
private fun AttachButton(
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPdf: () -> Unit,
) {
    val c = Ox.colors
    var open by remember { mutableStateOf(false) }
    Box {
        OxIconButton(Icons.Rounded.Add, "Add attachment", { open = true }, size = 34.dp, iconSize = 20.dp, tint = c.textSecondary)
        OxDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OxDropdownItem(OxMenuAction("Photos", Icons.Rounded.Image) { open = false; onPickImage() })
            OxDropdownItem(OxMenuAction("Camera", Icons.Rounded.PhotoCamera) { open = false; onTakePhoto() })
            OxDropdownItem(OxMenuAction("Document (PDF)", Icons.AutoMirrored.Rounded.InsertDriveFile) { open = false; onPickPdf() })
        }
    }
}
