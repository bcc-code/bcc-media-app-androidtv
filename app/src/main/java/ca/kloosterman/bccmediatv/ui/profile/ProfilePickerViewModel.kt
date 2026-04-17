package ca.kloosterman.bccmediatv.ui.profile

import androidx.lifecycle.ViewModel
import ca.kloosterman.bccmediatv.auth.Profile
import ca.kloosterman.bccmediatv.auth.ProfileStore
import ca.kloosterman.bccmediatv.data.LanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val profileStore: ProfileStore,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    data class UiState(
        val profiles: List<Profile> = emptyList(),
        val activeProfileId: String? = null
    )

    private val _state = MutableStateFlow(
        UiState(
            profiles = profileStore.getProfiles(),
            activeProfileId = profileStore.activeProfileId
        )
    )
    val state: StateFlow<UiState> = _state

    fun refresh() {
        _state.value = UiState(
            profiles = profileStore.getProfiles(),
            activeProfileId = profileStore.activeProfileId
        )
    }

    fun switchTo(profile: Profile) {
        profileStore.activeProfileId = profile.userId
        profileStore.saveProfile(profile.copy(lastUsed = System.currentTimeMillis()))
        _state.value = UiState(
            profiles = profileStore.getProfiles(),
            activeProfileId = profile.userId
        )
        languageRepository.emitLanguageChanged()
    }
}
