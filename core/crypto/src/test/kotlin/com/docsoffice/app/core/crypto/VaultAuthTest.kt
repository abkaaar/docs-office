package com.docsoffice.app.core.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultAuthTest {

    private lateinit var auth: VaultAuth

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        auth = VaultAuth(context)
    }

    @Test
    fun setAndVerifyPin() = runTest {
        auth.setPin("2468")
        assertThat(auth.isPinConfigured()).isTrue()
        assertThat(auth.verifyPin("2468")).isTrue()
        assertThat(auth.verifyPin("0000")).isFalse()
    }
}
