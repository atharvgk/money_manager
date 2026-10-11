package app.moneymanager.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import app.moneymanager.core.categorize.Categories

/** One simple line icon per category; the colour comes from the category. */
fun categoryIcon(key: String?): ImageVector = when (key) {
    Categories.FOOD -> Icons.Rounded.Restaurant
    Categories.GROCERIES -> Icons.Rounded.LocalGroceryStore
    Categories.SHOPPING -> Icons.Rounded.ShoppingBag
    Categories.TRANSPORT -> Icons.Rounded.LocalTaxi
    Categories.FUEL -> Icons.Rounded.LocalGasStation
    Categories.BILLS -> Icons.Rounded.Lightbulb
    Categories.RENT -> Icons.Rounded.Home
    Categories.SUBSCRIPTIONS -> Icons.Rounded.Subscriptions
    Categories.ENTERTAINMENT -> Icons.Rounded.Movie
    Categories.HEALTH -> Icons.Rounded.MedicalServices
    Categories.EDUCATION -> Icons.Rounded.School
    Categories.TRAVEL -> Icons.Rounded.Flight
    Categories.INVESTMENTS -> Icons.Rounded.Savings
    Categories.TRANSFERS -> Icons.Rounded.People
    Categories.ATM_CASH -> Icons.Rounded.LocalAtm
    Categories.INCOME -> Icons.Rounded.Payments
    Categories.REFUNDS -> Icons.Rounded.Replay
    else -> Icons.Rounded.Category
}
