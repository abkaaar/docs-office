package com.docsoffice.app.core.pdftools

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfToolsModule {
    @Binds
    @Singleton
    abstract fun bindPdfDocumentEngine(impl: PdfBoxAndroidEngine): PdfDocumentEngine

    @Binds
    @Singleton
    abstract fun bindPdfBoxInit(impl: PdfBoxInitializer): PdfBoxInit
}
