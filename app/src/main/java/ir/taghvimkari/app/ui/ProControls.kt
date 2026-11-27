package ir.taghvimkari.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val ActionShape = RoundedCornerShape(14.dp)
private val ActionPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)

/**
 * The one action button of the app: 48dp high, 14dp corners, an optional leading icon and, when [chevron] is
 * set, an arrow (open / closed) at the end. [filled] = main action, otherwise a quiet outlined button.
 * A small orange dot shows while [dot] is on (a filter is active).
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    chevron: Boolean? = null,
    dot: Boolean = false,
    container: Color = Purple
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        if (dot) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Orange))
        }
        if (chevron != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (chevron) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, modifier = Modifier.size(20.dp)
            )
        }
    }
    val base = modifier.heightIn(min = 48.dp)
    if (filled) {
        Button(
            onClick = onClick, enabled = enabled, modifier = base, shape = ActionShape,
            contentPadding = ActionPadding,
            colors = ButtonDefaults.buttonColors(containerColor = container),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            content = content
        )
    } else {
        OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = base, shape = ActionShape,
            contentPadding = ActionPadding,
            border = BorderStroke(1.dp, if (enabled) Purple.copy(alpha = .45f) else Color(0xFFD5D2E8)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple),
            content = content
        )
    }
}

/**
 * A segmented switch: one rounded track, the chosen part is a filled pill. Used wherever a page has two
 * or three views (ارزیابی جدید / مشاهده ارزیابی, …). [badges] shows a small count beside a part's label.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<String?> = emptyList()
) {
    Surface(
        color = Purple.copy(alpha = .10f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, label ->
                val on = selected == index
                Surface(
                    color = if (on) Purple else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(index) }
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            textAlign = TextAlign.Center,
                            color = if (on) Color.White else Purple,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall
                        )
                        val badge = badges.getOrNull(index)
                        if (badge != null) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = if (on) Color.White.copy(alpha = .22f) else Purple.copy(alpha = .16f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    badge,
                                    Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    color = if (on) Color.White else Purple,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small pill for quick choices (ماه جاری، ماه قبل). */
@Composable
fun QuickChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Purple.copy(alpha = .10f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = Purple, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** A chip that can be switched on and off (several can be on together); a tick shows while it is on. */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        color = if (selected) Purple else Purple.copy(alpha = .10f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = if (selected) Color.White else if (enabled) Purple else Color.Gray,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
