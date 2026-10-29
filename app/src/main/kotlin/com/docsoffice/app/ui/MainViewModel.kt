package com.docsoffice.app.ui

import androidx.lifecycle.ViewModel
import com.docsoffice.app.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val onboardingCompleted = preferences.onboardingCompleted
}
