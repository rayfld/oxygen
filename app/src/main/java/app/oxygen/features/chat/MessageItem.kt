package app.oxygen.features.chat
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oxygen.core.model.Attachment
import app.oxygen.core.model.ChatMessage
import app.oxygen.core.model.Citation
import app.oxygen.core.model.MessageStatus
import app.oxygen.core.model.Role
import app.oxygen.core.model.ToolCall
import app.oxygen.core.util.Fmt
import app.oxygen.core.util.prettyJsonOrOriginal
import app.oxygen.OxygenApp
import app.oxygen.data.AppSettings
import app.oxygen.data.AttachmentStore
import app.oxygen.data.GenerationManager
import app.oxygen.ui.components.OxBadge
import app.oxygen.ui.components.OxBadgeTone
import app.oxygen.ui.components.OxChip
import app.oxygen.ui.components.OxDropdownItem
import app.oxygen.ui.components.OxDropdownMenu
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.OxLoadingDots
import app.oxygen.ui.components.OxMenuAction
import app.oxygen.ui.components.OxModelAvatar
import app.oxygen.ui.components.OxMultilineField
import app.oxygen.ui.components.OxTextField
import app.oxygen.ui.components.OxDialog
import app.oxygen.ui.components.oxClickable
import app.oxygen.ui.components.oxCombinedClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.LocalChatFontScale
import app.oxygen.ui.theme.OxType
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
@Composable
fun MessageItem(
    ui: ChatViewModel.UiMessage,
    settings: AppSettings,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onRetryWithModel: () -> Unit,
    onBranchFrom: () -> Unit,
    onEditSave: (String) -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onSwitchBranch: (String) -> Unit,
    onSpeakToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ui.message.role) {
        Role.USER -> UserMessage(ui, onEditSave, onDelete, onSwitchBranch, plain = settings.messageStyle == "plain", modifier)
        Role.TOOL -> ToolResultMessage(ui.message, modifier)
        else -> AssistantMessage(
            ui = ui,
            settings = settings,
            isSpeaking = isSpeaking,
            onRegenerate = onRegenerate,
            onRetryWithModel = onRetryWithModel,
            onBranchFrom = onBranchFrom,
            onContinue = onContinue,
            onDelete = onDelete,
            onSwitchBranch = onSwitchBranch,
            onSpeakToggle = onSpeakToggle,
            modifier = modifier,
        )
    }
}
@Composable
private fun ToolResultMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    val c = Ox.colors
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Spacer(Modifier.width(38.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(c.container.copy(alpha = 0.45f))
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .oxClickable {
                    clipboard.setText(AnnotatedString(message.content))
                    Toast.makeText(context, "Result copied", Toast.LENGTH_SHORT).show()
                }
                .padding(10.dp),
        ) {
            Text("Tool result", style = OxType.labelSmall, color = c.textMuted)
            Spacer(Modifier.size(4.dp))
            Text(
                message.content,
                style = OxType.codeSmall,
                color = c.textSecondary,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
@Composable
private fun UserMessage(
    ui: ChatViewModel.UiMessage,
    onEditSave: (String) -> Unit,
    onDelete: () -> Unit,
    onSwitchBranch: (String) -> Unit,
    plain: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    val message = ui.message
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scale = LocalChatFontScale.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = if (plain) Alignment.Start else Alignment.End,
    ) {
        Column(
            Modifier
                .then(if (plain) Modifier.fillMaxWidth() else Modifier.widthIn(max = 460.dp))
                .then(
                    if (plain) {
                        Modifier
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                            .background(c.userBubble)
                    },
                )
                .padding(horizontal = if (plain) 0.dp else 14.dp, vertical = if (plain) 0.dp else 10.dp),
        ) {
            if (message.content.isNotBlank()) {
                SelectionContainer {
                    Text(
                        remember(message.content, c) { app.oxygen.core.markdown.InlineMarkdown.parse(message.content, c) },
                        style = OxType.bodyLarge.copy(
                            fontSize = (16f * scale).sp,
                            lineHeight = (23f * scale).sp,
                        ),
                        color = c.text,
                    )
                }
            }
        }
        if (message.attachments.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            AttachmentList(message.attachments, compact = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BranchSwitcher(ui, onSwitchBranch)
            OxIconButton(Icons.Rounded.Edit, "Edit message", { editing = true }, size = 30.dp, iconSize = 15.dp, tint = c.textMuted)
            CopyButton(message.content)
            MessageMenu(
                actions = listOf(
                    OxMenuAction("Delete", Icons.Rounded.Delete, destructive = true) { confirmDelete = true },
                ),
            )
        }
    }
    if (editing) {
        var text by remember(message.id) { mutableStateOf(message.content) }
        OxDialog(
            title = "Edit message",
            message = "Creates a new branch from this point.",
            confirmLabel = "Save & resend",
            onConfirm = {
                editing = false
                onEditSave(text)
            },
            onDismiss = { editing = false },
            extraContent = {
                OxMultilineField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Message",
                    minHeight = 120.dp,
                )
            },
        )
    }
    if (confirmDelete) {
        OxDialog(
            title = "Delete message?",
            message = "This removes the message and everything after it on this branch.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
@Composable
private fun AssistantMessage(
    ui: ChatViewModel.UiMessage,
    settings: AppSettings,
    isSpeaking: Boolean,
    onRegenerate: () -> Unit,
    onRetryWithModel: () -> Unit,
    onBranchFrom: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onSwitchBranch: (String) -> Unit,
    onSpeakToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    val haptics = LocalHapticFeedback.current
    val message = ui.message
    var confirmDelete by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column {
            if (message.status == MessageStatus.ERROR && message.content.isBlank()) {
                ErrorBanner(
                    text = message.error ?: "Something went wrong",
                    onRetry = onRegenerate,
                    onChangeModel = onRetryWithModel,
                )
            } else {
                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        MarkdownContent(message.content, streaming = false)
                    }
                }
                if (message.status == MessageStatus.INTERRUPTED && message.finishReason == "stopped") {
                    Spacer(Modifier.size(6.dp))
                    OxBadge("Stopped", OxBadgeTone.Amber)
                }
                if (message.error != null && message.content.isNotBlank()) {
                    Spacer(Modifier.size(6.dp))
                    ErrorBanner(text = message.error, onRetry = onRegenerate, onChangeModel = onRetryWithModel)
                }
            }
            if (message.attachments.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                AttachmentList(message.attachments)
            }
            if (message.citations.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                SourcesSection(message.citations)
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BranchSwitcher(ui, onSwitchBranch)
                CopyButton(message.content)
                OxIconButton(
                    Icons.Rounded.Refresh,
                    "Regenerate",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRegenerate()
                    },
                    size = 30.dp,
                    iconSize = 15.dp,
                    tint = c.textMuted,
                )
                if (message.finishReason == "length" || message.status == MessageStatus.INTERRUPTED) {
                    OxIconButton(Icons.Rounded.Replay, "Continue generating", onContinue, size = 30.dp, iconSize = 15.dp, tint = c.textMuted)
                }
                OxIconButton(
                    if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                    if (isSpeaking) "Stop reading" else "Read aloud",
                    onSpeakToggle,
                    size = 30.dp,
                    iconSize = 15.dp,
                    tint = c.textMuted,
                )
                MessageMenu(
                    actions = listOf(
                        OxMenuAction("Retry with model", Icons.Rounded.Autorenew) { onRetryWithModel() },
                        OxMenuAction("Branch from here", Icons.Rounded.CallSplit) { onBranchFrom() },
                        OxMenuAction("Share", Icons.Rounded.Share) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.content)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share message"))
                        },
                        OxMenuAction("Details", Icons.Rounded.Info) { showInfo = true },
                        OxMenuAction("Delete", Icons.Rounded.Delete, destructive = true) { confirmDelete = true },
                    ),
                )
            }
        }
    }
    if (confirmDelete) {
        OxDialog(
            title = "Delete message?",
            message = "Replies after it on this branch are deleted too.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (showInfo) {
        MessageInfoDialog(message) { showInfo = false }
    }
}
@Composable
fun ReasoningText(text: String) {
    val c = Ox.colors
    SelectionContainer {
        Text(
            remember(text, c) { app.oxygen.core.markdown.InlineMarkdown.parse(text, c) },
            style = OxType.bodySmall,
            color = c.textSecondary,
        )
    }
}
@Composable
fun ReasoningBlock(text: String, streaming: Boolean) {
    val c = Ox.colors
    var expanded by remember { mutableStateOf(streaming) }
    LaunchedEffect(streaming) { if (!streaming) expanded = false }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "reasoningChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .oxClickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Thinking", style = OxType.label, color = c.textMuted)
            if (streaming) {
                Spacer(Modifier.width(8.dp))
                OxLoadingDots(dotSize = 3.dp)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = c.textMuted,
                modifier = Modifier.size(16.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(Modifier.padding(bottom = 6.dp)) {
                SelectionContainer {
                    Text(
                        remember(text, c) { app.oxygen.core.markdown.InlineMarkdown.parse(text, c) },
                        style = OxType.bodySmall,
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}
@Composable
fun ToolCallCard(name: String, arguments: String, running: Boolean, result: String? = null) {
    val c = Ox.colors
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.container.copy(alpha = 0.5f))
            .border(1.dp, c.border, shape)
            .oxClickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Build, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, style = OxType.codeSmall.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            Spacer(Modifier.width(8.dp))
            if (running) {
                OxLoadingDots(dotSize = 3.dp)
            } else {
                OxBadge("called", OxBadgeTone.Blue)
            }
        }
        if (arguments.isNotBlank()) {
            Text(
                prettyJsonOrOriginal(arguments),
                style = OxType.codeSmall,
                color = c.textSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (result != null) {
            Spacer(Modifier.size(6.dp))
            Text("Result", style = OxType.labelSmall, color = c.textMuted)
            Text(
                result,
                style = OxType.codeSmall,
                color = c.textSecondary,
                maxLines = if (expanded) 16 else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
@Composable
private fun SourcesSection(citations: List<Citation>) {
    val c = Ox.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sourcesChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, c.border, CircleShape)
                .oxClickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("Sources", style = OxType.labelSmall, color = c.textSecondary)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide sources" else "Show sources",
                tint = c.textMuted,
                modifier = Modifier.size(14.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(Modifier.padding(top = 8.dp)) {
                CitationsRow(citations)
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CitationsRow(citations: List<Citation>) {
    val c = Ox.colors
    val uriHandler = LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) citations else citations.take(6)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        visible.forEach { citation ->
            val host = remember(citation.url) {
                runCatching { java.net.URI(citation.url).host?.removePrefix("www.") }.getOrNull() ?: citation.url
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.dp, c.border, CircleShape)
                    .oxCombinedClickable(
                        onLongClick = {
                            clipboard.setText(AnnotatedString(citation.url))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        },
                        onClick = { uriHandler.openUri(citation.url) },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = "https://www.google.com/s2/favicons?sz=64&domain=$host",
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    citation.title?.takeIf { it.isNotBlank() } ?: host,
                    style = OxType.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
        if (!expanded && citations.size > 6) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(c.surface)
                    .border(1.dp, c.border, CircleShape)
                    .oxClickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "+${citations.size - 6} more",
                    style = OxType.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
@Composable
private fun ErrorBanner(text: String, onRetry: () -> Unit, onChangeModel: (() -> Unit)? = null) {
    val c = Ox.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.destructiveSoft)
            .border(1.dp, c.destructive.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.destructive, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = OxType.bodySmall, color = c.destructive, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            "Retry",
            style = OxType.label.copy(fontWeight = FontWeight.SemiBold),
            color = c.destructive,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .oxClickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        if (onChangeModel != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                "Change model",
                style = OxType.label.copy(fontWeight = FontWeight.SemiBold),
                color = c.destructive,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .oxClickable(onClick = onChangeModel)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
@Composable
private fun BranchSwitcher(ui: ChatViewModel.UiMessage, onSwitchBranch: (String) -> Unit) {
    val c = Ox.colors
    if (ui.siblingCount <= 1) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        OxIconButton(
            Icons.Rounded.ChevronLeft,
            "Previous version",
            enabled = ui.siblingIndex > 0,
            onClick = { onSwitchBranch(ui.siblingIds[ui.siblingIndex - 1]) },
            size = 28.dp,
            iconSize = 16.dp,
            tint = c.textMuted,
        )
        Text("${ui.siblingIndex + 1}/${ui.siblingCount}", style = OxType.labelSmall, color = c.textMuted)
        OxIconButton(
            Icons.Rounded.ChevronRight,
            "Next version",
            enabled = ui.siblingIndex < ui.siblingCount - 1,
            onClick = { onSwitchBranch(ui.siblingIds[ui.siblingIndex + 1]) },
            size = 28.dp,
            iconSize = 16.dp,
            tint = c.textMuted,
        )
    }
}
@Composable
private fun CopyButton(content: String) {
    val c = Ox.colors
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManagerCompat()
    val context = LocalContext.current
    OxIconButton(
        Icons.Rounded.ContentCopy,
        contentDescription = "Copy",
        onClick = {
            clipboard(content)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        },
        size = 30.dp,
        iconSize = 15.dp,
        tint = c.textMuted,
    )
}
@Composable
private fun LocalClipboardManagerCompat(): (String) -> Unit {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    return remember(clipboard) {
        { text: String -> clipboard.setText(AnnotatedString(text)) }
    }
}
@Composable
private fun MessageMenu(actions: List<OxMenuAction>) {
    val c = Ox.colors
    var open by remember { mutableStateOf(false) }
    Box {
        OxIconButton(Icons.Rounded.MoreHoriz, "More actions", { open = true }, size = 30.dp, iconSize = 16.dp, tint = c.textMuted)
        OxDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                OxDropdownItem(
                    action.copy(onClick = {
                        open = false
                        action.onClick()
                    }),
                )
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachmentList(attachments: List<Attachment>, compact: Boolean = false) {
    val c = Ox.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewerTarget by remember { mutableStateOf<Attachment?>(null) }
    if (compact) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, alignment = Alignment.End),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            attachments.forEach { attachment ->
                when (attachment.kind) {
                    Attachment.Kind.IMAGE -> AsyncImage(
                        model = attachmentModel(attachment),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, c.border, RoundedCornerShape(10.dp))
                            .oxClickable { viewerTarget = attachment },
                    )
                    Attachment.Kind.PDF -> Row(
                        Modifier
                            .clip(CircleShape)
                            .background(c.surface)
                            .border(1.dp, c.border, CircleShape)
                            .oxClickable { openPdf(context, attachment) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.InsertDriveFile,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("PDF", style = OxType.labelSmall, color = c.textSecondary)
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            attachments.forEach { attachment ->
                when (attachment.kind) {
                    Attachment.Kind.IMAGE -> AsyncImage(
                        model = attachmentModel(attachment),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .oxClickable { viewerTarget = attachment },
                    )
                    Attachment.Kind.PDF -> Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface.copy(alpha = 0.6f))
                            .border(1.dp, c.border, RoundedCornerShape(10.dp))
                            .oxClickable { openPdf(context, attachment) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("PDF", style = OxType.labelSmall, color = c.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            attachment.name ?: "document.pdf",
                            style = OxType.bodySmall,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp),
                        )
                    }
                }
            }
        }
    }
    viewerTarget?.let { attachment ->
        val localFile = !attachment.uri.startsWith("http") && !attachment.uri.startsWith("data:")
        ImageViewerDialog(
            model = attachmentModel(attachment),
            onDismiss = { viewerTarget = null },
            onSave = if (localFile && OxygenApp.graph.attachments.canSaveToGallery()) {
                {
                    scope.launch {
                        val saved = OxygenApp.graph.attachments.saveToGallery(attachment)
                        Toast.makeText(context, if (saved) "Saved to gallery" else "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                null
            },
            onShare = { shareAttachment(context, attachment) },
        )
    }
}
private fun openPdf(context: Context, attachment: Attachment) {
    runCatching {
        val file = File(attachment.uri)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "app.oxygen.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
private fun attachmentModel(attachment: Attachment): Any =
    if (attachment.uri.startsWith("http") || attachment.uri.startsWith("data:")) {
        attachment.uri
    } else {
        File(attachment.uri)
    }
private fun shareAttachment(context: Context, attachment: Attachment) {
    val intent = Intent(Intent.ACTION_SEND)
    if (attachment.uri.startsWith("http")) {
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, attachment.uri)
    } else {
        val file = File(attachment.uri)
        if (!file.exists()) return
        val providerUri = FileProvider.getUriForFile(context, "app.oxygen.fileprovider", file)
        intent.type = attachment.mimeType.substringBefore(';')
        intent.putExtra(Intent.EXTRA_STREAM, providerUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share image"))
}
@Composable
fun StreamingMessage(
    stream: GenerationManager.StreamState,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            val liveSteps = buildList {
                if (stream.reasoning.isNotBlank()) add(ChatViewModel.WorkStep.Thinking(stream.reasoning))
                stream.toolCalls.forEach { call ->
                    add(ChatViewModel.WorkStep.Tool(ToolCall(call.id, call.name, call.arguments), null))
                }
            }
            if (liveSteps.isNotEmpty()) {
                LiveWorkHeader(stream, liveSteps)
                Spacer(Modifier.height(8.dp))
                TraceTimeline(liveSteps, live = true)
            } else if (
                stream.phase == GenerationManager.Phase.CONNECTING ||
                stream.phase == GenerationManager.Phase.WAITING
            ) {
                var elapsed by remember(stream.startedAt) {
                    mutableStateOf(((System.currentTimeMillis() - stream.startedAt) / 1000).toInt())
                }
                LaunchedEffect(stream.startedAt) {
                    while (true) {
                        elapsed = ((System.currentTimeMillis() - stream.startedAt) / 1000).toInt()
                        delay(1000)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OxLoadingDots()
                    Spacer(Modifier.width(10.dp))
                    val base = if (stream.phase == GenerationManager.Phase.CONNECTING) "Connecting" else "Thinking"
                    Text(
                        if (elapsed >= 2) "$base ${elapsed}s" else base,
                        style = OxType.bodySmall,
                        color = c.textMuted,
                    )
                }
            }
            if (stream.content.isNotBlank()) {
                if (stream.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    SelectionContainer {
                        Text(
                            remember(stream.content, c) { app.oxygen.core.markdown.InlineMarkdown.parse(stream.content, c) },
                            style = OxType.bodyLarge,
                            color = c.textMuted,
                        )
                    }
                } else {
                    if (liveSteps.isNotEmpty()) Spacer(Modifier.size(10.dp))
                    SelectionContainer {
                        MarkdownContent(markdown = stream.content, streaming = true)
                    }
                }
            }
            stream.images.forEach { url ->
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, c.border, RoundedCornerShape(14.dp)),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (stream.error != null) {
                Spacer(Modifier.size(8.dp))
                ErrorBanner(stream.error, onRetry = {})
            }
        }
    }
}
@Composable
private fun MessageInfoDialog(message: ChatMessage, onDismiss: () -> Unit) {
    val c = Ox.colors
    OxDialog(
        title = "Message details",
        confirmLabel = "Close",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissLabel = "",
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Model", message.modelId ?: "-")
                InfoRow("Provider", message.provider ?: "auto")
                InfoRow("Prompt tokens", Fmt.tokens(message.promptTokens))
                InfoRow("Completion tokens", Fmt.tokens(message.completionTokens))
                message.cachedTokens?.takeIf { it > 0 }?.let { InfoRow("Cached tokens", Fmt.tokens(it)) }
                message.reasoningTokens?.takeIf { it > 0 }?.let { InfoRow("Reasoning tokens", Fmt.tokens(it)) }
                InfoRow("Cost", Fmt.cost(message.costUsd))
                InfoRow("Latency", Fmt.duration(message.latencyMs))
                InfoRow("Finish reason", message.finishReason ?: "-")
                message.generationId?.let { InfoRow("Generation", it) }
                InfoRow("Created", Fmt.dateTime(message.createdAt))
            }
        },
    )
}
@Composable
private fun InfoRow(label: String, value: String) {
    val c = Ox.colors
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = OxType.bodySmall, color = c.textMuted, modifier = Modifier.width(130.dp))
        Text(value, style = OxType.bodySmall, color = c.text)
    }
}
