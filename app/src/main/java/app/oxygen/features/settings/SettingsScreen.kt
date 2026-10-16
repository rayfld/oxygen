package app.oxygen.features.settings
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatParams
import app.oxygen.core.network.OAuthPkce
import app.oxygen.core.network.friendlyMessage
import app.oxygen.core.model.ChatMessage
import app.oxygen.core.model.Conversation
import app.oxygen.core.util.CrashLog
import app.oxygen.core.util.Fmt
import app.oxygen.core.util.json
import app.oxygen.data.Preset
import app.oxygen.data.toDomain
import app.oxygen.features.chat.ModelPickerSheet
import app.oxygen.features.chat.ParamsSheet
import app.oxygen.navigation.PendingNav
import app.oxygen.ui.components.OxButton
import app.oxygen.ui.components.OxButtonVariant
import app.oxygen.ui.components.OxCard
import app.oxygen.ui.components.OxDialog
import app.oxygen.ui.components.OxDivider
import app.oxygen.ui.components.OxListItem
import app.oxygen.ui.components.OxSheet
import app.oxygen.ui.components.OxSheetTitle
import app.oxygen.ui.components.OxMultilineField
import app.oxygen.ui.components.OxSectionHeader
import app.oxygen.ui.components.OxSegmentedControl
import app.oxygen.ui.components.OxSnackbarHost
import app.oxygen.ui.components.OxSwitchRow
import app.oxygen.ui.components.OxTextField
import app.oxygen.ui.components.OxTopBar
import app.oxygen.ui.components.isDark
import app.oxygen.ui.components.oxClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxAccent
import app.oxygen.ui.theme.OxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
@Serializable
private data class Backup(
    val version: Int = 1,
    val exportedAt: Long,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val presets: List<Preset> = emptyList(),
)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(initializer = { SettingsViewModel() })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val keyInfo by vm.keyInfo.collectAsStateWithLifecycle()
    val credits by vm.credits.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val catalog by OxygenApp.graph.models.catalogFlow.collectAsStateWithLifecycle()
    val catalogLoading by OxygenApp.graph.models.loading.collectAsStateWithLifecycle()
    val catalogError by OxygenApp.graph.models.lastError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showAddKey by remember { mutableStateOf(false) }
    var pickingDefaultModel by remember { mutableStateOf(false) }
    var pickingTitleModel by remember { mutableStateOf(false) }
    var showDefaultParams by remember { mutableStateOf(false) }
    var showSystemPrompt by remember { mutableStateOf(false) }
    var confirmClearChats by remember { mutableStateOf(false) }
    var confirmClearUsage by remember { mutableStateOf(false) }
    var showMemories by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf<String?>(null) }
    var showEgg by remember { mutableStateOf(false) }
    var eggTaps by remember { mutableStateOf(0) }
    LaunchedEffect(eggTaps) {
        if (eggTaps in 1..6) {
            delay(1600)
            eggTaps = 0
        }
    }
    val oauthResult by PendingNav.oauthResult.collectAsStateWithLifecycle()
    LaunchedEffect(oauthResult) {
        oauthResult?.let {
            PendingNav.oauthResult.value = null
            snackbar.showSnackbar(it)
        }
    }
    val activeSecret by produceState<String?>(initialValue = null, settings.activeKeyId) {
        value = withContext(Dispatchers.IO) {
            settings.activeKeyId?.let { id -> OxygenApp.graph.settings.secretFor(id) }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val chat = OxygenApp.graph.chat
                    val conversations = chat.observeConversations().first().map { it.toDomain() }
                    val messages = conversations.flatMap { chat.allMessages(it.id) }
                    val presets = OxygenApp.graph.presets.observeAll().first()
                    val backup = Backup(
                        exportedAt = System.currentTimeMillis(),
                        conversations = conversations,
                        messages = messages,
                        presets = presets,
                    )
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.encodeToString(Backup.serializer(), backup).toByteArray())
                    }
                }
            }
            scope.launch { snackbar.showSnackbar("Backup exported") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                    }
                    val backup = json.decodeFromString(Backup.serializer(), text)
                    val chat = OxygenApp.graph.chat
                    backup.conversations.forEach { chat.upsertConversation(it) }
                    backup.messages.forEach { chat.upsertMessage(it, indexForSearch = it.content.isNotBlank()) }
                    backup.presets.forEach { OxygenApp.graph.presets.save(it) }
                    backup.messages.size
                }
                snackbar.showSnackbar(
                    result.fold(
                        onSuccess = { "Imported $it messages" },
                        onFailure = { "Import failed: ${it.message}" },
                    ),
                )
            }
        }
    }
    Scaffold(
        containerColor = Ox.colors.background,
        snackbarHost = { OxSnackbarHost(snackbar) },
        topBar = {
            OxTopBar(
                title = "Settings",
                onBack = onBack,
                actions = {
                    if (refreshing) {
                        OxLoadingSpinner()
                    } else {
                        app.oxygen.ui.components.OxIconButton(
                            Icons.Rounded.Refresh,
                            "Refresh account",
                            onClick = { vm.reload() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            OxSectionHeader("OpenRouter account")
            OxCard(
                Modifier.padding(horizontal = 16.dp),
                padding = PaddingValues(0.dp),
            ) {
                if (settings.keys.isEmpty()) {
                    OxListItem(
                        title = "No API key yet",
                        subtitle = "Sign in, or paste a key from openrouter.ai/keys",
                        icon = Icons.Rounded.VpnKey,
                    )
                } else {
                    settings.keys.forEachIndexed { index, key ->
                        if (index > 0) OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                        KeyRow(
                            label = key.label,
                            masked = maskKey(activeSecret.takeIf { settings.activeKeyId == key.id }),
                            active = settings.activeKeyId == key.id,
                            onSetActive = { vm.setActiveKey(key.id) },
                            onDelete = { vm.removeKey(key.id) },
                        )
                    }
                }
                if (keyInfo != null || credits != null) {
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val info = keyInfo
                        if (info?.limit != null) {
                            Text(
                                "Key usage  ${Fmt.cost(info.usage ?: 0.0)} of ${Fmt.cost(info.limit)}",
                                style = OxType.bodySmall,
                                color = Ox.colors.textSecondary,
                            )
                            UsageBar(fraction = ((info.usage ?: 0.0) / info.limit).toFloat().coerceIn(0f, 1f))
                        } else if (info?.usage != null) {
                            Text(
                                "Used with this key  ${Fmt.cost(info.usage)}",
                                style = OxType.bodySmall,
                                color = Ox.colors.textSecondary,
                            )
                        }
                        info?.rateLimitRequests?.let { requests ->
                            Text(
                                "Rate limit  $requests requests / ${info.rateLimitIntervalSeconds ?: 60}s",
                                style = OxType.bodySmall,
                                color = Ox.colors.textMuted,
                            )
                        }
                        credits?.let { credit ->
                            Text(
                                "Account credits  ${Fmt.cost(credit.remaining)} remaining",
                                style = OxType.bodySmall,
                                color = Ox.colors.textSecondary,
                            )
                        }
                    }
                }
                OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OxButton(
                        "Sign in with OpenRouter",
                        onClick = {
                            if (OxygenApp.graph.oauthSignIn.start()) {
                                scope.launch {
                                    val result = runCatching { OxygenApp.graph.oauthSignIn.await() }
                                    snackbar.showSnackbar(
                                        result.fold(
                                            onSuccess = { "Signed in with OpenRouter" },
                                            onFailure = { it.friendlyMessage() },
                                        ),
                                    )
                                }
                            } else {
                                Toast.makeText(context, "No browser available for sign-in", Toast.LENGTH_SHORT).show()
                            }
                        },
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        variant = OxButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OxButton(
                        "Add key manually",
                        onClick = { showAddKey = true },
                        icon = Icons.Rounded.Add,
                        variant = OxButtonVariant.Outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OxSectionHeader("Chat defaults")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    OxListItem(
                        title = "Default model",
                        subtitle = "Used when starting a new chat",
                        trailingText = catalog.firstOrNull { it.id == settings.defaultModelId }?.shortName ?: settings.defaultModelId,
                        onClick = { pickingDefaultModel = true },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Default parameters",
                        subtitle = defaultParamsSummary(settings.defaultParams),
                        icon = Icons.Rounded.Tune,
                        onClick = { showDefaultParams = true },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Default system prompt",
                        subtitle = settings.defaultSystemPrompt.takeIf { it.isNotBlank() }?.take(80) ?: "None",
                        icon = Icons.Rounded.Edit,
                        onClick = { showSystemPrompt = true },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Title generation model",
                        subtitle = "Writes chat titles after the first reply",
                        trailingText = settings.titleModelId.ifBlank { "Same as chat" },
                        onClick = { pickingTitleModel = true },
                    )
                }
            }
            OxSectionHeader("Personalization")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    OxListItem(
                        title = "Memories",
                        subtitle = if (settings.memories.isEmpty()) {
                            "Facts added to every chat as context"
                        } else {
                            "${settings.memories.size} saved · added to every chat"
                        },
                        icon = Icons.Rounded.Psychology,
                        onClick = { showMemories = true },
                    )
                }
            }
            OxSectionHeader("Appearance")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    Column(Modifier.padding(16.dp)) {
                        Text("Theme", style = OxType.body, color = Ox.colors.text)
                        Spacer(Modifier.height(8.dp))
                        val modes = listOf("System", "Light", "Dark")
                        val index = when (settings.themeMode) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        }
                        OxSegmentedControl(
                            options = modes,
                            selectedIndex = index,
                            onSelect = { vm.setTheme(listOf("system", "light", "dark")[it]) },
                        )
                        Spacer(Modifier.height(18.dp))
                        Text("Accent", style = OxType.body, color = Ox.colors.text)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OxAccent.entries.forEach { accent ->
                                AccentSwatch(
                                    accent = accent,
                                    selected = settings.accentId == accent.id,
                                    onClick = { vm.setAccent(accent.id) },
                                )
                            }
                        }
                    }
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxSwitchRow(
                        title = "True black (OLED)",
                        subtitle = "Pure black background in dark mode",
                        checked = settings.oled,
                        onCheckedChange = { vm.setOled(it) },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    Column(Modifier.padding(16.dp)) {
                        Text("Message style", style = OxType.body, color = Ox.colors.text)
                        Spacer(Modifier.height(8.dp))
                        OxSegmentedControl(
                            options = listOf("Bubbles", "Plain"),
                            selectedIndex = if (settings.messageStyle == "plain") 1 else 0,
                            onSelect = { vm.setMessageStyle(if (it == 0) "bubble" else "plain") },
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Chat text size", style = OxType.body, color = Ox.colors.text)
                        Spacer(Modifier.height(8.dp))
                        val sizes = listOf(0.9f, 1f, 1.15f, 1.3f)
                        val sizeIndex = sizes.indexOfFirst { kotlin.math.abs(it - settings.chatFontScale) < 0.01f }
                            .let { if (it >= 0) it else 1 }
                        OxSegmentedControl(
                            options = listOf("Small", "Default", "Large", "XL"),
                            selectedIndex = sizeIndex,
                            onSelect = { vm.setChatFontScale(sizes[it]) },
                        )
                    }
                }
            }
            OxSectionHeader("Library")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    OxListItem(
                        title = "Presets & personas",
                        subtitle = "Reusable prompts, models and parameters",
                        icon = Icons.Rounded.Bookmark,
                        onClick = onOpenPresets,
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Usage & spend",
                        subtitle = "Local ledger plus account status",
                        icon = Icons.Rounded.DataUsage,
                        onClick = onOpenUsage,
                    )
                }
            }
            OxSectionHeader("Data")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    OxListItem(
                        title = "Export backup",
                        subtitle = "Chats, messages and presets as JSON",
                        icon = Icons.Rounded.Upload,
                        onClick = { exportLauncher.launch("oxygen-backup.json") },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Import backup",
                        subtitle = "Merges a previously exported file",
                        icon = Icons.Rounded.Download,
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Clear usage history",
                        subtitle = "Removes the local cost ledger",
                        icon = Icons.Rounded.Delete,
                        destructive = true,
                        onClick = { confirmClearUsage = true },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Delete all chats",
                        subtitle = "Every conversation on this device",
                        icon = Icons.Rounded.Delete,
                        destructive = true,
                        onClick = { confirmClearChats = true },
                    )
                }
            }
            OxSectionHeader("About")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    OxListItem(
                        title = "Oxygen",
                        subtitle = "Version 1.0.0 · an unofficial OpenRouter client",
                        icon = Icons.Rounded.Info,
                        onClick = {
                            eggTaps += 1
                            val remaining = 7 - eggTaps
                            when {
                                remaining <= 0 -> {
                                    eggTaps = 0
                                    showEgg = true
                                }
                                remaining <= 4 -> Toast.makeText(context, "$remaining taps left", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Models & pricing",
                        subtitle = "openrouter.ai/models",
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { uriHandler.openUri("https://openrouter.ai/models") },
                    )
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "OpenRouter dashboard",
                        subtitle = "Credits, activity and settings",
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { uriHandler.openUri("https://openrouter.ai/settings/credits") },
                    )
                    activeSecret?.let { secret ->
                        OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                        OxListItem(
                            title = "This key on OpenRouter",
                            subtitle = "opens the key's own settings page",
                            icon = Icons.Rounded.VpnKey,
                            onClick = {
                                uriHandler.openUri("https://openrouter.ai/keys/${sha256Hex(secret)}")
                            },
                        )
                    }
                    OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                    OxListItem(
                        title = "Crash log",
                        subtitle = "View the last uncaught exception",
                        icon = Icons.Rounded.WarningAmber,
                        onClick = {
                            val text = CrashLog.read(context)
                            if (text == null) {
                                Toast.makeText(context, "No crashes recorded", Toast.LENGTH_SHORT).show()
                            } else {
                                crashText = text
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (pickingDefaultModel) {
        ModelPickerSheet(
            models = catalog,
            currentModelId = settings.defaultModelId,
            favorites = settings.favoriteModelIds,
            recents = settings.lastModelIds,
            title = "Default model",
            onSelect = { vm.setDefaultModel(it); pickingDefaultModel = false },
            onToggleFavorite = { modelId -> scope.launch { OxygenApp.graph.settings.toggleFavoriteModel(modelId) } },
            onDetails = {},
            onDismiss = { pickingDefaultModel = false },
            loading = catalogLoading,
            error = catalogError,
            onRetry = { scope.launch { OxygenApp.graph.models.retry(OxygenApp.graph.settings.activeKeySecret()) } },
        )
    }
    if (pickingTitleModel) {
        ModelPickerSheet(
            models = catalog.filter { "text" in it.outputModalities },
            currentModelId = settings.titleModelId.ifBlank { null },
            favorites = settings.favoriteModelIds,
            recents = settings.lastModelIds,
            title = "Title generation model",
            onSelect = { vm.setTitleModel(it); pickingTitleModel = false },
            onToggleFavorite = { modelId -> scope.launch { OxygenApp.graph.settings.toggleFavoriteModel(modelId) } },
            onDetails = {},
            onDismiss = { pickingTitleModel = false },
            loading = catalogLoading,
            error = catalogError,
            onRetry = { scope.launch { OxygenApp.graph.models.retry(OxygenApp.graph.settings.activeKeySecret()) } },
        )
    }
    if (showDefaultParams) {
        ParamsSheet(
            model = catalog.firstOrNull { it.id == settings.defaultModelId },
            params = settings.defaultParams,
            systemPrompt = settings.defaultSystemPrompt,
            onSave = { params, prompt ->
                vm.setDefaultParams(params)
                vm.setDefaultSystemPrompt(prompt)
                showDefaultParams = false
            },
            onPickFallback = {},
            onSaveAsPreset = {},
            onDismiss = { showDefaultParams = false },
        )
    }
    if (showAddKey) {
        var label by remember { mutableStateOf("") }
        var secret by remember { mutableStateOf("") }
        OxDialog(
            title = "Add OpenRouter key",
            message = "Create one at openrouter.ai/keys. Stored encrypted on this device.",
            confirmLabel = "Add key",
            onConfirm = {
                if (secret.isNotBlank()) {
                    vm.addKey(label.ifBlank { "OpenRouter key" }, secret)
                    showAddKey = false
                }
            },
            onDismiss = { showAddKey = false },
            extraContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OxTextField(value = label, onValueChange = { label = it }, placeholder = "Label (optional)")
                    OxTextField(value = secret, onValueChange = { secret = it }, placeholder = "sk-or-v1-…")
                }
            },
        )
    }
    if (showSystemPrompt) {
        var prompt by remember { mutableStateOf(settings.defaultSystemPrompt) }
        OxDialog(
            title = "Default system prompt",
            confirmLabel = "Save",
            onConfirm = {
                vm.setDefaultSystemPrompt(prompt)
                showSystemPrompt = false
            },
            onDismiss = { showSystemPrompt = false },
            extraContent = {
                OxMultilineField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = "You are a helpful assistant…",
                )
            },
        )
    }
    if (confirmClearUsage) {
        OxDialog(
            title = "Clear usage history?",
            message = "The local cost ledger will be emptied. Account-level usage on OpenRouter is not affected.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClearUsage = false
                scope.launch {
                    OxygenApp.graph.usage.clear()
                    snackbar.showSnackbar("Usage history cleared")
                }
            },
            onDismiss = { confirmClearUsage = false },
        )
    }
    if (confirmClearChats) {
        OxDialog(
            title = "Delete all chats?",
            message = "Every conversation and message on this device will be removed.",
            confirmLabel = "Delete all",
            destructive = true,
            onConfirm = {
                confirmClearChats = false
                scope.launch {
                    OxygenApp.graph.generationManager.recoverInterrupted()
                    val conversations = OxygenApp.graph.chat.observeConversations().first()
                    conversations.forEach { OxygenApp.graph.chat.deleteConversation(it.id) }
                    snackbar.showSnackbar("All chats deleted")
                }
            },
            onDismiss = { confirmClearChats = false },
        )
    }
    if (showMemories) {
        var newMemory by remember { mutableStateOf("") }
        OxSheet(onDismissRequest = { showMemories = false }) {
            OxSheetTitle(
                "Memories",
                subtitle = "Included as context in every chat (sent to OpenRouter with your requests)",
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OxTextField(
                    value = newMemory,
                    onValueChange = { newMemory = it },
                    placeholder = "e.g. Prefers Kotlin and concise answers",
                    modifier = Modifier.weight(1f),
                )
                OxButton(
                    "Add",
                    onClick = {
                        vm.addMemory(newMemory)
                        newMemory = ""
                    },
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                if (settings.memories.isEmpty()) {
                    item {
                        Text(
                            "No memories yet.",
                            style = OxType.bodySmall,
                            color = Ox.colors.textMuted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                items(settings.memories, key = { it }) { memory ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(memory, style = OxType.body, color = Ox.colors.text, modifier = Modifier.weight(1f))
                        app.oxygen.ui.components.OxIconButton(
                            Icons.Rounded.Delete,
                            "Remove",
                            onClick = { vm.removeMemory(memory) },
                            size = 32.dp,
                            iconSize = 16.dp,
                            tint = Ox.colors.textMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    crashText?.let { text ->
        OxDialog(
            title = "Last crash",
            confirmLabel = "Clear",
            onConfirm = {
                CrashLog.clear(context)
                crashText = null
                Toast.makeText(context, "Crash log cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { crashText = null },
            dismissLabel = "Close",
            extraContent = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text, style = OxType.codeSmall, color = Ox.colors.textSecondary)
                    }
                }
            },
        )
    }
    if (showEgg) {
        OxDialog(
            title = "Oxygen",
            confirmLabel = "Close",
            onConfirm = { showEgg = false },
            onDismiss = { showEgg = false },
            showDismiss = false,
            extraContent = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EggRing()
                    Spacer(Modifier.height(12.dp))
                    Text("Atomic number 8.", style = OxType.bodySmall, color = Ox.colors.textMuted)
                }
            },
        )
    }
}
@Composable
private fun KeyRow(
    label: String,
    masked: String?,
    active: Boolean,
    onSetActive: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Ox.colors
    Row(
        Modifier
            .fillMaxWidth()
            .oxClickable(onClick = onSetActive)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (active) c.primary else c.borderStrong, CircleShape)
                .then(if (active) Modifier.background(c.primary) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = OxType.body.copy(fontWeight = FontWeight.Medium), color = c.text)
            if (masked != null) {
                Text(masked, style = OxType.labelSmall, color = c.textMuted)
            }
        }
        app.oxygen.ui.components.OxIconButton(
            Icons.Rounded.Delete,
            "Remove key",
            onDelete,
            size = 32.dp,
            iconSize = 16.dp,
            tint = c.textMuted,
        )
    }
}
@Composable
private fun UsageBar(fraction: Float) {
    val c = Ox.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.containerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (fraction > 0.9f) c.destructive else c.primary),
        )
    }
}
@Composable
private fun OxLoadingSpinner() {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.padding(8.dp).size(18.dp),
        strokeWidth = 2.dp,
        color = Ox.colors.textSecondary,
    )
}
private fun maskKey(secret: String?): String? =
    secret?.let { "••••••••" + it.takeLast(6) }
