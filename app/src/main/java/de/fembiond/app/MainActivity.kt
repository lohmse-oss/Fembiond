package de.fembiond.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.fembiond.app.data.AppData
import de.fembiond.app.ui.AppViewModel
import de.fembiond.app.ui.screens.AnalysisScreen
import de.fembiond.app.ui.screens.CalendarScreen
import de.fembiond.app.ui.screens.DayScreen
import de.fembiond.app.ui.screens.EntryScreen
import de.fembiond.app.ui.screens.ExportScreen
import de.fembiond.app.ui.screens.GadScreen
import de.fembiond.app.ui.screens.HistoryScreen
import de.fembiond.app.ui.screens.HomeScreen
import de.fembiond.app.ui.screens.MedicationScreen
import de.fembiond.app.ui.screens.MoreScreen
import de.fembiond.app.ui.screens.OnboardingScreen
import de.fembiond.app.ui.screens.UnrestScreen
import de.fembiond.app.ui.screens.EventsScreen
import de.fembiond.app.ui.screens.SettingsScreen
import de.fembiond.app.ui.screens.TermsScreen
import de.fembiond.app.ui.theme.FembiondTheme
import java.time.LocalDate

class MainActivity : FragmentActivity() {

    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: AppViewModel = viewModel()
            val data by vm.data.collectAsState()
            val d = data
            FembiondTheme(d?.settings?.theme ?: de.fembiond.app.data.ThemeMode.SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        d == null -> Box(Modifier.fillMaxSize())
                        d.settings.lockEnabled && !unlocked -> LockScreen { authenticate() }
                        !d.settings.onboardingDone -> OnboardingScreen(vm, d)
                        else -> AppNav(vm, d, intent?.getStringExtra(EXTRA_ROUTE))
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) unlocked = false
    }

    fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Fembiond entsperren")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Fembiond ist gesperrt", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        Button(onClick = onUnlock) { Text("Entsperren") }
    }
}

object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val ANALYSIS = "analysis"
    const val MORE = "more"
    const val ENTRY = "entry?day={day}&note={note}"
    const val UNREST = "unrest?event={event}&day={day}"
    const val EVENTS = "events"
    const val ABOUT = "about"
    const val MRS = "mrs"
    const val WEEKLY = "weekly"
    const val GUIDE = "guide?open={open}"
    fun guide(open: String? = null) = "guide?open=${open ?: ""}"
    const val DAY = "day/{day}"
    const val MEDS = "meds"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val TERMS = "terms"
    const val GAD = "gad"
    const val HISTORY = "history"

    fun entry(day: LocalDate? = null, note: Boolean = false) = "entry?day=${day?.toEpochDay() ?: -1}&note=$note"
    fun day(d: LocalDate) = "day/${d.toEpochDay()}"
    fun unrest(eventId: Long? = null, day: LocalDate? = null) = "unrest?event=${eventId ?: -1}&day=${day?.toEpochDay() ?: -1}"
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Heute", Icons.Rounded.Spa),
    Tab(Routes.CALENDAR, "Kalender", Icons.Rounded.CalendarMonth),
    Tab(Routes.ANALYSIS, "Auswertung", Icons.Rounded.Analytics),
    Tab(Routes.MORE, "Mehr", Icons.Rounded.MoreHoriz),
)

@Composable
private fun AppNav(vm: AppViewModel, data: AppData, startRoute: String?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    LaunchedEffect(startRoute) { if (startRoute != null) nav.navigate(startRoute) }
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == current }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, Routes.HOME, Modifier.padding(bottom = pad.calculateBottomPadding()),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 12 } },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(240)) { it / 12 } },
        ) {
            composable(Routes.HOME) { HomeScreen(vm, data, nav) }
            composable(Routes.CALENDAR) { CalendarScreen(data, nav) }
            composable(Routes.ANALYSIS) { AnalysisScreen(data, nav) }
            composable(Routes.MORE) { MoreScreen(data, nav) }
            composable(Routes.ENTRY) { b ->
                val day = b.arguments?.getString("day")?.toLongOrNull()?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)
                val note = b.arguments?.getString("note") == "true"
                EntryScreen(vm, data, nav, day, note)
            }
            composable(Routes.UNREST) { b ->
                val ev = b.arguments?.getString("event")?.toLongOrNull()?.takeIf { it >= 0 }
                val day = b.arguments?.getString("day")?.toLongOrNull()?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)
                UnrestScreen(vm, data, nav, ev, day)
            }
            composable(Routes.EVENTS) { EventsScreen(vm, data, nav) }
            composable(Routes.MRS) { de.fembiond.app.ui.screens.MrsScreen(vm, data, nav) }
            composable(Routes.WEEKLY) { de.fembiond.app.ui.screens.WeeklyReviewScreen(data, nav) }
            composable(Routes.ABOUT) { de.fembiond.app.ui.screens.AboutScreen(data, nav) }
            composable(Routes.GUIDE) { b -> de.fembiond.app.ui.screens.GuideScreen(nav, b.arguments?.getString("open")?.takeIf { it.isNotBlank() }) }
            composable(Routes.DAY) { b ->
                DayScreen(vm, data, nav, LocalDate.ofEpochDay(b.arguments?.getString("day")!!.toLong()))
            }
            composable(Routes.MEDS) { MedicationScreen(vm, data, nav) }
            composable(Routes.EXPORT) { ExportScreen(vm, data, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, data, nav) }
            composable(Routes.TERMS) { TermsScreen(vm, data, nav) }
            composable(Routes.GAD) { GadScreen(vm, data, nav) }
            composable(Routes.HISTORY) { HistoryScreen(vm, data, nav) }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
