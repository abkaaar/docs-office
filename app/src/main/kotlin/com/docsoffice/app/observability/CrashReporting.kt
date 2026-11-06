package com.docsoffice.app.observability

import android.util.Log
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class CrashReporting @Inject constructor(
    private val preferences: UserPreferencesRepository,
) {
    fun start(scope: CoroutineScope) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        }
        ReleaseTree.crashCollectionEnabled = false

        scope.launch {
            preferences.analyticsEnabled.distinctUntilChanged().collect { enabled ->
                ReleaseTree.crashCollectionEnabled = enabled
                runCatching {
                    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
                }.onFailure {
                    Timber.w("Crashlytics unavailable")
                }
            }
        }
    }
}

/** Release Timber tree: warnings and above only; never log file names/paths. */
class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        val safe = message
            .replace(Regex("""(/|file:|content:)[^\s]+"""), "[redacted]")
        if (t != null && crashCollectionEnabled) {
            runCatching {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        }
        Log.println(priority, tag ?: "DocsOffice", safe)
    }

    companion object {
        @Volatile
        var crashCollectionEnabled: Boolean = false
    }
}
