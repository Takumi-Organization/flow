package dev.flow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import dev.flow.app.platform.AppState
import dev.flow.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as FlowApp).graph
        setContent {
            var theme by remember { mutableStateOf(graph.prefs.theme) }
            var accent by remember { mutableIntStateOf(graph.prefs.accent) }
            var route by rememberSaveable { mutableStateOf(if (graph.prefs.onboarded) "home" else "tutorial") }
            val back = remember { mutableStateListOf<String>() }
            fun go(r: String) { back.add(route); route = r }
            fun tab(r: String) { back.clear(); route = r }
            FlowTheme(theme, accent) {
                Box(Modifier.fillMaxSize().background(k.paper)) {
                    val isTab = route in setOf("home", "library", "history", "settings")
                    BackHandler(enabled = !isTab || route != "home") { if (back.isNotEmpty()) route = back.removeAt(back.lastIndex) else route = "home" }
                    when {
                        route == "tutorial" -> Tutorial(graph) { graph.prefs.onboarded = true; tab(it) }
                        route.startsWith("editor:") -> EditorScreen(graph, route.removePrefix("editor:"), { if (back.isNotEmpty()) route = back.removeAt(back.lastIndex) else route = "home" }, { go("run:$it") })
                        route.startsWith("run:") -> RunDetailScreen(graph, route.removePrefix("run:")) { if (back.isNotEmpty()) route = back.removeAt(back.lastIndex) else route = "history" }
                        else -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                when (route) {
                                    "library" -> LibraryScreen(graph) { go("editor:$it") }
                                    "history" -> HistoryScreen(graph) { go("run:$it") }
                                    "settings" -> SettingsScreen(graph, theme, accent, { theme = it; graph.prefs.theme = it }, { accent = it; graph.prefs.accent = it }) { go("tutorial") }
                                    else -> HomeScreen(graph, { go("editor:$it") }, { go("run:$it") })
                                }
                            }
                            BottomNav(route) { tab(it) }
                        }
                    }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); AppState.inForeground = true }
    override fun onStop() { AppState.inForeground = false; super.onStop() }
}
