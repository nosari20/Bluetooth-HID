package com.fnosari.bluetoothhid.activities.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fnosari.bluetoothhid.activities.main.components.PermissionsGate
import com.fnosari.bluetoothhid.activities.main.screens.DevicesScreen
import com.fnosari.bluetoothhid.activities.main.screens.HomeScreen
import com.fnosari.bluetoothhid.services.HIDService
import com.fnosari.bluetoothhid.ui.theme.BluetoothHIDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothHIDTheme {
                PermissionsGate {
                    val context = LocalContext.current
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { HIDService.start(context) }
                    MainActivityNavigation()
                }
            }
        }
    }

    /**
     * Routes for the main activity
     */
    enum class Routes {
        HOME,
        DEVICES
    }
}

/**
 * Navigation for the main activity
 */
@Composable
fun MainActivityNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
        startDestination = MainActivity.Routes.HOME.name
    ) {
        composable(MainActivity.Routes.HOME.name) { HomeScreen(navController) }
        composable(MainActivity.Routes.DEVICES.name) { DevicesScreen(navController) }
    }
}