private fun defaultParamsSummary(params: ChatParams): String {
    val parts = ArrayList<String>()
    params.temperature?.let { parts += "temp ${String.format(Locale.US, "%.2f", it)}" }
    params.topP?.let { parts += "top_p ${String.format(Locale.US, "%.2f", it)}" }
    params.maxTokens?.let { parts += "max $it" }
    params.reasoningEffort?.let { parts += "reasoning $it" }
    if (params.webSearch == true) parts += "web search"
    params.provider.let { if (!it.isEmpty) parts += "routing" }
    return if (parts.isEmpty()) "Model defaults" else parts.joinToString(" · ")
}
private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
@Composable
private fun AccentSwatch(accent: OxAccent, selected: Boolean, onClick: () -> Unit) {
    val c = Ox.colors
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, c.text, CircleShape) else Modifier)
            .oxClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(accent.vivid)
                .border(1.dp, c.border, CircleShape),
        )
    }
}
@Composable
private fun EggRing() {
    val c = Ox.colors
    val transition = rememberInfiniteTransition(label = "egg")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "eggAngle",
    )
    Canvas(Modifier.size(120.dp)) {
        val center = this.center
        val radius = size.minDimension / 2f - 12.dp.toPx()
        drawCircle(color = c.primary, radius = radius, center = center, style = Stroke(width = 8.dp.toPx()))
        val radians = Math.toRadians(angle.toDouble())
        drawCircle(
            color = c.primary,
            radius = 8.dp.toPx(),
            center = Offset(
                x = center.x + (radius * cos(radians)).toFloat(),
                y = center.y + (radius * sin(radians)).toFloat(),
            ),
        )
    }
}
