package ca.kloosterman.bccmediatv.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.kloosterman.bccmediatv.data.MyListRepository
import ca.kloosterman.bccmediatv.data.ShowBookmarkItem
import ca.kloosterman.bccmediatv.graphql.GetMyListQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyListViewModel @Inject constructor(
    private val repository: MyListRepository
) : ViewModel() {

    val entries: StateFlow<List<GetMyListQuery.Item>> = repository.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val showBookmarks: StateFlow<List<ShowBookmarkItem>> = repository.showBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading: StateFlow<Boolean> = repository.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val error: StateFlow<String?> = repository.error
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { repository.load() }
    }

    fun reload() {
        viewModelScope.launch { repository.load(force = true) }
    }
}
