package de.utrost.textr

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** Shared native-screen chrome for comparison and explicit copy editing. */
abstract class ReadingScreen : ComponentActivity() {
    protected var dark = false
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun foreground() = Color.parseColor(if (dark) "#E3E9E5" else "#202923")
    protected fun background() = Color.parseColor(if (dark) "#171C1A" else "#FAFAF7")
    override fun onCreate(savedInstanceState: Bundle?) {
        dark = when (getSharedPreferences("reading", 0).getString("theme", "system")) {
            "dark" -> true; "light" -> false
            else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (dark) R.style.Theme_Textr_Dark else R.style.Theme_Textr)
        super.onCreate(savedInstanceState)
        val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    protected fun install(root: View) {
        root.setBackgroundColor(background())
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
    }
    protected fun button(label: Int, action: () -> Unit) = Button(this).apply {
        setText(label); isAllCaps = false; minHeight = dp(48); setOnClickListener { action() }
    }
}
