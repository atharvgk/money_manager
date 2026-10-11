package app.moneymanager.core.categorize

enum class CategoryKind { EXPENSE, INCOME }

data class CategoryDef(
    val key: String,
    val name: String,
    val emoji: String,
    /** ARGB colours. The first eight spending categories use the validated categorical palette. */
    val colorLight: Long,
    val colorDark: Long,
    val kind: CategoryKind = CategoryKind.EXPENSE,
)

object Categories {
    const val FOOD = "food"
    const val GROCERIES = "groceries"
    const val SHOPPING = "shopping"
    const val TRANSPORT = "transport"
    const val FUEL = "fuel"
    const val BILLS = "bills"
    const val RENT = "rent"
    const val SUBSCRIPTIONS = "subscriptions"
    const val ENTERTAINMENT = "entertainment"
    const val HEALTH = "health"
    const val EDUCATION = "education"
    const val TRAVEL = "travel"
    const val INVESTMENTS = "investments"
    const val TRANSFERS = "transfers_people"
    const val ATM_CASH = "atm_cash"
    const val INCOME = "income"
    const val REFUNDS = "refunds"
    const val OTHER = "other"

    val defaults: List<CategoryDef> = listOf(
        CategoryDef(FOOD, "Food & Dining", "🍔", 0xFFEB6834, 0xFFD95926),
        CategoryDef(GROCERIES, "Groceries", "🛒", 0xFF1BAF7A, 0xFF199E70),
        CategoryDef(SHOPPING, "Shopping", "🛍️", 0xFF2A78D6, 0xFF3987E5),
        CategoryDef(TRANSPORT, "Transport", "🚕", 0xFF6250D6, 0xFF9085E9),
        CategoryDef(FUEL, "Fuel", "⛽", 0xFF9A5B3C, 0xFFB8785A),
        CategoryDef(BILLS, "Bills & Utilities", "💡", 0xFFEDA100, 0xFFC98500),
        CategoryDef(RENT, "Rent", "🏠", 0xFFA0367A, 0xFFA0367A),
        CategoryDef(SUBSCRIPTIONS, "Subscriptions", "🔁", 0xFFE87BA4, 0xFFD55181),
        CategoryDef(ENTERTAINMENT, "Entertainment", "🎬", 0xFFE34948, 0xFFE66767),
        CategoryDef(HEALTH, "Health", "💊", 0xFF008300, 0xFF0CA30C),
        CategoryDef(EDUCATION, "Education", "🎓", 0xFF0F7A6B, 0xFF3FB5A2),
        CategoryDef(TRAVEL, "Travel", "✈️", 0xFF5598E7, 0xFF86B6EF),
        CategoryDef(INVESTMENTS, "Investments", "📈", 0xFF5B7A12, 0xFF8DB33A),
        CategoryDef(TRANSFERS, "Transfers to people", "👥", 0xFF6B7A90, 0xFF95A3B8),
        CategoryDef(ATM_CASH, "ATM/Cash", "🏧", 0xFFB07A00, 0xFFE0A52A),
        CategoryDef(INCOME, "Income", "💰", 0xFF0B8A3E, 0xFF2FBF6B, CategoryKind.INCOME),
        CategoryDef(REFUNDS, "Refunds", "↩️", 0xFF2F8F9D, 0xFF4FB3C2, CategoryKind.INCOME),
        CategoryDef(OTHER, "Other", "📦", 0xFF898781, 0xFF898781),
    )

    private val byKey = defaults.associateBy { it.key }
    fun get(key: String): CategoryDef = byKey[key] ?: byKey.getValue(OTHER)
}
