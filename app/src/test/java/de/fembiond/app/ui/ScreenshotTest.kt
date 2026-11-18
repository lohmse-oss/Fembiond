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
import de.fembiond.app.ui.screens.UnrestScreen
import de.fembiond.app.ui.screens.EventsScreen
import de.fembiond.app.ui.screens.MoreScreen
import de.fembiond.app.ui.screens.ExportScreen
import de.fembiond.app.ui.screens.SettingsScreen
import de.fembiond.app.ui.screens.HistoryScreen
import de.fembiond.app.ui.theme.FembiondTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun androidx.compose.ui.Modifier.androidxPadding() = this.padding(24.dp)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val data = SampleData.build()
    private val dir = "../docs/screenshots"

    private fun shot(name: String, dark: Boolean = false, content: @Composable (AppViewModel) -> Unit) {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { FembiondTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { content(vm) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$dir/$name.png")
    }

    @Config(qualifiers = "w412dp-h2200dp-xxhdpi")
    @Test fun home() = shot("01_heute") { HomeScreen(it, data, rememberNavController()) }
    @Test fun homeDark() = shot("01_heute_dunkel", dark = true) { HomeScreen(it, data, rememberNavController()) }
    @Test fun entry() = shot("02_eingabe") {
        EntryScreen(it, data, rememberNavController(), null, false, "Bin heute ziemlich müde, hab seit Mittag Kopfweh und so eine innere Unruhe, so Stärke drei. Ohrensausen")
    }
    @Test fun unrest() = shot("03_unruhe") { UnrestScreen(it, data, rememberNavController()) }
    @Test fun events() = shot("08_ereignisse") { EventsScreen(it, data, rememberNavController()) }
    @Test fun appIcon() = shot("00_app_symbol") {
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.androidxPadding()) {
            for (shape in listOf(androidx.compose.foundation.shape.CircleShape, androidx.compose.foundation.shape.RoundedCornerShape(28)))
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.size(160.dp).clip(shape),
                ) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(de.fembiond.app.R.drawable.ic_launcher_background), null, androidx.compose.ui.Modifier.fillMaxSize())
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(de.fembiond.app.R.drawable.ic_launcher_foreground), null, androidx.compose.ui.Modifier.fillMaxSize())
                }
        }
    }
    @Test fun more() = shot("09_mehr") { MoreScreen(data, rememberNavController()) }
    @Test fun export() = shot("10_export") { ExportScreen(it, data, rememberNavController()) }
    @Test fun settings() = shot("11_einstellungen") { SettingsScreen(it, data, rememberNavController()) }
    @Test fun history() = shot("12_historie") { HistoryScreen(it, data, rememberNavController()) }

    // Große Systemschrift (Pixel: Schriftgröße „Groß“)
    @Config(qualifiers = "w412dp-h3000dp-xxhdpi", fontScale = 1.3f)
    @Test fun homeLargeFont() = shot("90_heute_grosse_schrift") { HomeScreen(it, data, rememberNavController()) }
    @Config(qualifiers = "w412dp-h3000dp-xxhdpi", fontScale = 1.3f)
    @Test fun unrestLargeFont() = shot("91_unruhe_grosse_schrift") { UnrestScreen(it, data, rememberNavController()) }
    @Config(qualifiers = "w412dp-h7000dp-xxhdpi", fontScale = 1.3f)
    @Test fun analysisLargeFont() = shot("92_auswertung_grosse_schrift") { AnalysisScreen(data, rememberNavController()) }
    @Config(qualifiers = "w412dp-h3000dp-xxhdpi", fontScale = 1.3f)
    @Test fun dayLargeFont() = shot("93_tag_grosse_schrift") { DayScreen(it, data, rememberNavController(), data.today) }
    @Config(qualifiers = "w412dp-h3000dp-xxhdpi", fontScale = 1.3f)
    @Test fun medsLargeFont() = shot("94_medikation_grosse_schrift") { MedicationScreen(it, data, rememberNavController()) }
    @Config(qualifiers = "w412dp-h3000dp-xxhdpi", fontScale = 1.3f)
    @Test fun eventsLargeFont() = shot("95_ereignisse_grosse_schrift") { EventsScreen(it, data, rememberNavController()) }
    @Test fun calendar() = shot("04_kalender") { CalendarScreen(data, rememberNavController()) }
    @Test fun day() = shot("05_tag") { DayScreen(it, data, rememberNavController(), data.today) }
    @Test fun meds() = shot("07_medikation") { MedicationScreen(it, data, rememberNavController()) }

    @Config(qualifiers = "w412dp-h5600dp-xxhdpi")
    @Test fun analysis() = shot("06_auswertung") { AnalysisScreen(data, rememberNavController()) }

    @Config(qualifiers = "w412dp-h5600dp-xxhdpi")
    @Test fun analysisDark() = shot("06_auswertung_dunkel", dark = true) { AnalysisScreen(data, rememberNavController()) }
}
