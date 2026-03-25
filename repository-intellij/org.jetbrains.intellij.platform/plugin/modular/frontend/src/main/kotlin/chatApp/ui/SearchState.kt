package org.jetbrains.plugins.template.chatApp.ui

sealed class SearchState {
    object Idle : SearchState()

    data class Searching(val query: String) : SearchState()

    data class SearchResults(
        val query: String = "",
        // List of message IDs that match search
        val searchResultIds: List<String> = emptyList(),
        val currentSelectedSearchResultIndex: Int = -1
    ) : SearchState()
}

val SearchState.isSearching: Boolean get() = this is SearchState.Searching || this is SearchState.SearchResults

val SearchState.hasResults: Boolean get() = this is SearchState.SearchResults && searchResultIds.isNotEmpty()

val SearchState.totalResults: Int
    get() = when (this) {
        is SearchState.Idle, is SearchState.Searching -> -1
        is SearchState.SearchResults -> searchResultIds.size
    }

val SearchState.currentSearchResultIndex: Int
    get() = when (this) {
        is SearchState.Idle, is SearchState.Searching -> -1
        is SearchState.SearchResults -> currentSelectedSearchResultIndex
    }

val SearchState.searchQuery: String?
    get() = when (this) {
        is SearchState.Idle -> null
        is SearchState.Searching -> query
        is SearchState.SearchResults -> query
    }

val SearchState.searchResultIds: List<String>
    get() = when (this) {
        is SearchState.Idle -> emptyList()
        is SearchState.Searching -> emptyList()
        is SearchState.SearchResults -> searchResultIds
    }

// Corresponds to chat message id
val SearchState.currentSelectedSearchResultId: String? get() = searchResultIds.getOrNull(currentSearchResultIndex)
