package app.oxygen.ui.components
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.oxygen.ui.theme.Ox
import app.oxygen.ui.theme.OxType
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OxSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ox.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = c.surface,
        contentColor = c.text,
        scrimColor = c.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.borderStrong),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
@Composable
fun OxSheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = Ox.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = OxType.titleLarge, color = c.text)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = OxType.bodySmall, color = c.textMuted)
        }
    }
}
@Composable
fun OxDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    showDismiss: Boolean = dismissLabel.isNotBlank(),
    extraContent: (@Composable () -> Unit)? = null,
) {
    val c = Ox.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.textSecondary,
        shape = RoundedCornerShape(18.dp),
        title = { Text(title, style = OxType.title) },
        text = {
            Column {
                if (message != null) {
                    Text(message, style = OxType.body)
                }
                if (extraContent != null) {
                    Spacer(Modifier.height(12.dp))
                    extraContent()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = OxType.button.copy(fontWeight = FontWeight.SemiBold),
                    color = if (destructive) c.destructive else c.text,
                )
            }
        },
        dismissButton = if (showDismiss) {
            {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, style = OxType.button, color = c.textMuted)
                }
            }
        } else {
            null
        },
    )
}
@Composable
fun OxSheetRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
