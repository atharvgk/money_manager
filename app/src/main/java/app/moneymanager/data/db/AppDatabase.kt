package app.moneymanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Bump [version] and add a Migration when changing entities. Never use destructive migrations here:
 * this database is the only copy of your history (apart from JSON exports).
 */
@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, RuleEntity::class, UnparsedEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun rules(): RuleDao
    abstract fun unparsed(): UnparsedDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "money.db").build()
    }
}
