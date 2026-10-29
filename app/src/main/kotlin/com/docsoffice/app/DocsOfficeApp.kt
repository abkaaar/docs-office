package com.docsoffice.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.docsoffice.app.core.crypto.VaultRepository
import com.docsoffice.app.core.crypto.VaultSession
import com.docsoffice.app.core.files.TrashPurgeWorker
import com.docsoffice.app.core.pdftools.PdfBoxInit
import com.docsoffice.app.observability.CrashReporting
import com.docsoffice.app.observability.ReleaseTree
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class DocsOfficeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var vaultSession: VaultSession

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    lateinit var crashReporting: CrashReporting

    @Inject
    lateinit var pdfBoxInit: PdfBoxInit

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        crashReporting.start(appScope)
        pdfBoxInit.ensureInitialized()
        TrashPurgeWorker.enqueue(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    vaultSession.lock()
                    vaultRepository.clearDecryptCache()
                }
            },
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
