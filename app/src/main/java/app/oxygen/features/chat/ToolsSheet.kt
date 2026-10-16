package app.oxygen.features.chat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.oxygen.core.model.ChatParams
import app.oxygen.core.tools.Tools
import app.oxygen.ui.components.OxButton
import app.oxygen.ui.components.OxNumberField
import app.oxygen.ui.components.OxSegmentedControl
import app.oxygen.ui.components.OxSheet
import app.oxygen.ui.components.OxSheetTitle
import app.oxygen.ui.components.OxSwitchRow
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
fun toolExecutor(params: ChatParams, spec: Tools.Spec): String? =
    params.tools[spec.id]
        ?: if (spec.serverId != null && spec.serverId in params.serverTools) Tools.EXEC_SERVER else null
fun enabledToolCount(params: ChatParams): Int =
    Tools.ALL.count { it.id != Tools.WEB_SEARCH_ID && toolExecutor(params, it) != null }
@Composable
fun ToolsSheet(
    params: ChatParams,
    toolsAvailable: Boolean,
    onChange: (ChatParams) -> Unit,
    onDismiss: () -> Unit,
) {
    OxSheet(onDismissRequest = onDismiss) {
        OxSheetTitle("Tools", subtitle = "Run on this device or on OpenRouter")
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!toolsAvailable) {
                Text(
                    "This model only outputs images, so tools are unavailable.",
                    style = OxType.bodySmall,
                    color = Ox.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                Tools.ALL.filterNot { it.id == Tools.WEB_SEARCH_ID }.forEach { spec ->
                    val executor = toolExecutor(params, spec)
                    ToolRow(
                        spec = spec,
                        executor = executor,
                        onEnable = {
                            val default = if (spec.local) Tools.EXEC_LOCAL else Tools.EXEC_SERVER
                            onChange(
                                params.copy(
                                    tools = params.tools + (spec.id to default),
                                    serverTools = params.serverTools.filterNot { it == spec.serverId },
                                    webSearch = if (spec.id == Tools.WEB_SEARCH_ID) null else params.webSearch,
                                ),
                            )
                        },
                        onDisable = {
                            onChange(
                                params.copy(
                                    tools = params.tools - spec.id,
                                    serverTools = params.serverTools.filterNot { it == spec.serverId },
                                    webSearch = if (spec.id == Tools.WEB_SEARCH_ID) null else params.webSearch,
                                ),
                            )
                        },
                        onExecutor = { exec ->
                            onChange(
                                params.copy(
                                    tools = params.tools + (spec.id to exec),
                                    serverTools = params.serverTools.filterNot { it == spec.serverId },
                                ),
                            )
                        },
                    )
                }
                val anyServerEnabled = Tools.ALL.any {
                    toolExecutor(params, it) == Tools.EXEC_SERVER
                }
                if (anyServerEnabled) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Max tool calls", style = OxType.body, color = Ox.colors.text)
                            Text("Server-tool steps per request (1-30)", style = OxType.labelSmall, color = Ox.colors.textMuted)
                        }
                        OxNumberField(
                            value = params.maxToolCalls?.toString().orEmpty(),
                            onValueChange = { text ->
                                onChange(params.copy(maxToolCalls = text.toIntOrNull()?.coerceIn(1, 30)))
                            },
                            placeholder = "30",
                            modifier = Modifier.padding(start = 8.dp).width(110.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        OxButton(
            "Done",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
@Composable
private fun ToolRow(
    spec: Tools.Spec,
    executor: String?,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onExecutor: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OxSwitchRow(
            title = spec.label,
            subtitle = spec.note?.let { "${spec.description} ($it)" } ?: spec.description,
            checked = executor != null,
            onCheckedChange = { on -> if (on) onEnable() else onDisable() },
        )
        if (executor != null) {
            if (spec.supportsLocal && spec.supportsServer) {
                OxSegmentedControl(
                    options = listOf("On device", "On server"),
                    selectedIndex = if (executor == Tools.EXEC_LOCAL) 0 else 1,
                    onSelect = { index -> onExecutor(if (index == 0) Tools.EXEC_LOCAL else Tools.EXEC_SERVER) },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                )
            } else {
                Text(
                    if (executor == Tools.EXEC_LOCAL) "Runs on this device" else "Runs on OpenRouter",
                    style = OxType.labelSmall,
                    color = Ox.colors.textMuted,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                )
            }
        }
    }
}
