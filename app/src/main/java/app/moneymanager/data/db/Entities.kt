package app.moneymanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Source {
    const val SMS = "SMS"
    const val MANUAL = "MANUAL"
}

@Entity(
    tableName = "transactions",
    indices = [
        Index("timestamp"),
        Index("reference"),
        Index("merchantKey"),
        Index("categoryKey"),
        Index(value = ["smsHash"], unique = true),
        Index(value = ["amountPaise", "type"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "DEBIT" or "CREDIT". */
    val type: String,
    val amountPaise: Long,
    val timestamp: Long,
    val account: String? = null,
    val accountKind: String? = null,
    val bank: String? = null,
    val merchant: String? = null,
    val merchantKey: String? = null,
    val vpa: String? = null,
    val reference: String? = null,
    val balancePaise: Long? = null,
    val channel: String? = null,
    /** Comma-separated [app.moneymanager.core.model.TxnHint] names, used when re-applying rules. */
    @ColumnInfo(defaultValue = "") val hints: String = "",
    val categoryKey: String,
    /** True once you pick a category by hand; rules never override it afterwards. */
    val categoryLocked: Boolean = false,
    val note: String? = null,
    /** Excluded from spending/income totals (e.g. transfers between your own accounts). */
    val excluded: Boolean = false,
    /** Soft delete, so a rescan doesn't bring the transaction back. */
    val deleted: Boolean = false,
    val source: String,
    val sender: String? = null,
    val rawText: String? = null,
    val smsHash: String? = null,
    val ruleId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val key: String,
    val name: String,
    val emoji: String,
    val colorLight: Long,
    val colorDark: Long,
    /** "EXPENSE" or "INCOME". */
    val kind: String,
    val sortOrder: Int,
)

object RuleSource {
    const val USER = "USER"
    const val SEED = "SEED"
}

@Entity(tableName = "rules", indices = [Index(value = ["keyword"], unique = true)])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Lower-case keyword matched against merchant name + UPI ID. */
    val keyword: String,
    val categoryKey: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "unparsed", indices = [Index(value = ["smsHash"], unique = true), Index("timestamp")])
data class UnparsedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val smsHash: String,
    val reason: String,
    val dismissed: Boolean = false,
)

/** Distinct accounts/cards seen, for the account filter. */
data class AccountRow(
    val account: String,
    val bank: String?,
    val accountKind: String?,
    val n: Int,
)
