package app.oxygen.core.db
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        GenerationEntity::class,
        PresetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OxygenDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun fts(): FtsDao
    abstract fun generations(): GenerationDao
    abstract fun presets(): PresetDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN ephemeral INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun build(context: Context): OxygenDatabase =
            Room.databaseBuilder(context, OxygenDatabase::class.java, "oxygen.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
