package com.docsoffice.app.observability

import com.docsoffice.app.core.datastore.UserPreferencesRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporting @Inject constructor(
    private val preferences: UserPreferencesRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            preferences.analyticsEnabled.distinctUntilChanged().collect { enabled ->
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
        if (priority < android.util.Log.WARN) return
        val safe = message
            .replace(Regex("""(/|file:|content:)[^\s]+"""), "[redacted]")
        if (t != null) {
            runCatching {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        }
        android.util.Log.println(priority, tag ?: "DocsOffice", safe)
    }
}
