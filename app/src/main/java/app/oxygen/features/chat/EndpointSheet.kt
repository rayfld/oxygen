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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.oxygen.OxygenApp
import app.oxygen.core.model.ChatModel
import app.oxygen.core.model.ModelEndpoint
import app.oxygen.core.util.Fmt
import app.oxygen.ui.components.OxBadge
import app.oxygen.ui.components.OxBadgeTone
import app.oxygen.ui.components.OxChip
import app.oxygen.ui.components.OxCheckLine
import app.oxygen.ui.components.OxKeyValue
import app.oxygen.ui.components.OxLoadingDots
import app.oxygen.ui.components.OxSectionHeader
import app.oxygen.ui.components.OxSheet
import app.oxygen.ui.components.OxSheetTitle
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import kotlinx.coroutines.launch
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EndpointSheet(
    model: ChatModel,
    onDismiss: () -> Unit,
) {
    val c = Ox.colors
    val scope = rememberCoroutineScope()
    var endpoints by remember { mutableStateOf<List<ModelEndpoint>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    fun load() {
        scope.launch {
            loading = true
            error = null
            val key = OxygenApp.graph.settings.activeKeySecret().orEmpty()
            OxygenApp.graph.models.endpoints(key, model.id)
                .onSuccess { endpoints = it }
                .onFailure { error = it.message ?: it::class.simpleName ?: "Request failed" }
            loading = false
        }
    }
    LaunchedEffect(model.id) { load() }
    OxSheet(onDismissRequest = onDismiss) {
        OxSheetTitle(model.shortName, subtitle = "${app.oxygen.core.util.Brand.authorLabel(model.author)} · ${model.id}")
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (model.description != null) {
                Text(
                    model.description,
                    style = OxType.bodySmall,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            OxSectionHeader("Capabilities")
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OxCheckLine(model.hasVision, "Vision")
                OxCheckLine(model.hasTools, "Tool calling")
                OxCheckLine(model.hasReasoning, "Reasoning")
                OxCheckLine(model.hasAudioIn, "Audio input")
                OxCheckLine("image_url" in model.supportedParams || model.hasVision, "Image input")
                OxCheckLine("response_format" in model.supportedParams, "Structured output")
            }
            OxSectionHeader("Pricing")
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OxKeyValue("Input", Fmt.pricePerM(model.promptPrice) + " / M tokens")
                OxKeyValue("Output", Fmt.pricePerM(model.completionPrice) + " / M tokens")
                model.cacheReadPrice?.takeIf { it > 0 }?.let { OxKeyValue("Cache read", Fmt.pricePerM(it) + " / M tokens") }
                model.cacheWritePrice?.takeIf { it > 0 }?.let { OxKeyValue("Cache write", Fmt.pricePerM(it) + " / M tokens") }
                OxKeyValue("Context", Fmt.context(model.contextLength) + " tokens")
            }
            OxSectionHeader(
                "Providers",
                subtitle = "OpenRouter routes automatically; these are the live endpoints.",
            )
            val loaded = endpoints
            when {
                loading && loaded == null -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OxLoadingDots()
                        Spacer(Modifier.width(10.dp))
                        Text("Loading providers", style = OxType.bodySmall, color = c.textMuted)
                    }
                }
                error != null && loaded.isNullOrEmpty() -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            error.orEmpty(),
                            style = OxType.bodySmall,
                            color = c.textMuted,
                            maxLines = 3,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OxChip(label = "Retry", onClick = { load() })
                    }
                }
                loaded.isNullOrEmpty() -> {
                    Text(
                        "No providers listed for this model.",
                        style = OxType.bodySmall,
                        color = c.textMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                else -> {
                    Column(Modifier.fillMaxWidth()) {
                        loaded.forEach { endpoint -> EndpointRow(endpoint) }
                    }
                }
            }
            Spacer(Modifier.width(1.dp).padding(bottom = 20.dp))
        }
    }
}
@Composable
private fun EndpointRow(endpoint: ModelEndpoint) {
    val c = Ox.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                endpoint.providerName,
                style = OxType.body.copy(fontWeight = FontWeight.Medium),
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            endpoint.quantization?.let { OxBadge(it) }
        }
        Text(
            buildString {
                endpoint.promptPrice?.let { append(Fmt.pricePerM(it)).append("/M in") }
                endpoint.completionPrice?.let { append(" · ").append(Fmt.pricePerM(it)).append("/M out") }
                endpoint.contextLength?.let { append(" · ").append(Fmt.context(it)).append(" ctx") }
            },
            style = OxType.labelSmall,
            color = c.textMuted,
        )
        val stats = buildList {
            endpoint.latencyMs?.let { add("${it.toInt()}ms latency") }
            endpoint.throughput?.let { add("${it.toInt()} tok/s") }
            endpoint.uptime?.let { add("${it.toInt()}% uptime") }
        }
        if (stats.isNotEmpty()) {
            Text(stats.joinToString("  ·  "), style = OxType.labelSmall, color = c.textSecondary)
        }
    }
}
