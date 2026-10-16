package app.oxygen.features.usage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oxygen.core.util.Fmt
import app.oxygen.ui.components.OxCard
import app.oxygen.ui.components.OxDivider
import app.oxygen.ui.components.OxModelAvatar
import app.oxygen.ui.components.OxSectionHeader
import app.oxygen.ui.components.OxTopBar
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
@Composable
fun UsageScreen(onBack: () -> Unit) {
    val vm: UsageViewModel = viewModel(initializer = { UsageViewModel() })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val keyInfo by vm.keyInfo.collectAsStateWithLifecycle()
    val credits by vm.credits.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Ox.colors.background,
        topBar = { OxTopBar(title = "Usage & spend", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            OxSectionHeader("This device")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("Total spend", Fmt.cost(state.summary?.total ?: 0.0), Modifier.weight(1f))
                StatCard("Last 30 days", Fmt.cost(state.summary?.last30Days ?: 0.0), Modifier.weight(1f))
                StatCard("Generations", (state.summary?.generationCount ?: 0).toString(), Modifier.weight(1f))
            }
            OxSectionHeader("Daily spend (14 days)", subtitle = "Aggregated from the local ledger")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(14.dp)) {
                DailyChart(state.daily)
            }
            OxSectionHeader("OpenRouter account")
            OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                Column {
                    if (keyInfo == null && credits == null) {
                        Text(
                            "No account data yet. Add an API key in Settings.",
                            style = OxType.bodySmall,
                            color = Ox.colors.textMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        keyInfo?.let { info ->
                            InfoRow("Key usage", Fmt.cost(info.usage ?: 0.0))
                            info.limit?.let {
                                InfoRow("Key limit", Fmt.cost(it))
                                InfoRow("Remaining on key", Fmt.cost(info.limitRemaining ?: (it - (info.usage ?: 0.0))))
                            }
                            info.rateLimitRequests?.let {
                                InfoRow("Rate limit", "$it req / ${info.rateLimitIntervalSeconds ?: 60}s")
                            }
                            InfoRow("Free tier", if (info.isFreeTier) "Yes" else "No")
                        }
                        credits?.let { credit ->
                            OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                            InfoRow("Total credits", Fmt.cost(credit.totalCredits))
                            InfoRow("Credits used", Fmt.cost(credit.totalUsage))
                            InfoRow("Credits remaining", Fmt.cost(credit.remaining))
                        }
                    }
                }
            }
            if (state.byModel.isNotEmpty()) {
                OxSectionHeader("Spend by model")
                OxCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(0.dp)) {
                    Column {
                        state.byModel.forEachIndexed { index, aggregate ->
                            if (index > 0) OxDivider(insetStart = 16.dp, insetEnd = 16.dp)
                            val model = vm.modelById(aggregate.modelId)
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        model.shortName,
                                        style = OxType.body.copy(fontWeight = FontWeight.Medium),
                                        color = Ox.colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${aggregate.count} calls · ${Fmt.tokens(aggregate.tokens.toInt())} tokens",
                                        style = OxType.labelSmall,
                                        color = Ox.colors.textMuted,
                                    )
                                }
                                Text(
                                    Fmt.cost(aggregate.cost),
                                    style = OxType.body.copy(fontWeight = FontWeight.Medium),
                                    color = Ox.colors.text,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    OxCard(modifier, padding = PaddingValues(14.dp)) {
        Text(label, style = OxType.labelSmall, color = Ox.colors.textMuted)
        Spacer(Modifier.height(6.dp))
        Text(value, style = OxType.title, color = Ox.colors.text, maxLines = 1)
    }
}
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = OxType.body, color = Ox.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = OxType.body.copy(fontWeight = FontWeight.Medium), color = Ox.colors.text)
    }
}
@Composable
private fun DailyChart(daily: List<app.oxygen.core.db.DayAggregate>) {
    val barColor = Ox.colors.primary
    val mutedColor = Ox.colors.containerHigh
    val series = remember(daily) { buildDailySeries(daily, days = 14) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp),
    ) {
        val max = (series.maxOrNull() ?: 0.0).coerceAtLeast(0.000001)
        val slot = size.width / series.size.coerceAtLeast(1)
        val barWidth = minOf(slot * 0.55f, 18.dp.toPx())
        series.forEachIndexed { index, value ->
            val barHeight = ((value / max) * size.height * 0.92).toFloat()
            val left = index * slot + (slot - barWidth) / 2f
            if (barHeight <= 0f) {
                drawRoundRect(
                    color = mutedColor,
                    topLeft = Offset(left, size.height - 2f),
                    size = Size(barWidth, 2f),
                    cornerRadius = CornerRadius(1f, 1f),
                )
            } else {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
    }
}
private fun buildDailySeries(daily: List<app.oxygen.core.db.DayAggregate>, days: Int): List<Double> {
    val byDay = daily.associate { it.day to it.cost }
    val today = java.time.LocalDate.now()
    return (days - 1 downTo 0).map { offset ->
        byDay[today.minusDays(offset.toLong()).toString()] ?: 0.0
    }
}
