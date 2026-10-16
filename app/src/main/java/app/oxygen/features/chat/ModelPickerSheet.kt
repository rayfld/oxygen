package app.oxygen.features.chat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.oxygen.core.model.ChatModel
import app.oxygen.core.util.Brand
import app.oxygen.core.util.Fmt
import app.oxygen.ui.components.OxBadge
import app.oxygen.ui.components.OxBadgeTone
import app.oxygen.ui.components.OxChip
import app.oxygen.ui.components.OxLoadingDots
import app.oxygen.ui.components.OxDropdownItem
import app.oxygen.ui.components.OxDropdownMenu
import app.oxygen.ui.components.OxIconButton
import app.oxygen.ui.components.OxMenuAction
import app.oxygen.ui.components.OxSearchField
import app.oxygen.ui.components.OxSheet
import app.oxygen.ui.components.OxSheetTitle
import app.oxygen.ui.components.oxClickable
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
private enum class ModelFilter(val label: String) {
    All("All"),
    Free("Free"),
    Vision("Vision"),
    Reasoning("Reasoning"),
    Tools("Tools"),
    Image("Image"),
}
private enum class ModelSort(val label: String) {
    Popular("Popular"),
    Name("Name"),
    Newest("Newest"),
    Price("Cheapest"),
    Context("Largest"),
}
private fun ChatModel.matches(filter: ModelFilter): Boolean = when (filter) {
    ModelFilter.All -> true
    ModelFilter.Free -> isFree
    ModelFilter.Vision -> hasVision
    ModelFilter.Reasoning -> hasReasoning
    ModelFilter.Tools -> hasTools
    ModelFilter.Image -> hasImageOut
}
@Composable
fun ModelPickerSheet(
    models: List<ChatModel>,
    currentModelId: String?,
    favorites: List<String>,
    recents: List<String>,
    title: String,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDetails: (ChatModel) -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ModelFilter.All) }
    var sort by remember { mutableStateOf(ModelSort.Popular) }
    var sortOpen by remember { mutableStateOf(false) }
    val filtered = remember(models, query, filter, sort) {
        val q = query.trim().lowercase()
        models
            .asSequence()
            .filter { model ->
                filter == ModelFilter.All || model.matches(filter)
            }
            .filter { model ->
                q.isEmpty() ||
                    model.name.lowercase().contains(q) ||
                    model.id.lowercase().contains(q) ||
                    (model.description?.lowercase()?.contains(q) == true)
            }
            .let { sequence ->
                when (sort) {
                    ModelSort.Popular -> sequence.sortedWith(
                        compareByDescending<ChatModel> { it.popularityScore ?: -1L }
                            .thenBy { it.name.lowercase() },
                    )
                    ModelSort.Name -> sequence.sortedBy { it.name.lowercase() }
                    ModelSort.Newest -> sequence.sortedByDescending { it.createdAt }
                    ModelSort.Price -> sequence.sortedBy { (it.promptPrice ?: 0.0) + (it.completionPrice ?: 0.0) }
                    ModelSort.Context -> sequence.sortedByDescending { it.contextLength ?: 0 }
                }
            }
            .toList()
    }
    val favoriteModels = filtered.filter { it.id in favorites }
    val recentModels = filtered.filter { it.id in recents && it.id !in favorites }
    val shown = filtered.filter { it.id !in favorites && it.id !in recents }
    OxSheet(onDismissRequest = onDismiss) {
        OxSheetTitle(
            title,
            subtitle = when {
                models.isNotEmpty() -> "${models.size} models on OpenRouter"
                loading -> "Loading catalog"
                error != null -> "Catalog unavailable"
                else -> "No models"
            },
        )
        if (models.isEmpty() && loading) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OxLoadingDots()
                Spacer(Modifier.width(10.dp))
                Text("Fetching the model list", style = OxType.bodySmall, color = Ox.colors.textMuted)
            }
        }
        if (models.isEmpty() && !loading && error != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    error,
                    style = OxType.bodySmall,
                    color = Ox.colors.textMuted,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OxChip(label = "Retry", onClick = onRetry)
            }
        }
        OxSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search models",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelFilter.entries.forEach { option ->
                OxChip(
                    label = option.label,
                    selected = filter == option,
                    onClick = { filter = option },
                )
            }
            Box {
                OxChip(label = "Sort: ${sort.label}", onClick = { sortOpen = true })
                OxDropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    ModelSort.entries.forEach { option ->
                        OxDropdownItem(
                            OxMenuAction(option.label) {
                                sortOpen = false
                                sort = option
                            },
                        )
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            if (favoriteModels.isNotEmpty()) {
                item { GroupLabel("Favorites") }
                items(favoriteModels, key = { "fav-${it.id}" }) { model ->
                    ModelRow(model, model.id == currentModelId, favorite = true, onSelect, onToggleFavorite, onDetails)
                }
            }
            if (recentModels.isNotEmpty()) {
                item { GroupLabel("Recently used") }
                items(recentModels, key = { "rec-${it.id}" }) { model ->
                    ModelRow(model, model.id == currentModelId, favorite = model.id in favorites, onSelect, onToggleFavorite, onDetails)
                }
            }
            item { GroupLabel(if (favoriteModels.isEmpty() && recentModels.isEmpty()) "All models" else "More models") }
            items(shown, key = { "all-${it.id}" }) { model ->
                Box(Modifier.animateItem()) {
                    ModelRow(model, model.id == currentModelId, favorite = false, onSelect, onToggleFavorite, onDetails)
                }
            }
            if (shown.isEmpty() && favoriteModels.isEmpty() && recentModels.isEmpty()) {
                item {
                    Text(
                        "No models match your search.",
                        style = OxType.bodySmall,
                        color = Ox.colors.textMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            item { Spacer(Modifier.width(1.dp).padding(bottom = 18.dp)) }
        }
    }
}
@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = OxType.labelSmall,
        color = Ox.colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}
@Composable
private fun ModelRow(
    model: ChatModel,
    selected: Boolean,
    favorite: Boolean,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDetails: (ChatModel) -> Unit,
) {
    val c = Ox.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(c.container.copy(alpha = 0.55f)) else Modifier)
            .oxClickable { onSelect(model.id) }
            .padding(start = 20.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.shortName,
                style = OxType.body.copy(fontWeight = FontWeight.Medium),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                provenanceLine(model),
                style = OxType.labelSmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            priceLine(model)?.let { line ->
                Text(
                    line,
                    style = OxType.labelSmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        OxIconButton(
            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = "Favorite",
            onClick = { onToggleFavorite(model.id) },
            size = 32.dp,
            iconSize = 17.dp,
            tint = if (favorite) c.warning else c.textMuted,
        )
        OxIconButton(
            Icons.Rounded.Info,
            contentDescription = "Details",
            onClick = { onDetails(model) },
            size = 32.dp,
            iconSize = 17.dp,
            tint = c.textMuted,
        )
    }
}
private fun provenanceLine(model: ChatModel): String {
    val parts = ArrayList<String>(4)
    parts += Brand.authorLabel(model.author)
    model.contextLength?.let { parts += "${Fmt.context(it)} ctx" }
    if (model.hasVision) parts += "vision"
    if (model.hasImageOut) parts += "image"
    return parts.joinToString("  ·  ")
}
private fun priceLine(model: ChatModel): String? = when {
    model.isFree -> "Free"
    model.promptPrice != null || model.completionPrice != null ->
        "${Fmt.pricePerM(model.promptPrice)}/${Fmt.pricePerM(model.completionPrice)} per M"
    else -> null
}
