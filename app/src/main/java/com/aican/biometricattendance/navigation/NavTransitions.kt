package com.aican.biometricattendance.navigation


import androidx.compose.animation.*
import androidx.compose.animation.core.tween

val slideUpDownEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(500)
    )
}

val slideUpDownExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Down,
        animationSpec = tween(500)
    )
}

val slideUpDownPopEnterTransition: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(500)
    )
}

val slideUpDownPopExitTransition: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Down,
        animationSpec = tween(500)
    )
}