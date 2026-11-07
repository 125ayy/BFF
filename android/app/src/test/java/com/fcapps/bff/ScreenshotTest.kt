package com.fcapps.bff

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.fcapps.bff.Prefs.finderMessage
import com.fcapps.bff.Prefs.permissionSetupVersion
import com.fcapps.bff.Prefs.setupDone
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the main screens with real Android layout + native drawing and writes PNGs to
 * build/reports/screens/<size>/<scale>/<screen>.png. Fails if any primary action is
 * clipped (its bottom edge beyond the visible viewport without the screen being scrollable).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val out = File(System.getProperty("screens.dir") ?: "build/reports/screens")

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi") fun normalPhone_100() = renderAll("normal", 1.0f)
    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi") fun normalPhone_200() = renderAll("normal", 2.0f)
    @Test @Config(qualifiers = "w320dp-h533dp-hdpi") fun smallPhone_100() = renderAll("small", 1.0f)
    @Test @Config(qualifiers = "w320dp-h533dp-hdpi") fun smallPhone_200() = renderAll("small", 2.0f)

    private fun renderAll(size: String, scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        app.finderMessage = "Contact owner at email: david@example.com"
        app.setupDone = true; app.permissionSetupVersion = Prefs.SETUP_VERSION
        val dir = File(out, "$size/${(scale * 100).toInt()}").apply { mkdirs() }
        val problems = mutableListOf<String>()

        shoot(dir, "ready", ReadyActivity::class.java, problems, R.id.btnReady, R.id.btnPrivacy)
        shoot(dir, "settings", SettingsActivity::class.java, problems, R.id.rowSound)
        shoot(dir, "sound", SoundActivity::class.java, problems, R.id.pill60)
        shoot(dir, "location", LocationActivity::class.java, problems, R.id.swLocation)
        shoot(dir, "privacy", PrivacyActivity::class.java, problems, R.id.btnDone)
        shoot(dir, "setup", SetupActivity::class.java, problems, R.id.btnContinue)
        shoot(dir, "alarm", AlarmActivity::class.java, problems, R.id.btnFound)
        // Home is SplashActivity after its 1.5 s hop
        val splash = Robolectric.buildActivity(SplashActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1600))
        capture(dir, "home", splash.get(), problems, R.id.btnShare, R.id.btnAbout)

        assertTrue("Clipped primary actions:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun <T : Activity> shoot(dir: File, name: String, cls: Class<T>, problems: MutableList<String>, vararg mustBeReachable: Int) {
        val activity = Robolectric.buildActivity(cls).setup().get()
        capture(dir, name, activity, problems, *mustBeReachable)
    }

    private fun capture(dir: File, name: String, activity: Activity, problems: MutableList<String>, vararg mustBeReachable: Int) {
        val root = activity.window.decorView
        val dm = activity.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val scroller = findScrollable(root)
        for (id in mustBeReachable) {
            val v = activity.findViewById<View>(id) ?: continue
            val loc = IntArray(2); v.getLocationInWindow(loc)
            val bottom = loc[1] + v.height
            val clippedByViewport = bottom > dm.heightPixels && scroller == null
            val zeroSized = v.width == 0 || v.height == 0
            if (clippedByViewport || zeroSized) problems += "$name: view ${activity.resources.getResourceEntryName(id)} bottom=$bottom viewport=${dm.heightPixels} size=${v.width}x${v.height}"
        }
    }

    private fun findScrollable(v: View): View? {
        if (v is android.widget.ScrollView || v is androidx.core.widget.NestedScrollView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findScrollable(v.getChildAt(i))?.let { return it }
        return null
    }
}
