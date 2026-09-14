package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object DocEditor : Screen("doc_editor")
    object MusicStudio : Screen("music_studio")
    object Chat : Screen("chat")
    object Profile : Screen("profile")
}
