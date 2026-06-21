package io.heckel.ntfy.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup
import io.heckel.ntfy.R

class ThemeSegmentedPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var currentMode: Int = AppCompatDelegate.MODE_NIGHT_YES
    private var onModeChanged: ((Int) -> Unit)? = null

    // Prevent re-entrant calls during programmatic selection
    private var binding = false

    init {
        layoutResource = R.layout.preference_theme_segmented
    }

    fun setMode(mode: Int) {
        currentMode = mode
        notifyChanged()
    }

    fun getMode(): Int = currentMode

    fun setOnModeChangedListener(listener: (Int) -> Unit) {
        onModeChanged = listener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val title = holder.itemView.findViewById<TextView>(android.R.id.title)
        title?.text = this.title

        val toggleGroup = holder.itemView.findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle_group)
            ?: return

        binding = true
        val buttonId = modeToButtonId(currentMode)
        if (buttonId != -1) {
            toggleGroup.check(buttonId)
        }
        binding = false

        // Update content descriptions to reflect selected state for accessibility
        updateAccessibility(toggleGroup)

        // Clear stale listeners from recycled holders before adding a fresh one (F1 fix).
        toggleGroup.clearOnButtonCheckedListeners()
        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || binding) return@addOnButtonCheckedListener
            val mode = buttonIdToMode(checkedId)
            if (mode == currentMode) return@addOnButtonCheckedListener
            currentMode = mode
            updateAccessibility(group)
            onModeChanged?.invoke(mode)
        }
    }

    private fun modeToButtonId(mode: Int): Int = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> R.id.btn_theme_light
        AppCompatDelegate.MODE_NIGHT_YES -> R.id.btn_theme_dark
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> R.id.btn_theme_system
        else -> -1
    }

    private fun buttonIdToMode(buttonId: Int): Int = when (buttonId) {
        R.id.btn_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
        R.id.btn_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
        R.id.btn_theme_system -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }

    private fun updateAccessibility(group: MaterialButtonToggleGroup) {
        val selectedId = group.checkedButtonId
        listOf(R.id.btn_theme_light, R.id.btn_theme_dark, R.id.btn_theme_system).forEach { id ->
            group.findViewById<com.google.android.material.button.MaterialButton>(id)?.let { btn ->
                btn.isSelected = (id == selectedId)
            }
        }
    }
}
