package app.moneymanager.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

// ---------------------------------------------------------------------------------------------- surfaces

private val CardShape = RoundedCornerShape(20.dp)

/** The app's one card style: flat, borderless, rounded, on the grey page. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(CardShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = CardShape,
        color = LocalMoneyColors.current.card,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** A card with a title and an optional action on the right ("See all"). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier) {
        if (title != null) {
            Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                action?.invoke()
            }
            Spacer(Modifier.height(if (action != null && subtitle == null) 8.dp else 12.dp))
        }
        content()
    }
}

/** "See all ›" style link used in card headers. Compact, so card titles line up with or without one. */
@Composable
fun CardLink(text: String, onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(onClick = onClick, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Screen title row, with a back arrow on inner screens. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        // Used inside screens that already have 16dp side padding.
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack != null) 0.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

// ---------------------------------------------------------------------------------------------- pickers

/** "October ▾": opens a list of months from the first recorded one to now. */
@Composable
fun MonthMenu(month: YearMonth, earliest: YearMonth?, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val now = YearMonth.now(zone)
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(monthLabel(month), style = MaterialTheme.typography.headlineSmall)
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose month")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val first = minOf(earliest ?: now, month)
            var m = now
            while (!m.isBefore(first)) {
                val item = m
                DropdownMenuItem(
                    text = { Text(monthLabel(item), fontWeight = if (item == month) FontWeight.SemiBold else null) },
                    trailingIcon = { if (item == month) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { onChange(item); open = false },
                )
                m = m.minusMonths(1)
            }
        }
    }
}

/** ‹ October 2026 › for screens that step through months. */
@Composable
fun MonthSelector(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(monthLabel(month), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(140.dp))
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** All · Bank · Cards. */
@Composable
fun ScopeTabs(scope: AccountScope, onChange: (AccountScope) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        AccountScope.entries.forEachIndexed { i, s ->
            SegmentedButton(
                selected = scope == s,
                onClick = { onChange(s) },
                shape = SegmentedButtonDefaults.itemShape(i, AccountScope.entries.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(inactiveContainerColor = LocalMoneyColors.current.card),
            ) {
                Text(
                    when (s) {
                        AccountScope.ALL -> "All"
                        AccountScope.BANK -> "Bank accounts"
                        AccountScope.CARDS -> "Credit cards"
                    },
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * "All accounts", "HDFC ••1234", "Cash"… scrolls sideways, edge to edge (it sits in 16dp-padded screens and
 * cards). Hidden when there's only one account.
 */
@Composable
fun AccountChipsRow(chips: List<AccountChip>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    if (chips.size < 2 && selected == null) return
    Row(
        modifier.bleed(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = FilterChipDefaults.filterChipColors(containerColor = LocalMoneyColors.current.card)
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All accounts") }, colors = colors, border = null)
        chips.forEach { c ->
            FilterChip(selected = selected == c.key, onClick = { onSelect(c.key) }, label = { Text(c.label, maxLines = 1) }, colors = colors, border = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(categories: List<CategoryEntity>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        categories.forEach { c ->
            FilterChip(
                selected = c.key == selected,
                onClick = { onSelect(c.key) },
                leadingIcon = { Icon(categoryIcon(c.key), contentDescription = null, tint = c.color(), modifier = Modifier.size(18.dp)) },
                label = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** Lets content run into the parent's side padding, e.g. a sideways-scrolling row that should reach the edge. */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** "23%", or "<1%" for a sliver, so nothing shows as 0% of the total. */
fun percentOf(part: Long, total: Long): String {
    if (total <= 0 || part <= 0) return "0%"
    val pct = part * 100 / total
    return if (pct < 1) "<1%" else "$pct%"
}

// ---------------------------------------------------------------------------------------------- small pieces

/** A list row inside a card: icon, title + subtitle, and a trailing value. */
@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Right-aligned amount with an optional small line under it. */
@Composable
fun AmountColumn(amount: String, below: (@Composable () -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.End) {
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        below?.invoke()
    }
}

/** Category icon on a soft tinted rounded square. */
@Composable
fun CategoryBadge(category: CategoryEntity?, size: Dp = 40.dp) {
    val color = category.color()
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = if (LocalMoneyColors.current.dark) 0.22f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category?.key), contentDescription = category?.name, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/**
 * "▼ ₹1,234 (12%) less than 1–11 Sep". More spending reads as worse, less as better, with an arrow
 * and words so it never relies on colour alone.
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
        same -> MaterialTheme.colorScheme.surfaceContainerHighest
        up -> colors.worseContainer
        else -> colors.betterContainer
    }
    val arrow = if (same) "=" else if (up) "▲" else "▼"
    val pct = percent?.let { " (${abs(it).roundToInt()}%)" } ?: ""
    val words = if (same) "Same as $versus" else "${Inr.formatRounded(abs(deltaPaise))}$pct ${if (up) "more" else "less"} than $versus"
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(arrow, color = fg, fontSize = 10.sp)
        Spacer(Modifier.width(6.dp))
        Text(words, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 2)
    }
}

/** Compact "▲ ₹1,200" / "▼ ₹300" for lists. */
@Composable
fun DeltaText(deltaPaise: Long, modifier: Modifier = Modifier) {
    val colors = LocalMoneyColors.current
    val up = deltaPaise > 0
    Text(
        (if (up) "▲ " else "▼ ") + Inr.formatRounded(abs(deltaPaise)),
        color = if (up) colors.worse else colors.better,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
fun AmountText(
    paise: Long,
    credit: Boolean,
    modifier: Modifier = Modifier,
    excluded: Boolean = false,
    style: TextStyle = MaterialTheme.typography.titleMedium,
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
        textDecoration = if (excluded) TextDecoration.LineThrough else null,
    )
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 40.sp)
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
