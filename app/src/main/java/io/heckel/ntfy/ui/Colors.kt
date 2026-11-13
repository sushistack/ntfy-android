package io.heckel.ntfy.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import io.heckel.ntfy.R
import io.heckel.ntfy.util.isDarkThemeOn

class Colors {
    companion object {
        fun primary(context: Context): Int {
            return MaterialColors.getColor(context, R.attr.colorPrimary, Color.GREEN)
        }

        fun onPrimary(context: Context): Int {
            return MaterialColors.getColor(context, R.attr.colorOnPrimary, Color.GREEN)
        }

        fun notificationIcon(context: Context): Int {
            return MaterialColors.getColor(context, R.attr.colorPrimary, Color.GREEN)
        }

        fun linkColor(context: Context): Int {
            return MaterialColors.getColor(context, R.attr.colorPrimary, Color.GREEN)
        }

        fun itemSelectedBackground(context: Context): Int {
            return ContextCompat.getColor(context, R.color.md_theme_surfaceContainerHigh)
        }

        fun cardBackgroundColor(context: Context): Int {
            return if (isDarkThemeOn(context)) {
                MaterialColors.getColor(context, R.attr.colorSurfaceContainer, Color.GRAY)
            } else {
                MaterialColors.getColor(context, R.attr.colorSurface, Color.WHITE)
            }
        }

        fun cardSelectedBackgroundColor(context: Context): Int {
            return if (isDarkThemeOn(context)) {
                MaterialColors.getColor(context, R.attr.colorSurfaceContainerHigh, Color.GRAY)
            } else {
                MaterialColors.getColor(context, R.attr.colorSurfaceContainerHighest, Color.GRAY)
            }
        }

        fun statusBarNormal(context: Context, dynamicColors: Boolean, darkMode: Boolean): Int {
            val default = context.resources.getColor(R.color.action_bar, null)
            return if (dynamicColors) {
                // Use colorSurface for both light and dark mode when dynamic colors are enabled
                MaterialColors.getColor(context, R.attr.colorSurface, default)
            } else {
                default
            }
        }

        // The app bar sits on the page background (ntfy-web AppBar) in both themes, so its text/icons and
        // the status bar icons simply follow light/dark.
        fun shouldUseLightStatusBar(dynamicColors: Boolean, darkMode: Boolean): Boolean = !darkMode

        fun toolbarTextColor(context: Context, dynamicColors: Boolean, darkMode: Boolean): Int =
            MaterialColors.getColor(context, R.attr.colorOnSurface, if (darkMode) Color.WHITE else Color.BLACK)

        fun dangerText(context: Context): Int {
            return MaterialColors.getColor(context, R.attr.colorError, Color.RED)
        }

        fun swipeToRefreshColor(context: Context): Int {
            return MaterialColors.getColor(context, R.attr.colorPrimary, Color.GREEN)
        }
    }
}
