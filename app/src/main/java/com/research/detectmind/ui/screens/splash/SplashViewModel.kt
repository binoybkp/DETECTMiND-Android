package com.research.detectmind.ui.screens.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.detectmind.data.repository.EnrollmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SplashState {
    object Loading : SplashState
    object Enrolled : SplashState
    object NotEnrolled : SplashState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    dataStore: DataStore<Preferences>
) : ViewModel() {

    val state = dataStore.data
        .map { prefs ->
            if (prefs[EnrollmentRepository.KEY_ENROLLED] == true) SplashState.Enrolled
            else SplashState.NotEnrolled
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SplashState.Loading)
}
