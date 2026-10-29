package com.docsoffice.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        ReadingProgressEntity::class,
        SearchHistoryEntity::class,
        DocumentTextEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        BookmarkEntity::class,
        VaultItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class DocsOfficeDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun documentTextDao(): DocumentTextDao
    abstract fun tagDao(): TagDao
    abstract fun vaultItemDao(): VaultItemDao
}
