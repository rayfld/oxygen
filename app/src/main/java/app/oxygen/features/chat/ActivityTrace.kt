package app.oxygen.features.chat
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oxygen.core.model.ToolCall
import app.oxygen.data.GenerationManager
import app.oxygen.ui.components.OxLoadingDots
import app.oxygen.ui.components.oxCombinedClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import kotlinx.coroutines.delay
@Composable
fun ActivityTrace(
    work: ChatViewModel.ChatListItem.Work,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "workChevron")
    val seconds = (work.durationMs / 1000).coerceAtLeast(1)
    val base = when {
        work.stopped -> "Stopped after ${seconds}s"
        work.hasTools -> "Worked for ${seconds}s"
        else -> "Thought for ${seconds}s"
    }
    val label = if (work.steps.size > 1 && !work.stopped) "$base · ${work.steps.size} steps" else base
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .oxCombinedClickable(
                    onLongClick = {
                        clipboard.setText(AnnotatedString(traceToMarkdown(work, seconds)))
                        Toast.makeText(context, "Trace copied", Toast.LENGTH_SHORT).show()
                    },
                    onClick = { expanded = !expanded },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.background)
                    .border(1.5.dp, c.textMuted, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = OxType.label, color = c.textMuted)
            Spacer(Modifier.weight(1f))
            StepGlyphTrail(work.steps)
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse steps" else "Expand steps",
                tint = c.textMuted,
                modifier = Modifier.size(16.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
                TraceTimeline(work.steps, live = false)
            }
        }
    }
}
@Composable
fun LiveWorkHeader(
    stream: GenerationManager.StreamState,
    steps: List<ChatViewModel.WorkStep>,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    var elapsed by remember(stream.startedAt) {
        mutableStateOf(((System.currentTimeMillis() - stream.startedAt) / 1000).toInt())
    }
    LaunchedEffect(stream.startedAt) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - stream.startedAt) / 1000).toInt()
            delay(1000)
        }
    }
    val activeTool = (steps.lastOrNull() as? ChatViewModel.WorkStep.Tool)?.call?.name
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PulsingNode(strong = true)
        Spacer(Modifier.width(10.dp))
        Text(
            buildString {
                append("Working · ")
                append(if (elapsed >= 1) "${elapsed}s" else "0s")
                activeTool?.let { append(" · ").append(it) }
            },
            style = OxType.label,
            color = c.textSecondary,
        )
    }
}
@Composable
fun TraceTimeline(
    steps: List<ChatViewModel.WorkStep>,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().animateContentSize()) {
        steps.forEachIndexed { index, step ->
            TraceStepRow(
                step = step,
                isLast = index == steps.lastIndex,
                active = live && index == steps.lastIndex,
                live = live,
                index = index,
            )
        }
    }
}
@Composable
private fun TraceStepRow(
    step: ChatViewModel.WorkStep,
    isLast: Boolean,
    active: Boolean,
    live: Boolean,
    index: Int,
) {
    val c = Ox.colors
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val dim by animateFloatAsState(if (live && !active) 0.72f else 1f, label = "stepDim")
    val stagger = index.coerceAtMost(8) * 30
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(180, delayMillis = stagger)) +
            slideInVertically(animationSpec = tween(180, delayMillis = stagger)) { it / 4 },
        exit = fadeOut(),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.width(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                    when (step) {
                        is ChatViewModel.WorkStep.Thinking ->
                            if (active) PulsingNode(strong = true) else StaticNode(strong = true)
                        is ChatViewModel.WorkStep.Say -> StaticNode(strong = false)
                        is ChatViewModel.WorkStep.Tool -> ToolNode(step.call.name, active)
                    }
                }
                if (!isLast) {
                    Box(
                        Modifier
                            .padding(vertical = 2.dp)
                            .width(1.dp)
                            .weight(1f)
                            .background(c.border),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(bottom = if (isLast) 0.dp else 14.dp)
                    .alpha(dim)
                    .oxCombinedClickable(
                        onLongClick = {
                            clipboard.setText(AnnotatedString(stepToMarkdown(step)))
                            Toast.makeText(context, "Step copied", Toast.LENGTH_SHORT).show()
                        },
                        onClick = {},
                    ),
            ) {
                when (step) {
                    is ChatViewModel.WorkStep.Thinking -> {
                        Text("Thinking", style = OxType.labelSmall, color = c.textMuted)
                        Spacer(Modifier.height(3.dp))
                        ReasoningText(step.text)
                    }
                    is ChatViewModel.WorkStep.Say -> ReasoningText(step.text)
                    is ChatViewModel.WorkStep.Tool -> ToolStepBody(step, live, active)
                }
            }
        }
    }
}
@Composable
private fun ToolStepBody(step: ChatViewModel.WorkStep.Tool, live: Boolean, active: Boolean) {
    when (step.call.name) {
        "calculator" -> CalculatorStep(step.call, step.result, running = live && active)
        "datetime" -> DatetimeStep(step.result, running = live && active)
        "shell", "bash" -> ShellStep(step.call, step.result, running = live && active)
        "web_search" -> WebSearchStep(step.call, step.result, running = live && active)
        else -> ToolCallCard(step.call.name, step.call.arguments, running = live, result = step.result)
    }
}
@Composable
private fun WebSearchStep(call: ToolCall, result: String?, running: Boolean) {
    val c = Ox.colors
    val uriHandler = LocalUriHandler.current
    val query = remember(call.arguments) { extractJsonString(call.arguments, "query") }
    val sources = remember(result) {
        result?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }
    ToolStepShell("Web search", Icons.Rounded.Language, c.success, running) {
        if (query != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                query,
                style = OxType.bodySmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            sources.take(8).forEach { url ->
                Text(
                    prettyUrl(url),
                    style = OxType.bodySmall,
                    color = c.link,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .oxCombinedClickable(onClick = { runCatching { uriHandler.openUri(url) } })
                        .padding(vertical = 2.dp),
                )
            }
            if (sources.size > 8) {
                Spacer(Modifier.height(2.dp))
                Text("+${sources.size - 8} more", style = OxType.labelSmall, color = c.textMuted)
            }
        } else if (!running) {
            Spacer(Modifier.height(4.dp))
            Text("No sources returned", style = OxType.labelSmall, color = c.textMuted)
        }
    }
}
private fun prettyUrl(url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removePrefix("www.")
    .trimEnd('/')
@Composable
private fun CalculatorStep(call: ToolCall, result: String?, running: Boolean) {
    val c = Ox.colors
    val expression = remember(call.arguments) { extractJsonString(call.arguments, "expression") }
    ToolStepShell("calculator", Icons.Rounded.Calculate, c.link, running) {
        if (expression != null) {
            Spacer(Modifier.height(4.dp))
            Text(expression, style = OxType.codeSmall, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (result != null) {
            Spacer(Modifier.height(6.dp))
            val isError = result.startsWith("Error")
            Text(
                result,
                style = if (isError) {
                    OxType.bodySmall
                } else {
                    OxType.code.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                },
                color = if (isError) c.destructive else c.text,
            )
        }
    }
}
@Composable
private fun DatetimeStep(result: String?, running: Boolean) {
    val c = Ox.colors
    ToolStepShell("datetime", Icons.Rounded.Schedule, c.warning, running) {
        if (result != null) {
            Spacer(Modifier.height(4.dp))
            Text(result, style = OxType.bodySmall, color = c.textSecondary)
        }
    }
}
@Composable
private fun ShellStep(call: ToolCall, result: String?, running: Boolean) {
    val c = Ox.colors
    val command = remember(call.arguments) { extractJsonString(call.arguments, "command") }
    ToolStepShell("shell", Icons.Rounded.Terminal, c.success, running) {
        if (command != null) {
            Spacer(Modifier.height(4.dp))
            Text("$ $command", style = OxType.codeSmall, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (result != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                result,
                style = OxType.codeSmall,
                color = c.textSecondary,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
@Composable
private fun ToolStepShell(
    title: String,
    icon: ImageVector,
    tint: Color,
    running: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ox.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.container.copy(alpha = 0.45f))
            .border(1.dp, c.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(7.dp))
            Text(title, style = OxType.codeSmall.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            Spacer(Modifier.weight(1f))
            if (running) OxLoadingDots(dotSize = 3.dp)
        }
        content()
    }
}
private fun extractJsonString(arguments: String, key: String): String? {
    val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(arguments) ?: return null
    return match.groupValues.getOrNull(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\n", "\n")
        ?.replace("\\\\", "\\")
}
@Composable
private fun StaticNode(strong: Boolean) {
    val c = Ox.colors
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(c.background)
            .border(1.5.dp, if (strong) c.borderStrong else c.border, CircleShape),
    )
}
@Composable
private fun PulsingNode(strong: Boolean) {
    val c = Ox.colors
    val transition = rememberInfiniteTransition(label = "nodePing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "nodePingProgress",
    )
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(16.dp)
                .graphicsLayer {
                    val s = 0.5f + progress * 0.8f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - progress) * 0.6f
                }
                .clip(CircleShape)
                .border(1.dp, if (strong) c.borderStrong else c.border, CircleShape),
        )
        StaticNode(strong)
    }
}
@Composable
private fun ToolNode(toolName: String, active: Boolean) {
    val c = Ox.colors
    val (icon, tint) = toolVisual(toolName)
    val node: @Composable () -> Unit = {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(c.container)
                .border(1.dp, c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(10.dp))
        }
    }
    if (active) {
        val transition = rememberInfiniteTransition(label = "toolPing")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "toolPingProgress",
        )
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        val s = 0.6f + progress * 0.7f
                        scaleX = s
                        scaleY = s
                        alpha = (1f - progress) * 0.5f
                    }
                    .clip(CircleShape)
                    .border(1.dp, c.borderStrong, CircleShape),
            )
            node()
        }
    } else {
        node()
    }
}
@Composable
private fun toolVisual(name: String): Pair<ImageVector, Color> {
    val c = Ox.colors
    return when (name) {
        "calculator" -> Icons.Rounded.Calculate to c.link
        "datetime" -> Icons.Rounded.Schedule to c.warning
        "web_search" -> Icons.Rounded.Language to c.success
        "web_fetch" -> Icons.Rounded.Link to c.link
        "image_generation" -> Icons.Rounded.Image to c.textSecondary
        "shell", "bash" -> Icons.Rounded.Terminal to c.success
        "random" -> Icons.Rounded.Casino to c.warning
        "uuid" -> Icons.Rounded.Fingerprint to c.textSecondary
        "base64" -> Icons.Rounded.Code to c.textSecondary
        "hash" -> Icons.Rounded.Tag to c.textSecondary
        "convert" -> Icons.Rounded.SwapHoriz to c.textSecondary
        "text_stats" -> Icons.Rounded.Subject to c.textSecondary
        "days_between" -> Icons.Rounded.DateRange to c.textSecondary
        "fusion" -> Icons.Rounded.Hub to c.link
        "advisor" -> Icons.Rounded.School to c.warning
        "subagent" -> Icons.Rounded.AccountTree to c.link
        "search_models" -> Icons.Rounded.Search to c.textSecondary
        else -> Icons.Rounded.Build to c.textSecondary
    }
}
@Composable
private fun StepGlyphTrail(steps: List<ChatViewModel.WorkStep>) {
    val c = Ox.colors
    if (steps.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.take(6).forEach { step ->
            when (step) {
                is ChatViewModel.WorkStep.Thinking -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .border(1.25.dp, c.textMuted, CircleShape),
                )
                is ChatViewModel.WorkStep.Say -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.textMuted),
                )
                is ChatViewModel.WorkStep.Tool -> {
                    val (icon, tint) = toolVisual(step.call.name)
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                }
            }
        }
        if (steps.size > 6) {
            Text("+${steps.size - 6}", style = OxType.labelSmall, color = c.textMuted)
        }
    }
}
private fun traceToMarkdown(work: ChatViewModel.ChatListItem.Work, seconds: Long): String = buildString {
    append("Worked for ${seconds}s (${work.steps.size} steps)\n\n")
    work.steps.forEach { step -> append(stepToMarkdown(step)).append("\n") }
}
private fun stepToMarkdown(step: ChatViewModel.WorkStep): String = when (step) {
    is ChatViewModel.WorkStep.Thinking -> "**Thinking**\n${step.text}\n"
    is ChatViewModel.WorkStep.Say -> "${step.text}\n"
    is ChatViewModel.WorkStep.Tool -> buildString {
        append("**${step.call.name}** `${step.call.arguments}`\n")
        step.result?.let { append("→ $it\n") }
    }
}
