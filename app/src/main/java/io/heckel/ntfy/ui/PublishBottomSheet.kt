package io.heckel.ntfy.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import io.heckel.ntfy.R
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.msg.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Host callback wired by FeedActivity so the bottom sheet can emit optimistic messages. */
interface OutboxListener {
    fun onOptimisticEmit(msg: OptimisticMessage, job: Job)
    fun onOptimisticSuccess(localId: String)
    fun onOptimisticFailure(localId: String, cause: String)
}

class PublishBottomSheet : BottomSheetDialogFragment() {

    private lateinit var topicText: TextInputEditText
    private lateinit var titleText: TextInputEditText
    private lateinit var messageText: TextInputEditText
    private lateinit var tagsText: TextInputEditText
    private lateinit var priorityChipGroup: ChipGroup
    private lateinit var chipLow: Chip
    private lateinit var chipNormal: Chip
    private lateinit var chipHigh: Chip
    private lateinit var chipUrgent: Chip
    private lateinit var sendButton: MaterialButton
    private lateinit var closeButton: MaterialButton
    private lateinit var errorText: TextView

    private val repository by lazy { (requireActivity().application as Application).repository }
    private val api by lazy { ApiService(requireContext()) }

    private var selectedPriority: Int = PRIORITY_NORMAL
    private var outboxListener: OutboxListener? = null

    fun setOutboxListener(listener: OutboxListener) {
        outboxListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_publish_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        topicText = view.findViewById(R.id.publish_sheet_topic)
        titleText = view.findViewById(R.id.publish_sheet_title_text)
        messageText = view.findViewById(R.id.publish_sheet_message)
        tagsText = view.findViewById(R.id.publish_sheet_tags)
        priorityChipGroup = view.findViewById(R.id.publish_sheet_priority_chips)
        chipLow = view.findViewById(R.id.chip_priority_low)
        chipNormal = view.findViewById(R.id.chip_priority_normal)
        chipHigh = view.findViewById(R.id.chip_priority_high)
        chipUrgent = view.findViewById(R.id.chip_priority_urgent)
        sendButton = view.findViewById(R.id.publish_sheet_send)
        closeButton = view.findViewById(R.id.publish_sheet_close)
        errorText = view.findViewById(R.id.publish_sheet_error)

        // Pre-fill topic from args if provided
        arguments?.getString(ARG_INITIAL_TOPIC)?.let { if (it.isNotEmpty()) topicText.setText(it) }

        setupPriorityChips()
        setupValidation()
        setupButtons()
    }

