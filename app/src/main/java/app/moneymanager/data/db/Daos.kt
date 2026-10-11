package app.moneymanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TransactionEntity>)

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash)")
    suspend fun hashExists(hash: String): Boolean

    /** Possible duplicates of a new SMS transaction: same amount & direction, and same reference or close in time. */
    @Query(
        """SELECT * FROM transactions WHERE source = 'SMS' AND type = :type AND amountPaise = :amount
           AND ((:ref IS NOT NULL AND reference = :ref) OR timestamp BETWEEN :from AND :to)""",
    )
    suspend fun dedupeCandidates(type: String, amount: Long, ref: String?, from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query(
        """SELECT * FROM transactions WHERE deleted = 0
           AND timestamp >= :from AND timestamp < :to
           AND (:allCategories = 1 OR categoryKey IN (:categories))
           AND (:account IS NULL OR account = :account OR (:account = 'cash' AND source = 'MANUAL'))
           AND (:q = '' OR merchant LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%'
                OR vpa LIKE '%' || :q || '%' OR rawText LIKE '%' || :q || '%'
                OR CAST(amountPaise / 100 AS TEXT) LIKE :q || '%')
           ORDER BY timestamp DESC""",
    )
    fun observeFiltered(
        from: Long,
        to: Long,
        allCategories: Boolean,
        categories: List<String>,
        account: String?,
        q: String,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT MIN(timestamp) FROM transactions WHERE deleted = 0")
    fun observeEarliest(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0")
    fun observeCount(): Flow<Int>

    @Query(
        """SELECT account, MAX(bank) AS bank, MAX(accountKind) AS accountKind, COUNT(*) AS n FROM transactions
           WHERE deleted = 0 AND account IS NOT NULL GROUP BY account ORDER BY n DESC""",
    )
    fun observeAccounts(): Flow<List<AccountRow>>

    /** Card numbers that any SMS called a credit card. */
    @Query("SELECT DISTINCT account FROM transactions WHERE accountKind = 'CREDIT_CARD' AND account IS NOT NULL")
    fun observeCreditCardAccounts(): Flow<List<String>>

    @Query("SELECT * FROM transactions WHERE source = 'SMS' AND rawText IS NOT NULL")
    suspend fun smsTransactions(): List<TransactionEntity>

    @Query("UPDATE transactions SET accountKind = :kind WHERE id = :id")
    suspend fun setAccountKind(id: Long, kind: String?)

    @Query("UPDATE transactions SET categoryKey = :category WHERE merchantKey = :merchantKey")
    suspend fun setCategoryForMerchant(merchantKey: String, category: String): Int

    @Query("SELECT * FROM transactions WHERE categoryLocked = 0 AND source = 'SMS'")
    suspend fun unlockedSmsTransactions(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryKey = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String)

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    suspend fun all(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun all(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(list: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY source DESC, keyword")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(list: List<RuleEntity>)

    /** Keyword is unique, so this replaces an existing rule for the same keyword. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RuleEntity>)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clear()
}

@Dao
interface UnparsedDao {
    @Query("SELECT * FROM unparsed WHERE dismissed = 0 ORDER BY timestamp DESC")
    fun observeActive(): Flow<List<UnparsedEntity>>

    @Query("SELECT COUNT(*) FROM unparsed WHERE dismissed = 0")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(u: UnparsedEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<UnparsedEntity>)

    @Query("SELECT * FROM unparsed WHERE smsHash = :hash")
    suspend fun byHash(hash: String): UnparsedEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM unparsed WHERE sender = :sender AND body = :body AND timestamp BETWEEN :from AND :to)")
    suspend fun similarExists(sender: String, body: String, from: Long, to: Long): Boolean

    @Query("UPDATE unparsed SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("DELETE FROM unparsed WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM unparsed")
    suspend fun all(): List<UnparsedEntity>

    @Query("DELETE FROM unparsed")
    suspend fun clear()
}
