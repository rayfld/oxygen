package app.oxygen.ui.components
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.oxygen.core.util.Brand
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
import app.oxygen.ui.theme.OxygenColors
@Composable
fun Modifier.oxClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(color = Color.Unspecified),
    enabled = enabled,
    onClick = onClick,
)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.oxCombinedClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this.combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(color = Color.Unspecified),
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick,
)
enum class OxButtonVariant { Primary, Secondary, Outline, Ghost, Destructive }
enum class OxButtonSize { Small, Medium, Large }
@Composable
fun OxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OxButtonVariant = OxButtonVariant.Primary,
    size: OxButtonSize = OxButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = Ox.colors
    val shape = RoundedCornerShape(10.dp)
    val height = when (size) {
        OxButtonSize.Small -> 32.dp
        OxButtonSize.Medium -> 40.dp
        OxButtonSize.Large -> 46.dp
    }
    val horizontal = when (size) {
        OxButtonSize.Small -> 12.dp
        OxButtonSize.Medium -> 16.dp
        OxButtonSize.Large -> 20.dp
    }
    val background = when (variant) {
        OxButtonVariant.Primary -> c.primary
        OxButtonVariant.Secondary -> c.container
        OxButtonVariant.Outline -> Color.Transparent
        OxButtonVariant.Ghost -> Color.Transparent
        OxButtonVariant.Destructive -> if (isDark(c)) c.destructive else c.destructive
    }
    val content = when (variant) {
        OxButtonVariant.Primary -> c.onPrimary
        OxButtonVariant.Destructive -> Color.White
        OxButtonVariant.Outline, OxButtonVariant.Ghost, OxButtonVariant.Secondary -> c.text
    }
    val border = if (variant == OxButtonVariant.Outline) BorderStroke(1.dp, c.borderStrong) else null
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .oxClickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
            Text(
                text,
                style = OxType.button,
                color = if (enabled) content else content.copy(alpha = 0.5f),
                maxLines = 1,
            )
        }
    }
}
@Composable
fun OxIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val color = tint ?: Ox.colors.text
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .oxClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = color.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}
@Composable
fun OxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color? = null,
    border: Boolean = true,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ox.colors
    val base = modifier
        .clip(shape)
        .background(color ?: c.surface)
        .then(if (border) Modifier.border(1.dp, c.border, shape) else Modifier)
    val clickable = if (onClick != null) base.oxClickable(onClick = onClick) else base
    Column(clickable.padding(padding), content = content)
}
@Composable
fun OxSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val c = Ox.colors
    SnackbarHost(hostState, modifier) { data ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = c.containerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                data.visuals.message,
                style = OxType.body,
                color = c.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
@Composable
fun OxChip(
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    val background by animateColorAsState(
        if (selected) c.primary else Color.Transparent,
        label = "chipBg",
    )
    val contentColor = when {
        !enabled -> c.textMuted
        selected -> c.onPrimary
        else -> c.textSecondary
    }
    val shape = CircleShape
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) c.primary else c.border, shape)
            .then(
                if (onClick != null) {
                    Modifier.oxCombinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            }
            Text(
                label,
                style = OxType.label,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}
enum class OxBadgeTone { Neutral, Green, Blue, Amber, Red }
@Composable
fun OxBadge(text: String, tone: OxBadgeTone = OxBadgeTone.Neutral, modifier: Modifier = Modifier) {
    val c = Ox.colors
    val color = when (tone) {
        OxBadgeTone.Neutral -> c.textSecondary
        OxBadgeTone.Green -> c.success
        OxBadgeTone.Blue -> c.link
        OxBadgeTone.Amber -> c.warning
        OxBadgeTone.Red -> c.destructive
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = OxType.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
        )
    }
}
@Composable
fun OxSkeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val c = Ox.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.clip(shape).background(c.skeleton.copy(alpha = alpha)))
}
@Composable
fun OxLoadingDots(
    modifier: Modifier = Modifier,
    color: Color = Ox.colors.textMuted,
    dotSize: Dp = 5.dp,
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dotsT",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val phase = (t - index).coerceIn(0f, 3f)
            val alpha = if (phase < 1f) 0.35f + 0.65f * phase else if (phase < 2f) 1f - 0.65f * (phase - 1f) else 0.35f
            Box(
                Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha.coerceIn(0.25f, 1f))),
            )
        }
    }
}
@Composable
fun OxSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val c = Ox.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .oxClickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OxType.body.copy(fontWeight = FontWeight.Medium), color = if (enabled) c.text else c.textMuted)
            if (subtitle != null) {
                Text(subtitle, style = OxType.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary,
                checkedTrackColor = c.primary,
                uncheckedThumbColor = c.textMuted,
                uncheckedTrackColor = c.containerHigh,
                uncheckedBorderColor = c.borderStrong,
            ),
        )
    }
}
@Composable
fun OxSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueText: String,
    defaultValue: Float? = null,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val c = Ox.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = OxType.body.copy(fontWeight = FontWeight.Medium), color = if (enabled) c.text else c.textMuted, modifier = Modifier.weight(1f))
            if (onReset != null && defaultValue != null && kotlin.math.abs(value - defaultValue) > 0.0001f) {
                Text(
                    "Reset",
                    style = OxType.labelSmall,
                    color = c.link,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .oxClickable(onClick = onReset)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(valueText, style = OxType.label.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.containerHigh,
                disabledThumbColor = c.textMuted,
                disabledActiveTrackColor = c.textMuted,
                disabledInactiveTrackColor = c.container,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
@Composable
fun OxSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ox.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.container)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .shadow(1.dp, RoundedCornerShape(8.dp))
                                .background(c.surface)
                                .border(1.dp, c.border, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        },
                    )
                    .oxClickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = OxType.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) c.text else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
