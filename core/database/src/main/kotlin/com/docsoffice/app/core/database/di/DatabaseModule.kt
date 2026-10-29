package com.docsoffice.app.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.docsoffice.app.core.database.DocsOfficeDatabase
import com.docsoffice.app.core.database.DocumentDao
import com.docsoffice.app.core.database.DocumentTextDao
import com.docsoffice.app.core.database.ReadingProgressDao
import com.docsoffice.app.core.database.SearchHistoryDao
import com.docsoffice.app.core.database.TagDao
import com.docsoffice.app.core.database.VaultItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE documents ADD COLUMN originalUri TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_progress (
              docId TEXT NOT NULL PRIMARY KEY,
              position INTEGER NOT NULL,
              updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              query TEXT NOT NULL,
              at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vault_items (
              id TEXT NOT NULL PRIMARY KEY,
              encryptedName TEXT NOT NULL,
              blobPath TEXT NOT NULL,
              mimeType TEXT NOT NULL,
              extension TEXT NOT NULL,
              sizeBytes INTEGER NOT NULL,
              createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DocsOfficeDatabase {
        return Room.databaseBuilder(
            context,
            DocsOfficeDatabase::class.java,
            "docs_office.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_3_4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideDocumentDao(database: DocsOfficeDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideReadingProgressDao(database: DocsOfficeDatabase): ReadingProgressDao =
        database.readingProgressDao()

    @Provides
    fun provideSearchHistoryDao(database: DocsOfficeDatabase): SearchHistoryDao =
        database.searchHistoryDao()

    @Provides
    fun provideDocumentTextDao(database: DocsOfficeDatabase): DocumentTextDao =
        database.documentTextDao()

    @Provides
    fun provideTagDao(database: DocsOfficeDatabase): TagDao = database.tagDao()

    @Provides
    fun provideVaultItemDao(database: DocsOfficeDatabase): VaultItemDao = database.vaultItemDao()
}
