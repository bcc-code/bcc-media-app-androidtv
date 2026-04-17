package ca.kloosterman.bccmediatv.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    object Loading : LoginUiState()
    data class ShowCode(
        val userCode: String,
        val verificationUriComplete: String
    ) : LoginUiState()
    object Authenticated : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val state: StateFlow<LoginUiState> = _state

    init {
        startDeviceFlow()
    }

    fun startDeviceFlow() {
        viewModelScope.launch {
            _state.value = LoginUiState.Loading
            authRepository.startDeviceFlow()
                .onSuccess { response ->
                    _state.value = LoginUiState.ShowCode(
                        userCode = response.userCode,
                        verificationUriComplete = response.verificationUriComplete
                    )
                    authRepository.pollForToken(response.deviceCode, response.interval)
                        .collect { result ->
                            when (result) {
                                is PollResult.Success -> _state.value = LoginUiState.Authenticated
                                is PollResult.Error -> _state.value = LoginUiState.Error(result.message)
                                PollResult.Pending -> { /* keep showing code */ }
                            }
                        }
                }
                .onFailure { e ->
                    _state.value = LoginUiState.Error(e.message ?: "Failed to start login")
                }
        }
    }
}
