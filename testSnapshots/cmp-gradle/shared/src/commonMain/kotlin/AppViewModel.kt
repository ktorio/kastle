package com.acme

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class AppViewModel : ViewModel() {
    var showContent by mutableStateOf(false)
        private set

    val greeting: String by lazy { Greeting().greet() }

    fun onToggleContent() {
        showContent = !showContent
    }
}