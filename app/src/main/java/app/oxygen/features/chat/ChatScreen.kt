package app.oxygen.features.chat
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.ChatParams
import app.oxygen.core.model.Role
import app.oxygen.data.Preset
import app.oxygen.ui.components.OxBadge
import app.oxygen.ui.components.OxBadgeTone
import app.oxygen.ui.components.OxButton
import app.oxygen.ui.components.OxButtonVariant
import app.oxygen.ui.components.OxChip
import app.oxygen.ui.components.OxDialog
import app.oxygen.ui.components.OxDropdownItem
import app.oxygen.ui.components.OxDropdownMenu
import app.oxygen.ui.components.OxEmptyState
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.OxMenuAction
import app.oxygen.ui.components.OxNumberField
import app.oxygen.ui.components.OxSnackbarHost
import app.oxygen.ui.components.OxSearchField
import app.oxygen.ui.components.OxTextField
import app.oxygen.ui.components.OxTopBar
import app.oxygen.ui.components.oxClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val vm: ChatViewModel = viewModel(key = "chat-$conversationId", initializer = { ChatViewModel(conversationId) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val catalog by OxygenApp.graph.models.catalogFlow.collectAsStateWithLifecycle()
    val catalogLoading by OxygenApp.graph.models.loading.collectAsStateWithLifecycle()
    val catalogError by OxygenApp.graph.models.lastError.collectAsStateWithLifecycle()
    val ephemeral = state.conversation?.ephemeral == true
    BackHandler(enabled = ephemeral) { vm.deleteConversation { onBack() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var follow by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }
    var fallbackPickMode by remember { mutableStateOf(false) }
    var retryPickMessageId by remember { mutableStateOf<String?>(null) }
    var showTools by remember { mutableStateOf(false) }
    var showWebSettings by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val toolsAvailable = state.currentModel?.let { "text" in it.outputModalities } ?: true
    var detailModel by remember { mutableStateOf<ChatModel?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPresetName by remember { mutableStateOf(false) }
    var speakingId by remember { mutableStateOf<String?>(null) }
    val tts = remember { TtsController(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val isStreaming = state.streaming != null
    DisposableEffect(isStreaming) {
        val window = (context as? android.app.Activity)?.window
        if (isStreaming) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(Unit) { onDispose { vm.persistDraft() } }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach(vm::addImage)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::addPdf)
    }
    val capturedUri = remember { mutableStateOf<Uri?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) capturedUri.value?.let(vm::addImage)
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            vm.setInput(if (input.isBlank()) spoken else "$input $spoken")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            val markdown = buildMarkdownExport(state.conversation?.title ?: "Chat", state.messages)
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(markdown.toByteArray()) }
                }
            }
        }
    }
    fun takePhoto() {
        val file = File(context.cacheDir, "images/captured_${System.currentTimeMillis()}.jpg").apply {
            parentFile?.mkdirs()
        }
        val uri = FileProvider.getUriForFile(context, "app.oxygen.fileprovider", file)
        capturedUri.value = uri
        cameraPicker.launch(uri)
    }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val totalItems = state.items.size + if (state.streaming != null) 1 else 0
    LaunchedEffect(state.messages.size, state.streaming?.content?.length, state.streaming?.reasoning?.length) {
        if (follow && totalItems > 0) {
            listState.scrollToItem(totalItems - 1, scrollOffset = 100_000)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canForward) -> if (scrolling) follow = !canForward }
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && follow && totalItems > 0) {
            listState.scrollToItem(totalItems - 1, scrollOffset = 100_000)
        }
    }
    LaunchedEffect(state.conversation) {
        if (state.conversation == null) {
            delay(1500)
            if (state.conversation == null) onBack()
        }
    }
    val renderMessage: @Composable (ChatViewModel.UiMessage) -> Unit = { ui ->
        MessageItem(
            ui = ui,
            settings = state.settings,
            isSpeaking = speakingId == ui.message.id,
            onRegenerate = { vm.regenerate(ui.message.id) },
            onRetryWithModel = {
                retryPickMessageId = ui.message.id
                showPicker = true
            },
            onBranchFrom = {
                vm.forkFrom(ui.message.id) { newId -> onOpenChat(newId) }
            },
            onEditSave = { text -> vm.editMessage(ui.message.id, text) },
            onContinue = { vm.continueMessage(ui.message.id) },
            onDelete = { vm.deleteMessage(ui.message.id) },
            onSwitchBranch = { siblingId -> vm.switchBranch(siblingId) },
            onSpeakToggle = {
                if (speakingId == ui.message.id) {
                    tts.stop()
                    speakingId = null
                } else {
                    speakingId = ui.message.id
                    tts.speak(markdownToPlainText(ui.message.content))
                }
            },
        )
    }
    Scaffold(
        containerColor = Ox.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { OxSnackbarHost(snackbar) },
        topBar = {
            ChatTopBar(
                title = state.conversation?.title ?: "Chat",
                subtitle = null,
                ephemeral = ephemeral,
                onBack = { if (ephemeral) vm.deleteConversation { onBack() } else onBack() },
                onRename = { showRename = true },
                onParams = { showParams = true },
                onShare = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            buildMarkdownExport(state.conversation?.title ?: "Chat", state.messages),
                        )
                    }
                    context.startActivity(Intent.createChooser(intent, "Share chat"))
                },
                onExport = {
                    exportLauncher.launch((state.conversation?.title ?: "chat").replace(Regex("[^\\w\\- ]"), "").trim() + ".md")
                },
                onDelete = { showDelete = true },
                onSaveChat = {
                    vm.saveTemporaryChat()
                    scope.launch { snackbar.showSnackbar("Saved to chats") }
                },
                searchActive = searchOpen,
                onSearchToggle = {
                    searchOpen = !searchOpen
                    if (!searchOpen) searchQuery = ""
                },
                onRegenerateTitle = {
                    vm.regenerateTitle()
                    scope.launch { snackbar.showSnackbar("Regenerating title") }
                },
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchOpen) {
                OxSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search in chat",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty() && state.streaming == null) {
                    EmptyChat(
                        hasKey = state.hasKey,
                        onOpenSettings = onOpenSettings,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val searching = searchOpen && searchQuery.isNotBlank()
                        if (searching) {
                            val visible = state.messages.filter { it.message.content.contains(searchQuery, ignoreCase = true) }
                            items(
                                items = visible,
                                key = { it.message.id },
                                contentType = { it.message.role.name },
                            ) { ui ->
                                Box(contentAlignment = Alignment.Center) {
                                    Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                                        renderMessage(ui)
                                    }
                                }
                            }
                        } else {
                            state.items.forEach { row ->
                                when (row) {
                                    is ChatViewModel.ChatListItem.Single -> item(key = "m-${row.ui.message.id}") {
                                        Box(contentAlignment = Alignment.Center) {
                                            Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                                                renderMessage(row.ui)
                                            }
                                        }
                                    }
                                    is ChatViewModel.ChatListItem.Work -> item(key = "w-${row.id}") {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                                                ActivityTrace(row)
                                                row.finalUi?.let { final -> renderMessage(final) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.streaming?.let { stream ->
                            val searching = searchOpen && searchQuery.isNotBlank()
                            if (!searching) {
                                item(key = "m-${stream.messageId}") {
                                    Box(contentAlignment = Alignment.Center) {
                                        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                                            StreamingMessage(stream)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ScrollToBottomButton(
                    visible = !atBottom,
                    streaming = isStreaming,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    onClick = {
                        follow = true
                        scope.launch { listState.scrollToItem(totalItems - 1, scrollOffset = 100_000) }
                    },
                )
            }
            ChatComposer(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom),
                ),
                input = input,
                onInputChange = vm::setInput,
                attachments = attachments,
                onRemoveAttachment = vm::removeAttachment,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onTakePhoto = { takePhoto() },
                onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                onVoiceClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message…")
                    }
                    runCatching { voiceLauncher.launch(intent) }
                },
                streaming = state.streaming != null,
                onSend = { vm.send() },
                onStop = { vm.stop() },
                modelLabel = state.currentModel?.shortName,
                onModelClick = { showPicker = true },
                onModelLongClick = { showParams = true },
                webSearch = state.conversation?.params?.let { params ->
                    app.oxygen.core.tools.Tools.byId(app.oxygen.core.tools.Tools.WEB_SEARCH_ID)?.let { spec ->
                        toolExecutor(params, spec) != null
                    }
                } == true,
                onToggleWebSearch = {
                    state.conversation?.let { conversation ->
                        val params = conversation.params
                        val spec = app.oxygen.core.tools.Tools.byId(app.oxygen.core.tools.Tools.WEB_SEARCH_ID) ?: return@let
                        val active = toolExecutor(params, spec) != null
                        val updated = if (active) {
                            params.copy(
                                tools = params.tools - spec.id,
                                serverTools = params.serverTools.filterNot { it == spec.serverId },
                                webSearch = null,
                            )
                        } else {
                            params.copy(
                                tools = params.tools + (spec.id to app.oxygen.core.tools.Tools.EXEC_SERVER),
                                serverTools = params.serverTools.filterNot { it == spec.serverId },
                                webSearch = null,
                            )
                        }
                        vm.setParams(updated)
                    }
                },
                toolsActiveCount = state.conversation?.params?.let { enabledToolCount(it) } ?: 0,
                toolsAvailable = toolsAvailable,
                onToolsClick = { showTools = true },
                onWebLongClick = { showWebSettings = true },
            )
        }
    }
    if (showPicker) {
        ModelPickerSheet(
            models = catalog,
            currentModelId = state.conversation?.modelId,
            favorites = state.settings.favoriteModelIds,
            recents = state.settings.lastModelIds,
            title = when {
                retryPickMessageId != null -> "Retry with model"
                fallbackPickMode -> "Add fallback model"
                else -> "Choose a model"
            },
            onSelect = { modelId ->
                when {
                    retryPickMessageId != null -> {
                        vm.regenerate(retryPickMessageId!!, modelId)
                        retryPickMessageId = null
                    }
                    fallbackPickMode -> {
                        state.conversation?.let { conversation ->
                            vm.setParams(conversation.params.copy(fallbackModels = (conversation.params.fallbackModels + modelId).distinct()))
                        }
                    }
                    else -> vm.setModel(modelId)
                }
                showPicker = false
                fallbackPickMode = false
            },
            onToggleFavorite = { modelId ->
                scope.launch { OxygenApp.graph.settings.toggleFavoriteModel(modelId) }
            },
            onDetails = { model ->
                showPicker = false
                detailModel = model
            },
            onDismiss = {
                showPicker = false
                fallbackPickMode = false
                retryPickMessageId = null
            },
            loading = catalogLoading,
            error = catalogError,
            onRetry = {
                scope.launch { OxygenApp.graph.models.retry(OxygenApp.graph.settings.activeKeySecret()) }
            },
        )
    }
    detailModel?.let { model ->
        EndpointSheet(
            model = model,
            onDismiss = { detailModel = null },
        )
    }
    if (showParams) {
        val conversation = state.conversation
        ParamsSheet(
            model = state.currentModel,
            params = conversation?.params ?: ChatParams(),
            systemPrompt = conversation?.systemPrompt.orEmpty(),
            onSave = { params, systemPrompt ->
                vm.setParams(params)
                vm.setSystemPrompt(systemPrompt)
                showParams = false
            },
            onPickFallback = {
                fallbackPickMode = true
                showPicker = true
            },
            onSaveAsPreset = { showPresetName = true },
            toolsAvailable = toolsAvailable,
            onOpenTools = {
                showParams = false
                showTools = true
            },
            onDismiss = { showParams = false },
        )
    }
    if (showTools) {
        ToolsSheet(
            params = state.conversation?.params ?: ChatParams(),
            toolsAvailable = toolsAvailable,
            onChange = { vm.setParams(it) },
            onDismiss = { showTools = false },
        )
    }
    if (showWebSettings) {
        var maxResults by remember { mutableStateOf(state.conversation?.params?.webSearchMaxResults?.toString().orEmpty()) }
        var searchPrompt by remember { mutableStateOf(state.conversation?.params?.webSearchPrompt.orEmpty()) }
        OxDialog(
            title = "Web search",
            message = "Applies to the web search tool for this chat.",
            confirmLabel = "Save",
            onConfirm = {
                state.conversation?.let { conversation ->
                    vm.setParams(
                        conversation.params.copy(
                            webSearchMaxResults = maxResults.toIntOrNull(),
                            webSearchPrompt = searchPrompt.takeIf { it.isNotBlank() },
                        ),
                    )
                }
                showWebSettings = false
            },
            onDismiss = { showWebSettings = false },
            extraContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OxTextField(
                        value = searchPrompt,
                        onValueChange = { searchPrompt = it },
                        placeholder = "Search prompt (optional)",
                    )
                    OxNumberField(
                        value = maxResults,
                        onValueChange = { maxResults = it },
                        placeholder = "Max results (auto)",
                    )
                }
            },
        )
    }
    if (showRename) {
        var title by remember { mutableStateOf(state.conversation?.title.orEmpty()) }
        OxDialog(
            title = "Rename chat",
            confirmLabel = "Save",
            onConfirm = {
                vm.rename(title)
                showRename = false
            },
            onDismiss = { showRename = false },
            extraContent = {
                OxTextField(value = title, onValueChange = { title = it }, placeholder = "Chat title")
            },
        )
    }
    if (showDelete) {
        OxDialog(
            title = "Delete this chat?",
            message = "This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                showDelete = false
                vm.deleteConversation { onBack() }
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showPresetName) {
        var name by remember { mutableStateOf(state.currentModel?.shortName?.plus(" preset") ?: "Preset") }
        OxDialog(
            title = "Save as preset",
            message = "Saves the current model, parameters and system prompt for reuse.",
            confirmLabel = "Save",
            onConfirm = {
                scope.launch {
                    val conversation = state.conversation
                    OxygenApp.graph.presets.save(
                        Preset(
                            id = "",
                            name = name.trim().ifBlank { "Preset" },
                            systemPrompt = conversation?.systemPrompt,
                            modelId = conversation?.modelId,
                            params = conversation?.params ?: ChatParams(),
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    snackbar.showSnackbar("Preset saved")
                }
                showPresetName = false
            },
            onDismiss = { showPresetName = false },
            extraContent = {
                OxTextField(value = name, onValueChange = { name = it }, placeholder = "Preset name")
            },
        )
    }
}
@Composable
private fun ScrollToBottomButton(
    visible: Boolean,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ox.colors.surface)
                .border(1.dp, Ox.colors.border, RoundedCornerShape(18.dp))
                .oxClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = "Scroll to bottom",
                tint = Ox.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            if (streaming) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Ox.colors.link),
                )
            }
        }
    }
}
@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String?,
    ephemeral: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onParams: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onSaveChat: () -> Unit,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
    onRegenerateTitle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    OxTopBar(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        titleContent = {
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .oxClickable(onClick = onRename)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(title, style = OxType.title, color = Ox.colors.text, maxLines = 1)
            }
        },
        actions = {
            if (ephemeral) {
                OxIconButton(Icons.Rounded.Bookmark, "Save chat", onSaveChat)
            }
            OxIconButton(
                if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                "Search in chat",
                onSearchToggle,
            )
            OxIconButton(Icons.Rounded.Tune, "Parameters", onParams)
            Box {
                OxIconButton(Icons.Rounded.MoreVert, "More", { menuOpen = true })
                OxDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    OxDropdownItem(OxMenuAction("Share chat", Icons.Rounded.Share) { menuOpen = false; onShare() })
                    OxDropdownItem(OxMenuAction("Export as Markdown", Icons.Rounded.Download) { menuOpen = false; onExport() })
                    OxDropdownItem(OxMenuAction("Regenerate title", Icons.Rounded.Refresh) { menuOpen = false; onRegenerateTitle() })
                    OxDropdownItem(OxMenuAction("OpenRouter settings", Icons.Rounded.VpnKey) { menuOpen = false; onOpenSettings() })
                    OxDropdownItem(OxMenuAction("Delete chat", Icons.Rounded.Delete, destructive = true) { menuOpen = false; onDelete() })
                }
            }
        },
    )
}
@Composable
private fun EmptyChat(
    hasKey: Boolean,
    onOpenSettings: () -> Unit,
) {
    val c = Ox.colors
    if (!hasKey) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OxEmptyState(
                icon = Icons.Rounded.VpnKey,
                title = "Connect OpenRouter",
                message = "Add an API key to start chatting.",
                actionLabel = "Open settings",
                onAction = onOpenSettings,
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OxygenMark(52.dp)
        Spacer(Modifier.height(16.dp))
        Text("No messages yet", style = OxType.body, color = c.textMuted)
    }
}
@Composable
fun OxygenMark(diameter: Dp = 48.dp, tint: Color = Ox.colors.textMuted) {
    Canvas(Modifier.size(diameter)) {
        val ringRadius = size.minDimension / 2f - 3.dp.toPx()
        val radians = Math.toRadians(-45.0)
        drawCircle(
            color = tint,
            radius = ringRadius,
            center = center,
            style = Stroke(width = size.minDimension * 0.14f),
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.11f,
            center = Offset(
                x = center.x + (ringRadius * cos(radians)).toFloat(),
                y = center.y + (ringRadius * sin(radians)).toFloat(),
            ),
        )
    }
}
private fun buildMarkdownExport(title: String, messages: List<ChatViewModel.UiMessage>): String {
    val sb = StringBuilder()
    sb.append("# ").append(title).append("\n\n")
    for (ui in messages) {
        val message = ui.message
        if (message.role == Role.TOOL) continue
        if (message.role == Role.ASSISTANT && message.content.isBlank()) continue
        when (message.role) {
            Role.USER -> sb.append("**You**\n\n")
            Role.ASSISTANT -> sb.append("**").append(message.modelId ?: "Assistant").append("**\n\n")
            Role.SYSTEM -> sb.append("**System**\n\n")
            Role.TOOL -> sb.append("**Tool result**\n\n")
        }
        if (!message.reasoning.isNullOrBlank()) {
            sb.append("<details><summary>Thinking</summary>\n\n")
                .append(message.reasoning)
                .append("\n\n</details>\n\n")
        }
        sb.append(message.content).append("\n\n")
    }
    sb.append("\n---\nGenerated by Oxygen.\n")
    return sb.toString()
}
