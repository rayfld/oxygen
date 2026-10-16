package app.oxygen.features.presets
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatParams
import app.oxygen.core.util.Fmt
import app.oxygen.data.Preset
import app.oxygen.features.chat.ModelPickerSheet
import app.oxygen.features.chat.ParamsSheet
import app.oxygen.ui.components.OxButton
import app.oxygen.ui.components.OxButtonVariant
import app.oxygen.ui.components.OxCard
import app.oxygen.ui.components.OxEmptyState
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.OxListItem
import app.oxygen.ui.components.OxMultilineField
import app.oxygen.ui.components.OxSheet
import app.oxygen.ui.components.OxSheetTitle
import app.oxygen.ui.components.OxTextField
import app.oxygen.ui.components.OxTopBar
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import kotlinx.coroutines.launch
import java.util.Locale
@Composable
fun PresetsScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm: PresetsViewModel = viewModel(initializer = { PresetsViewModel() })
    val presets by vm.presets.collectAsStateWithLifecycle()
    val catalog by OxygenApp.graph.models.catalogFlow.collectAsStateWithLifecycle()
    val catalogLoading by OxygenApp.graph.models.loading.collectAsStateWithLifecycle()
    val catalogError by OxygenApp.graph.models.lastError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Preset?>(null) }
    var pickingModel by remember { mutableStateOf(false) }
    var editingParams by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Ox.colors.background,
        topBar = {
            OxTopBar(
                title = "Presets",
                onBack = onBack,
                actions = {
                    OxIconButton(Icons.Rounded.Add, "New preset", onClick = {
                        val timestamp = System.currentTimeMillis()
                        editing = Preset(
                            id = "",
                            name = "",
                            systemPrompt = null,
                            modelId = null,
                            params = ChatParams(),
                            createdAt = timestamp,
                            updatedAt = timestamp,
                        )
                    })
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (presets.isEmpty()) {
                OxEmptyState(
                    icon = Icons.Rounded.Bookmark,
                    title = "No presets",
                    message = "Presets bundle a model, system prompt and parameters.",
                    actionLabel = "Create a preset",
                    onAction = {
                        val timestamp = System.currentTimeMillis()
                        editing = Preset("", "", null, null, ChatParams(), timestamp, timestamp)
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(presets, key = { it.id }) { preset ->
                        PresetCard(
                            preset = preset,
                            modelName = catalog.firstOrNull { it.id == preset.modelId }?.shortName ?: preset.modelId,
                            onEdit = { editing = preset },
                            onStart = {
                                scope.launch {
                                    val id = vm.startChat(preset)
                                    onOpenChat(id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    editing?.let { preset ->
        var name by remember(preset.id) { mutableStateOf(preset.name) }
        var systemPrompt by remember(preset.id) { mutableStateOf(preset.systemPrompt.orEmpty()) }
        var modelId by remember(preset.id) { mutableStateOf(preset.modelId) }
        var params by remember(preset.id) { mutableStateOf(preset.params) }
        OxSheet(onDismissRequest = { editing = null }) {
            OxSheetTitle(if (preset.id.isBlank()) "New preset" else "Edit preset")
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OxTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Preset name",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                OxMultilineField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    placeholder = "System prompt / persona",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    minHeight = 90.dp,
                )
                OxListItem(
                    title = "Model",
                    subtitle = "Overrides the default model for chats started from this preset",
                    trailingText = catalog.firstOrNull { it.id == modelId }?.shortName ?: "Default",
                    onClick = { pickingModel = true },
                )
                OxListItem(
                    title = "Parameters",
                    subtitle = summary(params),
                    icon = Icons.Rounded.Tune,
                    onClick = { editingParams = true },
                )
                if (preset.id.isNotBlank()) {
                    OxListItem(
                        title = "Delete preset",
                        icon = Icons.Rounded.Delete,
                        destructive = true,
                        onClick = {
                            vm.delete(preset.id)
                            editing = null
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OxButton(
                    "Start chat",
                    onClick = {
                        val updated = preset.copy(
                            name = name.trim().ifBlank { "Preset" },
                            systemPrompt = systemPrompt.takeIf { it.isNotBlank() },
                            modelId = modelId,
                            params = params,
                        )
                        vm.save(updated)
                        editing = null
                        scope.launch {
                            val id = vm.startChat(updated)
                            onOpenChat(id)
                        }
                    },
                    variant = OxButtonVariant.Outline,
                    modifier = Modifier.weight(1f),
                )
                OxButton(
                    "Save",
                    onClick = {
                        vm.save(
                            preset.copy(
                                name = name.trim().ifBlank { "Preset" },
                                systemPrompt = systemPrompt.takeIf { it.isNotBlank() },
                                modelId = modelId,
                                params = params,
                            ),
                        )
                        editing = null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (pickingModel) {
            ModelPickerSheet(
                models = catalog,
                currentModelId = modelId,
                favorites = emptyList(),
                recents = emptyList(),
                title = "Preset model",
                onSelect = {
                    modelId = it
                    pickingModel = false
                },
                onToggleFavorite = {},
                onDetails = {},
                onDismiss = { pickingModel = false },
                loading = catalogLoading,
                error = catalogError,
                onRetry = { scope.launch { OxygenApp.graph.models.retry(OxygenApp.graph.settings.activeKeySecret()) } },
            )
        }
        if (editingParams) {
            ParamsSheet(
                model = catalog.firstOrNull { it.id == (modelId ?: "") },
                params = params,
                systemPrompt = systemPrompt,
                onSave = { newParams, newPrompt ->
                    params = newParams
                    systemPrompt = newPrompt
                    editingParams = false
                },
                onPickFallback = {},
                onSaveAsPreset = {},
                onDismiss = { editingParams = false },
            )
        }
    }
}
@Composable
private fun PresetCard(
    preset: Preset,
    modelName: String?,
    onEdit: () -> Unit,
    onStart: () -> Unit,
) {
    val c = Ox.colors
    OxCard(Modifier.fillMaxWidth(), onClick = onEdit, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    preset.name,
                    style = OxType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    maxLines = 1,
                )
                Text(
                    buildString {
                        modelName?.let { append(it) }
                        val summary = summary(preset.params)
                        if (summary.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(summary)
                        }
                    },
                    style = OxType.labelSmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!preset.systemPrompt.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                preset.systemPrompt,
                style = OxType.bodySmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OxButton(
                "Start chat",
                onClick = onStart,
                variant = OxButtonVariant.Secondary,
                size = app.oxygen.ui.components.OxButtonSize.Small,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
            )
            OxButton(
                "Edit",
                onClick = onEdit,
                variant = OxButtonVariant.Ghost,
                size = app.oxygen.ui.components.OxButtonSize.Small,
            )
        }
    }
}
private fun summary(params: ChatParams): String {
    val parts = ArrayList<String>()
    params.temperature?.let { parts += "temp ${String.format(Locale.US, "%.2f", it)}" }
    params.maxTokens?.let { parts += "max $it" }
    params.reasoningEffort?.let { parts += "reasoning $it" }
    if (params.webSearch == true) parts += "web"
    if (params.fallbackModels.isNotEmpty()) parts += "${params.fallbackModels.size} fallbacks"
    return parts.joinToString(" · ")
}