    private fun setupPriorityChips() {
        selectedPriority = PRIORITY_NORMAL
        applyAllChipTints()

        priorityChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedPriority = when (checkedIds.firstOrNull()) {
                R.id.chip_priority_low    -> PRIORITY_LOW
                R.id.chip_priority_normal -> PRIORITY_NORMAL
                R.id.chip_priority_high   -> PRIORITY_HIGH
                R.id.chip_priority_urgent -> PRIORITY_URGENT
                else                      -> PRIORITY_NORMAL
            }
            applyAllChipTints()
        }
    }

    private fun applyAllChipTints() {
        applyChipTint(chipLow,    isSelected = selectedPriority == PRIORITY_LOW,    colorRes = R.color.muted)
        applyChipTint(chipNormal, isSelected = selectedPriority == PRIORITY_NORMAL, colorRes = R.color.text)
        applyChipTint(chipHigh,   isSelected = selectedPriority == PRIORITY_HIGH,   colorRes = R.color.priority_high)
        applyChipTint(chipUrgent, isSelected = selectedPriority == PRIORITY_URGENT, colorRes = R.color.priority_max)
    }

    private fun applyChipTint(chip: Chip, isSelected: Boolean, colorRes: Int) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        val borderColor = ContextCompat.getColor(requireContext(), R.color.control_border)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.muted)

        if (isSelected) {
            chip.chipStrokeColor = ColorStateList.valueOf(color)
            chip.setTextColor(color)
            chip.chipBackgroundColor = ColorStateList.valueOf(color and 0x00FFFFFF or 0x1A000000)
        } else {
            chip.chipStrokeColor = ColorStateList.valueOf(borderColor)
            chip.setTextColor(mutedColor)
            chip.chipBackgroundColor = ColorStateList.valueOf(0x00000000)
        }
    }

    private fun setupValidation() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateSendEnabled()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        topicText.addTextChangedListener(watcher)
        messageText.addTextChangedListener(watcher)
    }

    private fun updateSendEnabled() {
        val topicFilled = !topicText.text.isNullOrBlank()
        val messageFilled = !messageText.text.isNullOrBlank()
        sendButton.isEnabled = topicFilled && messageFilled
    }

    private fun setupButtons() {
        closeButton.setOnClickListener { dismiss() }
        sendButton.setOnClickListener { onSendClick() }
    }

    private fun onSendClick() {
        val topic = topicText.text.toString().trim()
        val title = titleText.text.toString().trim()
        val message = messageText.text.toString().trim()
        val tags = tagsText.text.toString().trim()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val priority = selectedPriority
        val listener = outboxListener
        val appContext = requireContext().applicationContext

        errorText.visibility = View.GONE
        sendButton.isEnabled = false

        val job = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val baseUrl = repository.getDefaultBaseUrl()
                ?: appContext.getString(R.string.app_base_url)
            val user = repository.getUser(baseUrl)

            if (listener != null) {
                // Optimistic path: emit pending card before the HTTP call, dismiss sheet.
                val payload = PublishPayload(
                    baseUrl  = baseUrl,
                    topic    = topic,
                    message  = message,
                    title    = title,
                    priority = priority,
                    tags     = tags,
                )
                val optimistic = OptimisticMessage.create(payload, System.currentTimeMillis())

                withContext(Dispatchers.Main) { dismiss() }

                // Notify host on Main so it can register the job before the HTTP call starts.
                withContext(Dispatchers.Main) {
                    listener.onOptimisticEmit(optimistic, coroutineContext[Job]!!)
                }

                try {
                    api.publish(
                        baseUrl  = baseUrl,
                        topic    = topic,
                        user     = user,
                        message  = message,
                        title    = title,
                        priority = priority,
                        tags     = tags,
                        delay    = "",
                    )
                    withContext(Dispatchers.Main) { listener.onOptimisticSuccess(optimistic.localId) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        listener.onOptimisticFailure(
                            optimistic.localId,
                            e.message ?: appContext.getString(R.string.publish_sheet_error_unknown)
                        )
                    }
                }
            } else {
                // Fallback path (no outbox wired): original inline send.
                try {
                    api.publish(
                        baseUrl   = baseUrl,
                        topic     = topic,
                        user      = user,
                        message   = message,
                        title     = title,
                        priority  = priority,
                        tags      = tags,
                        delay     = "",
                    )
                    withContext(Dispatchers.Main) { dismiss() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showError(e.message ?: appContext.getString(R.string.publish_sheet_error_unknown))
                        updateSendEnabled()
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = getString(R.string.publish_sheet_error_send, message)
        errorText.visibility = View.VISIBLE
    }

    companion object {
        const val TAG = "PublishBottomSheet"

        private const val ARG_INITIAL_TOPIC = "initialTopic"

        private const val PRIORITY_LOW    = 2
        private const val PRIORITY_NORMAL = 3
        private const val PRIORITY_HIGH   = 4
        private const val PRIORITY_URGENT = 5

        fun newInstance(initialTopic: String = ""): PublishBottomSheet {
            val sheet = PublishBottomSheet()
            sheet.arguments = Bundle().apply { putString(ARG_INITIAL_TOPIC, initialTopic) }
            return sheet
        }
    }
}
