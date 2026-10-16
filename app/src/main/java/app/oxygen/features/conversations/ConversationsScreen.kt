package app.oxygen.features.conversations
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oxygen.OxygenApp
import app.oxygen.core.util.Fmt
import app.oxygen.navigation.PendingNav
import app.oxygen.ui.components.OxDialog
import app.oxygen.ui.components.OxDropdownItem
import app.oxygen.ui.components.OxDropdownMenu
import app.oxygen.ui.components.OxEmptyState
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.OxMenuAction
import app.oxygen.ui.components.OxSearchField
import app.oxygen.ui.components.OxSnackbarHost
import app.oxygen.ui.components.OxSkeleton
import app.oxygen.ui.components.OxTextField
import app.oxygen.ui.components.oxClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import kotlinx.coroutines.launch
import java.util.Calendar
@Composable
fun ConversationsScreen(
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsage: () -> Unit,
) {
    val vm: ConversationsViewModel = viewModel(initializer = { ConversationsViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<ConversationsViewModel.Item?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationsViewModel.Item?>(null) }
    val sharedText by PendingNav.sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        PendingNav.sharedText.value = null
        val id = vm.createChat()
        OxygenApp.graph.generationManager.sendMessage(id, text, emptyList())
        onOpenChat(id)
    }
    val sharedImage by PendingNav.sharedImage.collectAsStateWithLifecycle()
    LaunchedEffect(sharedImage) {
        val uri = sharedImage ?: return@LaunchedEffect
        PendingNav.sharedImage.value = null
        val attachment = OxygenApp.graph.attachments.importImage(uri)
        val id = vm.createChat(ephemeral = true)
        if (attachment != null) {
            OxygenApp.graph.generationManager.sendMessage(id, "", listOf(attachment))
        }
        onOpenChat(id)
    }
    Scaffold(
        containerColor = Ox.colors.background,
        snackbarHost = { OxSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Oxygen", style = OxType.display, color = Ox.colors.text, modifier = Modifier.weight(1f))
                OxIconButton(Icons.Rounded.DataUsage, "Usage", onOpenUsage)
                OxIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
            if (state.items.isNotEmpty() || query.isNotBlank()) {
                OxSearchField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = "Search chats and messages",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    query.isNotBlank() -> {
                        if (results.isEmpty()) {
                            OxEmptyState(
                                icon = Icons.Rounded.DataUsage,
                                title = "No matches",
                                message = "Nothing found for \"$query\".",
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                                items(results, key = { it.conversationId + it.snippet.hashCode() }) { result ->
                                    SearchResultRow(result) { onOpenChat(result.conversationId) }
                                }
                            }
                        }
                    }
                    state.loading && state.items.isEmpty() -> {
                        Column(Modifier.padding(top = 12.dp)) {
                            repeat(7) { index ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                                    OxSkeleton(Modifier.height(14.dp).fillMaxWidth(0.4f + (index % 3) * 0.12f))
                                    Spacer(Modifier.height(7.dp))
                                    OxSkeleton(Modifier.height(11.dp).fillMaxWidth(0.65f))
                                }
                            }
                        }
                    }
                    state.items.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            OxEmptyState(
                                icon = Icons.Rounded.Add,
                                title = "No chats",
                                message = "New chats appear here.",
                            )
                        }
                    }
                    else -> {
                        val groups = remember(state.items) { groupByDate(state.items) }
                        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp)) {
                            groups.forEach { (label, groupItems) ->
                                item(key = "header-$label") { DateHeader(label) }
                                items(groupItems, key = { it.conversation.id }) { rowItem ->
                                    Box(Modifier.animateItem()) {
                                        SwipeableConversationRow(
                                            item = rowItem,
                                            onOpen = { onOpenChat(rowItem.conversation.id) },
                                            onRename = { renameTarget = rowItem },
                                            onDuplicate = {
                                                scope.launch {
                                                    OxygenApp.graph.chat.duplicateConversation(rowItem.conversation.id)?.let { newId ->
                                                        onOpenChat(newId)
                                                    }
                                                }
                                            },
                                            onDelete = { deleteTarget = rowItem },
                                            onTogglePin = { vm.togglePinned(rowItem.conversation.id, !rowItem.conversation.pinned) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                NewChatFab(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                    onClick = {
                        scope.launch {
                            val id = vm.createChat()
                            onOpenChat(id)
                        }
                    },
                )
                TempChatButton(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
                    onClick = {
                        scope.launch {
                            val id = vm.createChat(ephemeral = true)
                            onOpenChat(id)
                        }
                    },
                )
            }
        }
    }
    renameTarget?.let { target ->
        var title by remember(target.conversation.id) { mutableStateOf(target.conversation.title) }
        OxDialog(
            title = "Rename chat",
            confirmLabel = "Save",
            onConfirm = {
                vm.rename(target.conversation.id, title)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
            extraContent = {
                OxTextField(value = title, onValueChange = { title = it }, placeholder = "Chat title")
            },
        )
    }
    deleteTarget?.let { target ->
        OxDialog(
            title = "Delete \"${target.conversation.title}\"?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    val snapshot = OxygenApp.graph.chat.snapshotConversation(target.conversation.id)
                    vm.delete(target.conversation.id)
                    val result = snackbar.showSnackbar(
                        message = "Chat deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed && snapshot != null) {
                        OxygenApp.graph.chat.restoreConversation(snapshot)
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationRow(
    item: ConversationsViewModel.Item,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val c = Ox.colors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.destructiveSoft),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = c.destructive,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        Box(Modifier.fillMaxWidth().background(c.background)) {
            ConversationRow(
                item = item,
                onOpen = onOpen,
                onRename = onRename,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onTogglePin = onTogglePin,
            )
        }
    }
}
@Composable
private fun DateHeader(label: String) {
    Text(
        label,
        style = OxType.labelSmall,
        color = Ox.colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}
@Composable
private fun ConversationRow(
    item: ConversationsViewModel.Item,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val c = Ox.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .oxClickable(onClick = onOpen)
            .padding(start = 20.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.conversation.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    item.conversation.title,
                    style = OxType.body.copy(fontWeight = FontWeight.Medium),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val snippet = item.snippet
            if (!snippet.isNullOrBlank()) {
                Text(
                    snippet,
                    style = OxType.labelSmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    item.model?.shortName ?: item.conversation.modelId,
                    style = OxType.labelSmall,
                    color = c.textMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Text(
            Fmt.relativeTime(item.conversation.updatedAt),
            style = OxType.labelSmall,
            color = c.textMuted,
        )
        Box {
            OxIconButton(Icons.Rounded.MoreVert, "Options", { menuOpen = true }, size = 34.dp, iconSize = 17.dp, tint = c.textMuted)
            OxDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                OxDropdownItem(
                    OxMenuAction(
                        if (item.conversation.pinned) "Unpin" else "Pin",
                        Icons.Rounded.PushPin,
                    ) { menuOpen = false; onTogglePin() },
                )
                OxDropdownItem(OxMenuAction("Rename", Icons.Rounded.Edit) { menuOpen = false; onRename() })
                OxDropdownItem(OxMenuAction("Duplicate", Icons.Rounded.ContentCopy) { menuOpen = false; onDuplicate() })
                OxDropdownItem(OxMenuAction("Delete", Icons.Rounded.Delete, destructive = true) { menuOpen = false; onDelete() })
            }
        }
    }
}
@Composable
private fun SearchResultRow(result: ConversationsViewModel.SearchItem, onOpen: () -> Unit) {
    val c = Ox.colors
    Column(
        Modifier
            .fillMaxWidth()
            .oxClickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(result.title, style = OxType.body.copy(fontWeight = FontWeight.Medium), color = c.text, maxLines = 1)
        Text(result.snippet, style = OxType.labelSmall, color = c.textMuted, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
}
@Composable
private fun TempChatButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ox.colors
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(48.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.border, CircleShape)
            .oxClickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.VisibilityOff,
            contentDescription = "New temporary chat",
            tint = c.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
@Composable
private fun NewChatFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ox.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .shadow(10.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(c.primary)
            .oxClickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("New chat", style = OxType.button, color = c.onPrimary)
    }
}
private fun groupByDate(items: List<ConversationsViewModel.Item>): List<Pair<String, List<ConversationsViewModel.Item>>> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.timeInMillis
    val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
    val weekStart = todayStart - 7L * 24 * 60 * 60 * 1000
    val pinned = items.filter { it.conversation.pinned }
    val rest = items.filterNot { it.conversation.pinned }
    val grouped = LinkedHashMap<String, MutableList<ConversationsViewModel.Item>>()
    if (pinned.isNotEmpty()) grouped["Pinned"] = pinned.toMutableList()
    for (item in rest) {
        val label = when {
            item.conversation.updatedAt >= todayStart -> "Today"
            item.conversation.updatedAt >= yesterdayStart -> "Yesterday"
            item.conversation.updatedAt >= weekStart -> "Previous 7 days"
            else -> "Older"
        }
        grouped.getOrPut(label) { mutableListOf() }.add(item)
    }
    return grouped.map { it.key to it.value }
}
