package com.docsoffice.app.core.files

import com.docsoffice.app.core.model.ScanProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanProgressStore @Inject constructor() {
    private val _progress = MutableStateFlow(
        ScanProgress(foundCount = 0, isComplete = true, message = ""),
    )
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    fun update(foundCount: Int, isComplete: Boolean, message: String = "") {
        _progress.value = ScanProgress(foundCount, isComplete, message)
    }
}
