package com.resqlink.feature.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resqlink.R
import com.resqlink.core.ui.OrbitMark
import com.resqlink.core.ui.theme.ResQLinkTheme
import com.resqlink.feature.contacts.ContactsScreen
import com.resqlink.feature.contacts.ContactsViewModel
import com.resqlink.feature.history.HistoryScreen
import com.resqlink.feature.history.HistoryViewModel
import com.resqlink.feature.home.AlertDraft
import com.resqlink.feature.home.HomeScreen
import com.resqlink.feature.home.HomeViewModel
import com.resqlink.feature.onboarding.OnboardingScreen
import com.resqlink.feature.onboarding.OnboardingViewModel
import com.resqlink.feature.profile.ProfileScreen
import com.resqlink.feature.profile.ProfileViewModel
import com.resqlink.feature.settings.DiagnosticsScreen
import com.resqlink.feature.settings.SettingsScreen
import com.resqlink.feature.settings.SettingsViewModel

private enum class Route(val path: String, val label: Int, val icon: ImageVector) {
    HOME("home", R.string.home, Icons.Rounded.Home),
    CONTACTS("contacts", R.string.contacts, Icons.Rounded.Contacts),
    HISTORY("history", R.string.history, Icons.Rounded.History),
    SETTINGS("settings", R.string.settings, Icons.Rounded.Settings),
}

private const val PROFILE = "profile"
private const val DIAGNOSTICS = "diagnostics"

@Composable
fun AppRoot(
    appViewModel: AppViewModel,
    onboardingViewModel: OnboardingViewModel,
    homeViewModel: HomeViewModel,
    contactsViewModel: ContactsViewModel,
    profileViewModel: ProfileViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestLocation: () -> Unit,
    onOpenAlert: (AlertDraft) -> Unit,
) {
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()
    val onboardingMessage by onboardingViewModel.userMessage.collectAsStateWithLifecycle()
    ResQLinkTheme(appState.settings.theme) {
        AnimatedContent(
            targetState = Triple(appState.storageUnavailable, appState.loading, appState.settings.onboardingComplete),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "app_state",
        ) { (unavailable, loading, complete) ->
            when {
                unavailable -> StorageUnavailableScreen(appViewModel::retryStorage)
                loading -> SplashScreen()
                !complete -> OnboardingScreen(userMessage = onboardingMessage, onComplete = onboardingViewModel::complete)
                else -> MainNavigation(
                    homeViewModel,
                    contactsViewModel,
                    profileViewModel,
                    historyViewModel,
                    settingsViewModel,
                    onRequestLocation,
                    onOpenAlert,
                )
            }
        }
    }
}

@Composable
internal fun StorageUnavailableScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Saved data is unavailable", style = MaterialTheme.typography.headlineSmall)
        Text("Your saved files have been kept. Check that your device is unlocked and has free storage, then retry. If this continues, contact support before clearing app data.")
        Text("For urgent help, use your phone's dialer to call your local emergency number.")
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) { OrbitMark(Modifier.size(96.dp)) }
}

@Composable
private fun MainNavigation(
    homeViewModel: HomeViewModel,
    contactsViewModel: ContactsViewModel,
    profileViewModel: ProfileViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestLocation: () -> Unit,
    onOpenAlert: (AlertDraft) -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val mainRoute = Route.entries.any { route -> destination?.hierarchy?.any { it.route == route.path } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (mainRoute) {
                NavigationBar(
                    Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Route.entries.forEach { route ->
                        val selected = destination?.hierarchy?.any { it.route == route.path } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route.path) {
                                    popUpTo(Route.HOME.path) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(route.icon, stringResource(route.label)) },
                            label = { Text(stringResource(route.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.HOME.path,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Route.HOME.path) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onContacts = { navController.navigate(Route.CONTACTS.path) },
                    onProfile = { navController.navigate(PROFILE) },
                    onHistory = { navController.navigate(Route.HISTORY.path) },
                    onRequestLocation = onRequestLocation,
                    onOpenAlert = onOpenAlert,
                )
            }
            composable(Route.CONTACTS.path) { ContactsScreen(contactsViewModel) }
            composable(Route.HISTORY.path) { HistoryScreen(historyViewModel) }
            composable(Route.SETTINGS.path) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onProfile = { navController.navigate(PROFILE) },
                    onContacts = { navController.navigate(Route.CONTACTS.path) },
                    onDiagnostics = { navController.navigate(DIAGNOSTICS) },
                )
            }
            composable(PROFILE) { ProfileScreen(profileViewModel, onBack = navController::popBackStack) }
            composable(DIAGNOSTICS) { DiagnosticsScreen(settingsViewModel, onBack = navController::popBackStack) }
        }
    }
}
