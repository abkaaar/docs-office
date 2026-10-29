package com.docsoffice.app.core.crypto.di

import com.docsoffice.app.core.crypto.TinkVaultCrypto
import com.docsoffice.app.core.crypto.VaultCrypto
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    @Singleton
    abstract fun bindVaultCrypto(impl: TinkVaultCrypto): VaultCrypto
}
