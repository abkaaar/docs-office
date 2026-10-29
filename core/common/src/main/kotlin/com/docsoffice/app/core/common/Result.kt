package com.docsoffice.app.core.common

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()
}

inline fun <T> runCatchingApp(block: () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (t: Throwable) {
        AppResult.Error(t.message ?: "Unknown error", t)
    }
}
