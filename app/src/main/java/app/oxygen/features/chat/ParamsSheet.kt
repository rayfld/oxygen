package app.oxygen.features.chat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.ChatParams
import app.oxygen.core.model.ProviderInfo
import app.oxygen.core.model.ProviderPrefs
import app.oxygen.ui.components.OxButton
import app.oxygen.ui.components.OxButtonVariant
import app.oxygen.ui.components.OxChip
import app.oxygen.ui.components.OxListItem
import app.oxygen.ui.components.OxLoadingDots
import app.oxygen.ui.components.OxMultilineField
import app.oxygen.ui.components.OxNumberField
import app.oxygen.ui.components.OxSegmentedControl
import app.oxygen.ui.components.OxSectionHeader
import app.oxygen.ui.components.OxSheet
import app.oxygen.ui.components.OxSheetTitle
import app.oxygen.ui.components.OxSliderRow
import app.oxygen.ui.components.OxSwitchRow
import app.oxygen.ui.components.OxTextField
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParamsSheet(
    model: ChatModel?,
    params: ChatParams,
    systemPrompt: String,
    onSave: (ChatParams, String) -> Unit,
    onPickFallback: () -> Unit,
    onSaveAsPreset: () -> Unit,
    onDismiss: () -> Unit,
    toolsAvailable: Boolean = true,
    onOpenTools: () -> Unit = {},
) {
    var draft by remember { mutableStateOf(params) }
    var prompt by remember { mutableStateOf(systemPrompt) }
    var stopText by remember { mutableStateOf(params.stop?.joinToString("\n").orEmpty()) }
    fun supports(name: String): Boolean =
        model == null || model.supportedParams.isEmpty() || name in model.supportedParams
    OxSheet(onDismissRequest = onDismiss) {
        OxSheetTitle(
            "Parameters",
            subtitle = model?.let { "${it.shortName} · ${it.supportedParams.size} supported params" }
                ?: "Model defaults",
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxChip(
                label = "Reset tuning",
                onClick = {
                    stopText = ""
                    draft = draft.copy(
                        temperature = null,
                        topP = null,
                        frequencyPenalty = null,
                        presencePenalty = null,
                        maxTokens = null,
                        seed = null,
                        stop = null,
                        reasoningEffort = null,
                        reasoningMaxTokens = null,
                        reasoningExclude = null,
                        contextStrategy = null,
                        contextWindow = null,
                        responseFormat = null,
                        jsonSchema = null,
                    )
                },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (model?.imageGeneration == true) {
                ImageOptionsSection(model, draft) { draft = it }
            }
            OxSectionHeader("Instructions")
            OxMultilineField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "System prompt. Sets the model's behavior for this chat",
                modifier = Modifier.padding(horizontal = 20.dp),
                minHeight = 80.dp,
            )
            val anySampling = listOf("temperature", "top_p", "frequency_penalty", "presence_penalty").any { supports(it) }
            if (anySampling) {
                OxSectionHeader("Sampling")
                if (supports("temperature")) {
                    OxSliderRow(
                        title = "Temperature",
                        value = (draft.temperature ?: 1.0).toFloat(),
                        onValueChange = { draft = draft.copy(temperature = it.toDouble()) },
                        valueRange = 0f..2f,
                        valueText = String.format(Locale.US, "%.2f", draft.temperature ?: 1.0),
                        defaultValue = 1f,
                        onReset = { draft = draft.copy(temperature = null) },
                    )
                }
                if (supports("top_p")) {
                    OxSliderRow(
                        title = "Top P",
                        value = (draft.topP ?: 1.0).toFloat(),
                        onValueChange = { draft = draft.copy(topP = it.toDouble()) },
                        valueRange = 0.01f..1f,
                        valueText = String.format(Locale.US, "%.2f", draft.topP ?: 1.0),
                        defaultValue = 1f,
                        onReset = { draft = draft.copy(topP = null) },
                    )
                }
                if (supports("frequency_penalty")) {
                    OxSliderRow(
                        title = "Frequency penalty",
                        value = (draft.frequencyPenalty ?: 0.0).toFloat(),
                        onValueChange = { draft = draft.copy(frequencyPenalty = it.toDouble()) },
                        valueRange = -2f..2f,
                        valueText = String.format(Locale.US, "%.2f", draft.frequencyPenalty ?: 0.0),
                        defaultValue = 0f,
                        onReset = { draft = draft.copy(frequencyPenalty = null) },
                    )
                }
                if (supports("presence_penalty")) {
                    OxSliderRow(
                        title = "Presence penalty",
                        value = (draft.presencePenalty ?: 0.0).toFloat(),
                        onValueChange = { draft = draft.copy(presencePenalty = it.toDouble()) },
                        valueRange = -2f..2f,
                        valueText = String.format(Locale.US, "%.2f", draft.presencePenalty ?: 0.0),
                        defaultValue = 0f,
                        onReset = { draft = draft.copy(presencePenalty = null) },
                    )
                }
            }
            val anyLimits = listOf("max_tokens", "seed", "stop").any { supports(it) }
            if (anyLimits) {
                OxSectionHeader("Limits")
                if (supports("max_tokens")) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Max output tokens", style = OxType.body, color = Ox.colors.text)
                            Text("Blank = provider default", style = OxType.labelSmall, color = Ox.colors.textMuted)
                        }
                        OxNumberField(
                            value = draft.maxTokens?.toString().orEmpty(),
                            onValueChange = { draft = draft.copy(maxTokens = it.toIntOrNull()) },
                            placeholder = "auto",
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
                if (supports("seed")) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Seed", style = OxType.body, color = Ox.colors.text)
                            Text("For reproducible sampling", style = OxType.labelSmall, color = Ox.colors.textMuted)
                        }
                        OxNumberField(
                            value = draft.seed?.toString().orEmpty(),
                            onValueChange = { draft = draft.copy(seed = it.toLongOrNull()) },
                            placeholder = "random",
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
                if (supports("stop")) {
                    OxMultilineField(
                        value = stopText,
                        onValueChange = {
                            stopText = it
                            draft = draft.copy(stop = it.split("\n").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.takeIf { s -> s.isNotEmpty() })
                        },
                        placeholder = "Stop sequences (one per line)",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        minHeight = 56.dp,
                    )
                }
            }
            if (model == null || model.hasReasoning || supports("reasoning")) {
                OxSectionHeader("Reasoning")
                val efforts = listOf("Default", "Low", "Medium", "High")
                val currentIndex = when (draft.reasoningEffort) {
                    "low" -> 1
                    "medium" -> 2
                    "high" -> 3
                    else -> 0
                }
                OxSegmentedControl(
                    options = efforts,
                    selectedIndex = currentIndex,
                    onSelect = { index ->
                        draft = draft.copy(
                            reasoningEffort = when (index) {
                                1 -> "low"
                                2 -> "medium"
                                3 -> "high"
                                else -> null
                            },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                OxSwitchRow(
                    title = "Hide reasoning",
                    subtitle = "Request answers without exposing thinking tokens",
                    checked = draft.reasoningExclude == true,
                    onCheckedChange = { draft = draft.copy(reasoningExclude = it) },
                )
            }
            if (toolsAvailable) {
                OxSectionHeader("Tools")
                OxListItem(
                    title = "Tools and sandbox",
                    subtitle = enabledToolCount(draft).let { count ->
                        if (count == 0) "None enabled" else "$count enabled"
                    },
                    icon = Icons.Rounded.Build,
                    onClick = onOpenTools,
                )
            }
            OxSectionHeader("Context", subtitle = "How much history is sent with each request")
            val contextOptions = listOf("Full", "Window", "Middle-out")
            val contextIndex = when (draft.contextStrategy) {
                "window" -> 1
                "midout" -> 2
                else -> 0
            }
            OxSegmentedControl(
                options = contextOptions,
                selectedIndex = contextIndex,
                onSelect = { index ->
                    draft = draft.copy(
                        contextStrategy = when (index) {
                            1 -> "window"
                            2 -> "midout"
                            else -> null
                        },
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (draft.contextStrategy == "window") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Messages kept", style = OxType.body, color = Ox.colors.text)
                        Text("Older messages are dropped from the request", style = OxType.labelSmall, color = Ox.colors.textMuted)
                    }
                    OxNumberField(
                        value = draft.contextWindow?.toString().orEmpty(),
                        onValueChange = { draft = draft.copy(contextWindow = it.toIntOrNull()) },
                        placeholder = "20",
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
            OxSectionHeader("Plugins")
            OxSwitchRow(
                title = "Response healing",
                subtitle = "Repairs malformed JSON in structured outputs",
                checked = draft.responseHealing == true,
                onCheckedChange = { draft = draft.copy(responseHealing = it) },
            )
            OxSwitchRow(
                title = "Parse PDFs server-side",
                subtitle = "file-parser plugin. Sends PDF attachments as parsed text",
                checked = draft.fileParser == true,
                onCheckedChange = { draft = draft.copy(fileParser = it) },
            )
            if (supports("response_format")) {
                OxSectionHeader("Response format")
                val formatOptions = listOf("Text", "JSON object", "JSON schema")
                val formatIndex = when (draft.responseFormat) {
                    "json_object" -> 1
                    "json_schema" -> 2
                    else -> 0
                }
                OxSegmentedControl(
                    options = formatOptions,
                    selectedIndex = formatIndex,
                    onSelect = { index ->
                        draft = draft.copy(
                            responseFormat = when (index) {
                                1 -> "json_object"
                                2 -> "json_schema"
                                else -> null
                            },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (draft.responseFormat == "json_schema") {
                    OxMultilineField(
                        value = draft.jsonSchema.orEmpty(),
                        onValueChange = { draft = draft.copy(jsonSchema = it) },
                        placeholder = "{\"type\":\"object\",\"properties\":{...}} or a full json_schema with name/strict/schema",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        minHeight = 110.dp,
                        mono = true,
                    )
                }
            }
            OxSectionHeader("Fallback models", subtitle = "Tried in order when the primary model fails")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.fallbackModels.forEach { fallbackId ->
                    OxChip(
                        label = fallbackId,
                        icon = Icons.Rounded.Close,
                        onClick = {
                            draft = draft.copy(fallbackModels = draft.fallbackModels.filterNot { it == fallbackId })
                        },
                    )
                }
                OxChip(label = "Add", icon = Icons.Rounded.Add, onClick = onPickFallback)
            }
            RoutingEditor(
                prefs = draft.provider,
                onChange = { draft = draft.copy(provider = it) },
            )
            OxSectionHeader(
                "Advanced request body",
                subtitle = "JSON object merged into the request, for provider-specific parameters",
            )
            OxMultilineField(
                value = draft.extraBody.orEmpty(),
                onValueChange = { draft = draft.copy(extraBody = it.takeIf { text -> text.isNotBlank() }) },
                placeholder = "{\"reasoning\":{\"mode\":\"summary\"}}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                minHeight = 90.dp,
                mono = true,
            )
            Spacer(Modifier.width(1.dp).padding(bottom = 12.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OxButton(
                "Save as preset",
                onClick = onSaveAsPreset,
                variant = OxButtonVariant.Outline,
                modifier = Modifier.weight(1f),
            )
            OxButton(
                "Save",
                onClick = { onSave(draft, prompt) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageOptionsSection(
    model: ChatModel,
    draft: ChatParams,
    onChange: (ChatParams) -> Unit,
) {
    OxSectionHeader("Image", subtitle = "Applies to ${model.shortName}")
    @Composable fun optionRow(title: String, options: List<String>, current: String?, apply: (String?) -> Unit) {
        if (options.isEmpty()) return
        Text(
            title,
            style = OxType.labelSmall,
            color = Ox.colors.textMuted,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val selected = current == option
                OxChip(
                    label = option,
                    selected = selected,
                    onClick = { apply(if (selected) null else option) },
                )
            }
        }
    }
    optionRow("Aspect ratio", model.imageOptions("aspect_ratio"), draft.imageAspectRatio) {
        onChange(draft.copy(imageAspectRatio = it))
    }
    optionRow("Resolution", model.imageOptions("resolution"), draft.imageResolution) {
        onChange(draft.copy(imageResolution = it))
    }
    optionRow(
        "Quality",
        model.imageOptions("quality").ifEmpty { if (model.supportsImageParam("quality")) listOf("auto", "low", "medium", "high") else emptyList() },
        draft.imageQuality,
    ) { onChange(draft.copy(imageQuality = it)) }
    optionRow(
        "Output format",
        model.imageOptions("output_format").ifEmpty { if (model.supportsImageParam("output_format")) listOf("png", "jpeg", "webp") else emptyList() },
        draft.imageOutputFormat,
    ) { onChange(draft.copy(imageOutputFormat = it)) }
    optionRow(
        "Background",
        model.imageOptions("background").ifEmpty { if (model.supportsImageParam("background")) listOf("auto", "transparent", "opaque") else emptyList() },
        draft.imageBackground,
    ) { onChange(draft.copy(imageBackground = it)) }
    if (model.supportsImageParam("n")) {
        val range = model.imageRange("n") ?: 1..4
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Images", style = OxType.body, color = Ox.colors.text)
                Text("Number to generate", style = OxType.labelSmall, color = Ox.colors.textMuted)
            }
            OxNumberField(
                value = draft.imageCount?.toString().orEmpty(),
                onValueChange = { text ->
                    onChange(draft.copy(imageCount = text.toIntOrNull()?.coerceIn(range.first, range.last)))
                },
                placeholder = "1",
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutingEditor(
    prefs: ProviderPrefs,
    onChange: (ProviderPrefs) -> Unit,
) {
    var providers by remember { mutableStateOf<List<ProviderInfo>>(emptyList()) }
    var providersLoading by remember { mutableStateOf(false) }
    var providersError by remember { mutableStateOf<String?>(null) }
    var providerQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun loadProviders() {
        scope.launch {
            providersLoading = true
            providersError = null
            val key = OxygenApp.graph.settings.activeKeySecret().orEmpty()
            OxygenApp.graph.models.providers(key)
                .onSuccess { providers = it }
                .onFailure { providersError = it.message ?: "Could not load providers" }
            providersLoading = false
        }
    }
    LaunchedEffect(Unit) { loadProviders() }
    OxSectionHeader("Provider routing", subtitle = "Controls how OpenRouter picks the serving provider")
    OxSwitchRow(
        title = "Allow fallbacks",
        subtitle = "Let OpenRouter fall back to other providers if needed",
        checked = prefs.allowFallbacks ?: true,
        onCheckedChange = { onChange(prefs.copy(allowFallbacks = it)) },
    )
    OxSwitchRow(
        title = "Require parameters",
        subtitle = "Only use providers that support every parameter sent",
        checked = prefs.requireParameters == true,
        onCheckedChange = { onChange(prefs.copy(requireParameters = it)) },
    )
    OxSwitchRow(
        title = "Zero data retention",
        subtitle = "Only route to providers with ZDR policies",
        checked = prefs.zdr == true,
        onCheckedChange = { onChange(prefs.copy(zdr = it)) },
    )
    val sorts = listOf("Default", "Price", "Throughput", "Latency")
    val sortIndex = when (prefs.sort) {
        "price" -> 1
        "throughput" -> 2
        "latency" -> 3
        else -> 0
    }
    Text(
        "Prefer",
        style = OxType.labelSmall,
        color = Ox.colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 6.dp),
    )
    OxSegmentedControl(
        options = sorts,
        selectedIndex = sortIndex,
        onSelect = { index ->
            onChange(
                prefs.copy(
                    sort = when (index) {
                        1 -> "price"
                        2 -> "throughput"
                        3 -> "latency"
                        else -> null
                    },
                ),
            )
        },
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    val dataCollection = listOf("Default", "Allow", "Deny")
    val dataIndex = when (prefs.dataCollection) {
        "allow" -> 1
        "deny" -> 2
        else -> 0
    }
    Text(
        "Data collection",
        style = OxType.labelSmall,
        color = Ox.colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
    OxSegmentedControl(
        options = dataCollection,
        selectedIndex = dataIndex,
        onSelect = { index ->
            onChange(
                prefs.copy(
                    dataCollection = when (index) {
                        1 -> "allow"
                        2 -> "deny"
                        else -> null
                    },
                ),
            )
        },
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    if (providersError != null && providers.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                providersError.orEmpty(),
                style = OxType.bodySmall,
                color = Ox.colors.textMuted,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OxChip(label = "Retry", onClick = { loadProviders() })
        }
    } else if (providersLoading && providers.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OxLoadingDots()
            Spacer(Modifier.width(10.dp))
            Text("Loading providers", style = OxType.bodySmall, color = Ox.colors.textMuted)
        }
    } else if (providers.isNotEmpty()) {
        OxTextField(
            value = providerQuery,
            onValueChange = { providerQuery = it },
            placeholder = "Search ${providers.size} providers",
            singleLine = true,
            leadingIcon = Icons.Rounded.Search,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        val filteredProviders = remember(providers, providerQuery) {
            val q = providerQuery.trim().lowercase()
            if (q.isEmpty()) {
                providers.take(30)
            } else {
                providers.filter { it.name.lowercase().contains(q) || it.slug.contains(q) }.take(60)
            }
        }
        Text(
            "Only these providers",
            style = OxType.labelSmall,
            color = Ox.colors.textMuted,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filteredProviders.forEach { provider ->
                val selected = provider.slug in prefs.only
                OxChip(
                    label = provider.name,
                    selected = selected,
                    onClick = {
                        onChange(
                            prefs.copy(
                                only = if (selected) prefs.only - provider.slug else prefs.only + provider.slug,
                            ),
                        )
                    },
                )
            }
        }
        Text(
            "Ignore providers",
            style = OxType.labelSmall,
            color = Ox.colors.textMuted,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filteredProviders.forEach { provider ->
                val selected = provider.slug in prefs.ignore
                OxChip(
                    label = provider.name,
                    selected = selected,
                    onClick = {
                        onChange(
                            prefs.copy(
                                ignore = if (selected) prefs.ignore - provider.slug else prefs.ignore + provider.slug,
                            ),
                        )
                    },
                )
            }
        }
        if (providerQuery.isBlank() && providers.size > 30) {
            Text(
                "Showing 30 of ${providers.size} providers. Type to search.",
                style = OxType.labelSmall,
                color = Ox.colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
    Text(
        "Quantizations",
        style = OxType.labelSmall,
        color = Ox.colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
    )
    val quantizations = listOf("int4", "int8", "fp4", "fp6", "fp8", "fp16", "bf16", "unknown")
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quantizations.forEach { quantization ->
            val selected = quantization in prefs.quantizations
            OxChip(
                label = quantization,
                selected = selected,
                onClick = {
                    onChange(
                        prefs.copy(
                            quantizations = if (selected) prefs.quantizations - quantization else prefs.quantizations + quantization,
                        ),
                    )
                },
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OxNumberField(
            value = prefs.maxPricePrompt?.toString().orEmpty(),
            onValueChange = { onChange(prefs.copy(maxPricePrompt = it.toDoubleOrNull())) },
            placeholder = "Max $/M in",
            modifier = Modifier.weight(1f),
        )
        OxNumberField(
            value = prefs.maxPriceCompletion?.toString().orEmpty(),
            onValueChange = { onChange(prefs.copy(maxPriceCompletion = it.toDoubleOrNull())) },
            placeholder = "Max $/M out",
            modifier = Modifier.weight(1f),
        )
    }
}
