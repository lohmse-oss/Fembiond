package de.fembiond.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import java.time.LocalDate

/** Geteilte Übergänge: Tag im Wochenstreifen/Kalender „wächst“ in die Tagesansicht. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun ProvideNavAnim(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavAnimScope provides scope, content = content)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedDay(date: LocalDate): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimScope.current ?: return this
    return with(shared) {
        this@sharedDay.sharedBounds(
            rememberSharedContentState("day-${date.toEpochDay()}"),
            animatedVisibilityScope = anim,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