@Composable
fun OxSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, style = OxType.labelSmall, color = Ox.colors.textMuted)
        if (subtitle != null) {
            Text(subtitle, style = OxType.bodySmall, color = Ox.colors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
@Composable
fun OxListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Ox.colors
    val contentColor = if (destructive) c.destructive else c.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.oxClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (destructive) c.destructive else c.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = OxType.body.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = OxType.bodySmall,
                    color = c.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = OxType.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f, fill = false),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
@Composable
fun OxDivider(modifier: Modifier = Modifier, insetStart: Dp = 0.dp, insetEnd: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = insetStart, end = insetEnd)
            .height(1.dp)
            .background(Ox.colors.border),
    )
}
@Composable
fun OxEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Ox.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.container)
                .border(1.dp, c.border, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = OxType.title, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = OxType.body, color = c.textMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OxButton(actionLabel, onAction, variant = OxButtonVariant.Outline)
        }
    }
}
@Composable
fun OxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Ox.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                OxIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            }
            Spacer(Modifier.width(if (onBack != null) 2.dp else 10.dp))
            Column(Modifier.weight(1f)) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(title, style = OxType.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, style = OxType.labelSmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            actions()
            Spacer(Modifier.width(4.dp))
        }
        OxDivider()
    }
}
@Composable
fun isDark(colors: OxygenColors): Boolean =
    colors.background.red + colors.background.green + colors.background.blue < 1.2f
data class OxMenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
@Composable
fun OxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = Ox.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = c.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.border),
        shadowElevation = 8.dp,
        content = content,
    )
}
@Composable
fun OxDropdownItem(action: OxMenuAction) {
    val c = Ox.colors
    DropdownMenuItem(
        text = {
            Text(
                action.label,
                style = OxType.body,
                color = if (action.destructive) c.destructive else c.text,
            )
        },
        leadingIcon = action.icon?.let {
            {
                Icon(
                    it,
                    contentDescription = null,
                    tint = if (action.destructive) c.destructive else c.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = action.onClick,
    )
}
@Composable
fun OxKeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Ox.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = OxType.body, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = OxType.body.copy(fontWeight = FontWeight.Medium),
            color = c.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.7f, fill = false),
        )
    }
}
@Composable
fun OxCheckLine(checked: Boolean, text: String, modifier: Modifier = Modifier) {
    val c = Ox.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (checked) c.success else c.textMuted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(text, style = OxType.bodySmall, color = c.textSecondary)
    }
}
@Composable
fun OxSpacerH(height: Dp) = Spacer(Modifier.height(height))
