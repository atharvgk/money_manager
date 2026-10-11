package app.moneymanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.moneymanager.AppContainer
import app.moneymanager.data.ThemeMode
import app.moneymanager.ui.common.ProvideContainer
import app.moneymanager.ui.compare.CompareScreen
import app.moneymanager.ui.home.HomeScreen
import app.moneymanager.ui.lock.LockScreen
import app.moneymanager.ui.onboarding.OnboardingScreen
import app.moneymanager.ui.settings.RulesScreen
import app.moneymanager.ui.settings.SettingsScreen
import app.moneymanager.ui.settings.UnparsedScreen
import app.moneymanager.ui.subscriptions.SubscriptionsScreen
import app.moneymanager.ui.theme.MoneyManagerTheme
import app.moneymanager.ui.transactions.AddTransactionSheet
import app.moneymanager.ui.transactions.EditTransactionSheet
import app.moneymanager.ui.transactions.TransactionsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val HOME = "home"
    const val COMPARE = "compare"
    const val TRANSACTIONS = "transactions?category={category}&month={month}"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SETTINGS = "settings"
    const val RULES = "rules"
    const val UNPARSED = "unparsed"

    fun transactions(category: String? = null, month: String? = null): String =
        "transactions?category=${category.orEmpty()}&month=${month.orEmpty()}"
}

private data class Tab(val route: String, val navRoute: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.COMPARE, Routes.COMPARE, "Compare", Icons.Outlined.BarChart),
    Tab(Routes.TRANSACTIONS, Routes.transactions(), "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab(Routes.SUBSCRIPTIONS, Routes.SUBSCRIPTIONS, "Recurring", Icons.Outlined.Autorenew),
    Tab(Routes.SETTINGS, Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun AppRoot(container: AppContainer, activity: FragmentActivity, openTxn: MutableStateFlow<Long?>) {
    val prefs by container.settings.prefs.collectAsStateWithLifecycle(initialValue = null)
    val locked by container.lock.locked.collectAsStateWithLifecycle()
    MoneyManagerTheme(prefs?.theme ?: ThemeMode.SYSTEM) {
        ProvideContainer(container) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val p = prefs
                when {
                    p == null -> Unit
                    !p.onboardingDone -> OnboardingScreen()
                    else -> {
                        MainScaffold(openTxn)
                        AnimatedVisibility(visible = p.appLock && locked, enter = fadeIn(), exit = fadeOut()) {
                            LockScreen(activity = activity, onUnlocked = { container.lock.unlock() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(openTxn: MutableStateFlow<Long?>) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val pendingTxn by openTxn.collectAsStateWithLifecycle()

    LaunchedEffect(pendingTxn) {
        pendingTxn?.let {
            editing = it
            openTxn.value = null
        }
    }

    // When a sheet closes, Compose hands focus back to the first text field (the search box) and the
    // keyboard pops up. Clear it instead.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing, adding) {
        if (editing == null && !adding) {
            delay(200)
            focusManager.clearFocus(force = true)
        }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateTab(tab.navRoute) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenCategory = { cat, month -> nav.navigateTab(Routes.transactions(cat, month)) },
                    onOpenSettings = { nav.navigateTab(Routes.SETTINGS) },
                    onAdd = { adding = true },
                )
            }
            composable(Routes.COMPARE) { CompareScreen() }
            composable(
                Routes.TRANSACTIONS,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("month") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                TransactionsScreen(
                    initialCategory = entry.arguments?.getString("category").orEmpty(),
                    initialMonth = entry.arguments?.getString("month").orEmpty(),
                    onEdit = { editing = it },
                    onAdd = { adding = true },
                )
            }
            composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenRules = { nav.navigate(Routes.RULES) },
                    onOpenUnparsed = { nav.navigate(Routes.UNPARSED) },
                )
            }
            composable(Routes.RULES) { RulesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.UNPARSED) { UnparsedScreen(onBack = { nav.popBackStack() }) }
        }
    }

    editing?.let { id -> EditTransactionSheet(id = id, onDismiss = { editing = null }) }
    if (adding) AddTransactionSheet(onDismiss = { adding = false })
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !route.startsWith("transactions?category=") || route == Routes.transactions()
    }
}
