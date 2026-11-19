package dev.flow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.flow.app.platform.AppState
import dev.flow.app.ui.*
import kotlinx.coroutines.launch

private val TAB_ROUTES = setOf("home", "library", "history", "settings")

/**
 * Gesto de «atrás» desde el borde izquierdo, como en iOS: la pantalla sigue al dedo con escala y atenuado, y al soltar
 * vuelve con muelle o se cierra. Con resistencia (rubber-band) si se tira hacia el borde contrario.
 */
@Composable
private fun Modifier.edgeSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier {
    val x = remember { Animatable(0f) }; val scope = rememberCoroutineScope(); val view = LocalView.current; val rm = reduceMotion()
    return this.graphicsLayer {
        val p = (x.value / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        translationX = x.value; scaleX = 1f - .05f * p; scaleY = scaleX; alpha = 1f - .25f * p
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val edge = 28.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > edge) return@awaitEachGesture
            var vx = 0f
            horizontalDrag(down.id) { ch ->
                val dx = ch.positionChange().x; vx = dx
                val next = x.value + dx
                scope.launch { x.snapTo(if (next < 0f) next * .25f else next) }
                ch.consume()
            }
            val w = size.width.toFloat()
            scope.launch {
                if (x.value > w * .32f || vx > 24f) { view.hap(Hap.CLICK); if (rm) x.snapTo(w) else x.animateTo(w, spring(.9f, 500f)); onBack(); x.snapTo(0f) }
                else x.animateTo(0f, if (rm) tween(0) else spring(.65f, 420f))
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    /** Enlace a un flujo (extra «flowId», p. ej. desde una notificación): abre su editor, también con la app ya abierta. */
    private val openFlow = mutableStateOf<String?>(null)
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); openFlow.value = intent.getStringExtra("flowId") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as FlowApp).graph
        val firstFlow = intent?.getStringExtra("flowId")
        setContent {
            var theme by remember { mutableStateOf(graph.prefs.theme) }
            var accent by remember { mutableIntStateOf(graph.prefs.accent) }
            var route by rememberSaveable { mutableStateOf(if (graph.prefs.onboarded) (firstFlow?.let { "editor:$it" } ?: "home") else "tutorial") }
            val back = remember { mutableStateListOf<String>() }
            fun go(r: String) { back.add(route); route = r }
            fun tab(r: String) { back.clear(); route = r }
            fun pop() { route = if (back.isNotEmpty()) back.removeAt(back.lastIndex) else "home" }
            LaunchedEffect(openFlow.value) { openFlow.value?.let { if (graph.prefs.onboarded) go("editor:$it"); openFlow.value = null } }
            val rm = reduceMotion()
            FlowTheme(theme, accent) {
                Box(Modifier.fillMaxSize().background(k.paper)) {
                    val isTab = route in TAB_ROUTES
                    BackHandler(enabled = !isTab || route != "home") { pop() }
                    val navGlass = dev.flow.app.ui.rememberGlassBackdrop()
                    Box(Modifier.fillMaxSize().glassSource(navGlass)) {
                    AnimatedContent(route, Modifier.fillMaxSize(), label = "route", transitionSpec = {
                        val toPush = targetState !in TAB_ROUTES && initialState in TAB_ROUTES
                        val fromPush = initialState !in TAB_ROUTES && targetState in TAB_ROUTES
                        val deeper = targetState.startsWith("run:") && initialState.startsWith("editor:")
                        when {
                            rm -> EnterTransition.None togetherWith ExitTransition.None
                            toPush || deeper -> (slideInHorizontally(spring(.86f, 360f)) { it } + fadeIn(tween(160))) togetherWith (slideOutHorizontally(spring(.9f, 360f)) { -it / 5 } + fadeOut(tween(220)) + scaleOut(tween(260), .96f))
                            fromPush -> (slideInHorizontally(spring(.9f, 380f)) { -it / 5 } + fadeIn(tween(200)) + scaleIn(tween(260), .96f)) togetherWith (slideOutHorizontally(spring(.9f, 400f)) { it } + fadeOut(tween(200)))
                            else -> (fadeIn(tween(240, 60)) + scaleIn(spring(.9f, 500f), .97f)) togetherWith fadeOut(tween(110))
                        }.apply { targetContentZIndex = if (toPush || deeper) 1f else 0f }
                    }) { r ->
                        Box(Modifier.fillMaxSize().background(k.paper).edgeSwipeBack(r !in TAB_ROUTES && r != "tutorial") { pop() }) {
                            when {
                                r == "tutorial" -> Tutorial(graph) { graph.prefs.onboarded = true; tab(it) }
                                r.startsWith("editor:") -> EditorScreen(graph, r.removePrefix("editor:"), { pop() }, { go("run:$it") })
                                r.startsWith("run:") -> RunDetailScreen(graph, r.removePrefix("run:")) { if (back.isNotEmpty()) pop() else route = "history" }
                                else -> when (r) {
                                    "library" -> LibraryScreen(graph) { go("editor:$it") }
                                    "history" -> HistoryScreen(graph) { go("run:$it") }
                                    "settings" -> SettingsScreen(graph, theme, accent, { theme = it; graph.prefs.theme = it }, { accent = it; graph.prefs.accent = it }) { go("tutorial") }
                                    else -> HomeScreen(graph, { go("editor:$it") }, { go("run:$it") })
                                }
                            }
                        }
                    }
                    }
                    AnimatedVisibility(isTab, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(spring(.8f, 400f)) { it } + fadeIn(), exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120))) {
                        androidx.compose.runtime.CompositionLocalProvider(dev.flow.app.ui.LocalGlassBackdrop provides navGlass) { BottomNav(route) { tab(it) } }
                    }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); AppState.inForeground = true }
    override fun onStop() { AppState.inForeground = false; super.onStop() }
}
