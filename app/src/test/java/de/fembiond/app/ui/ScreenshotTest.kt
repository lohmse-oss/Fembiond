package de.fembiond.app.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.fembiond.app.data.ThemeMode
import de.fembiond.app.ui.screens.AnalysisScreen
import de.fembiond.app.ui.screens.CalendarScreen
import de.fembiond.app.ui.screens.DayScreen
import de.fembiond.app.ui.screens.EntryScreen
import de.fembiond.app.ui.screens.HomeScreen
import de.fembiond.app.ui.screens.MedicationScreen
import de.fembiond.app.ui.screens.PanicScreen
import de.fembiond.app.ui.theme.FembiondTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val data = SampleData.build()
    private val dir = "../docs/screenshots"

    private fun shot(name: String, dark: Boolean = false, content: @Composable (AppViewModel) -> Unit) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { FembiondTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content(vm) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$dir/$name.png")
    }

    @Test fun home() = shot("01_heute") { HomeScreen(it, data, rememberNavController()) }
    @Test fun homeDark() = shot("01_heute_dunkel", dark = true) { HomeScreen(it, data, rememberNavController()) }
    @Test fun entry() = shot("02_eingabe") {
        EntryScreen(it, data, rememberNavController(), null, false, "Bin heute ziemlich müde, hab seit Mittag Kopfweh und so eine innere Unruhe, so Stärke drei. Ohrensausen")
    }
    @Test fun panic() = shot("03_panik") { PanicScreen(it, data, rememberNavController()) }
    @Test fun calendar() = shot("04_kalender") { CalendarScreen(data, rememberNavController()) }
    @Test fun day() = shot("05_tag") { DayScreen(it, data, rememberNavController(), data.today) }
    @Test fun meds() = shot("07_medikation") { MedicationScreen(it, data, rememberNavController()) }

    @Config(qualifiers = "w412dp-h3000dp-xxhdpi")
    @Test fun analysis() = shot("06_auswertung") { AnalysisScreen(data, rememberNavController()) }

    @Config(qualifiers = "w412dp-h3000dp-xxhdpi")
    @Test fun analysisDark() = shot("06_auswertung_dunkel", dark = true) { AnalysisScreen(data, rememberNavController()) }
}
