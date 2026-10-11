package app.moneymanager.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.moneymanager.AppContainer
import app.moneymanager.core.format.Inr
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.ui.theme.LocalMoneyColors
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

@Composable
fun ProvideContainer(container: AppContainer, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalAppContainer provides container, content = content)

/** ViewModel with access to the app's repository/settings, without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

@Composable
fun CategoryBadge(category: CategoryEntity?, size: Dp = 40.dp) {
    val color = category.color()
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji ?: "📦", fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                Spacer(Modifier.size(12.dp))
            }
            content()
        }
    }
}

@Composable
fun MonthSelector(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            monthLabel(month),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(150.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/**
 * "▲ ₹1,234 (12%) vs Sep". More spending is shown as worse, less as better, with an arrow and words,
 * so it never relies on colour alone.
 */
@Composable
fun ChangeIndicator(deltaPaise: Long, percent: Double?, versus: String, modifier: Modifier = Modifier) {
    val colors = LocalMoneyColors.current
    val up = deltaPaise > 0
    val same = deltaPaise == 0L
    val fg = when {
        same -> MaterialTheme.colorScheme.onSurfaceVariant
        up -> colors.worse
        else -> colors.better
    }
    val bg = when {
        same -> MaterialTheme.colorScheme.surfaceContainerHigh
        up -> colors.worseContainer
        else -> colors.betterContainer
    }
    val arrow = if (same) "=" else if (up) "▲" else "▼"
    val pct = percent?.let { " (${abs(it).roundToInt()}%)" } ?: ""
    val words = if (same) "Same as $versus" else "${Inr.formatRounded(abs(deltaPaise))}$pct ${if (up) "more" else "less"} than $versus"
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(arrow, color = fg, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text(words, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AmountText(
    paise: Long,
    credit: Boolean,
    modifier: Modifier = Modifier,
    excluded: Boolean = false,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    val color = when {
        excluded -> MaterialTheme.colorScheme.onSurfaceVariant
        credit -> LocalMoneyColors.current.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = (if (credit) "+" else "") + Inr.format(paise),
        color = color,
        style = style,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier,
        textDecoration = if (excluded) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(categories: List<CategoryEntity>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        categories.forEach { c ->
            FilterChip(
                selected = c.key == selected,
                onClick = { onSelect(c.key) },
                label = { Text("${c.emoji}  ${c.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            action()
        }
    }
}
