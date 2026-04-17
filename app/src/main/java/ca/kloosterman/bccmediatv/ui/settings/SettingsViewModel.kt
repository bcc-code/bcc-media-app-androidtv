package ca.kloosterman.bccmediatv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kloosterman.bccmediatv.auth.AuthRepository
import ca.kloosterman.bccmediatv.data.LanguageRepository
import ca.kloosterman.bccmediatv.graphql.GetMeQuery
import com.apollographql.apollo.ApolloClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apollo: ApolloClient,
    private val authRepository: AuthRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    data class UiState(
        val displayName: String? = null,
        val isLoading: Boolean = true,
        val selectedLanguage: String = LanguageRepository.DEFAULT,
        val selectedAudioLanguage: String = LanguageRepository.DEFAULT,
        val selectedSubtitleLanguage: String? = null,
        val autoPlayDelay: Int = 10,
        val hasMultipleProfiles: Boolean = false
    )

    private val _state = MutableStateFlow(
        UiState(
            selectedLanguage = languageRepository.getLanguage(),
            selectedAudioLanguage = languageRepository.getAudioLanguage(),
            selectedSubtitleLanguage = languageRepository.getSubtitleLanguage(),
            autoPlayDelay = languageRepository.getAutoPlayDelay(),
            hasMultipleProfiles = authRepository.hasMultipleProfiles()
        )
    )
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            runCatching {
                apollo.query(GetMeQuery()).execute().dataOrThrow()
            }.onSuccess {
                _state.value = _state.value.copy(isLoading = false)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun setLanguage(code: String) {
        languageRepository.setLanguage(code)
        _state.value = _state.value.copy(selectedLanguage = code)
    }

    fun setAudioLanguage(code: String) {
        languageRepository.setAudioLanguage(code)
        _state.value = _state.value.copy(selectedAudioLanguage = code)
    }

    fun setSubtitleLanguage(code: String?) {
        languageRepository.setSubtitleLanguage(code)
        _state.value = _state.value.copy(selectedSubtitleLanguage = code)
    }

    fun setAutoPlayDelay(seconds: Int) {
        languageRepository.setAutoPlayDelay(seconds)
        _state.value = _state.value.copy(autoPlayDelay = seconds)
    }

    /**
     * Removes the current profile and its tokens.
     * Returns true if another profile was auto-selected (caller should recreate the Activity),
     * false if no profiles remain (caller should navigate to login).
     */
    fun logout(): Boolean {
        val hasOthers = authRepository.hasMultipleProfiles()
        if (!hasOthers) {
            languageRepository.clearLanguage()
        }
        authRepository.removeCurrentProfile()
        return authRepository.isLoggedIn()
    }
}
