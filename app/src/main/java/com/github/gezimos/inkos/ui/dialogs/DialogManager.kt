package com.github.gezimos.inkos.ui.dialogs

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.getTrueSystemFont
import com.github.gezimos.inkos.helper.loadFile
import com.github.gezimos.inkos.helper.storeFile
import com.github.gezimos.inkos.helper.utils.AppReloader
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DialogManager(val context: Context, val activity: Activity) {

    private lateinit var prefs: Prefs

    var backupRestoreDialog: AlertDialog? = null

    fun showBackupRestoreDialog() {
        // Dismiss any existing dialog to prevent multiple dialogs open simultaneously
        backupRestoreDialog?.dismiss()

        // Define the items for the dialog (Backup, Restore, Clear Data)
        val items = arrayOf(
            context.getString(R.string.advanced_settings_backup_restore_backup),
            context.getString(R.string.advanced_settings_backup_restore_restore),
            context.getString(R.string.advanced_settings_backup_restore_clear)
        )

        val dialogBuilder = MaterialAlertDialogBuilder(context)
        dialogBuilder.setTitle(context.getString(R.string.advanced_settings_backup_restore_title))
        dialogBuilder.setItems(items) { _, which ->
            when (which) {
                0 -> storeFile(activity, Constants.BackupType.FullSystem)
                1 -> loadFile(activity, Constants.BackupType.FullSystem)
                2 -> confirmClearData()
            }
        }

        // Assign the created dialog to backupRestoreDialog
        backupRestoreDialog = dialogBuilder.create()
        backupRestoreDialog?.show()
        setDialogFontForAllButtonsAndText(backupRestoreDialog, context, "settings")
    }

    // Function to handle the Clear Data action, with a confirmation dialog
    private fun confirmClearData() {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.advanced_settings_backup_restore_clear_title))
            .setMessage(context.getString(R.string.advanced_settings_backup_restore_clear_description))
            .setPositiveButton(context.getString(R.string.advanced_settings_backup_restore_clear_yes)) { _, _ ->
                clearData()
            }
            .setNegativeButton(
                context.getString(R.string.advanced_settings_backup_restore_clear_no),
                null
            )
            .show()
    }

    private fun clearData() {
        prefs = Prefs(context)
        prefs.clear()

        AppReloader.restartApp(context)
    }

    var sliderDialog: AlertDialog? = null

    fun showSliderDialog(
        context: Context,
        title: String,
        minValue: Int,
        maxValue: Int,
        currentValue: Int,
        onValueSelected: (Int) -> Unit // Callback for when the user selects a value
    ) {
        // Dismiss any existing dialog to prevent multiple dialogs from being open simultaneously
        sliderDialog?.dismiss()

        var seekBar: SeekBar
        lateinit var valueText: TextView

        // Create a layout to hold the SeekBar and the value display
        val seekBarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)

            // TextView to display the current value
            valueText = TextView(context).apply {
                text = "$currentValue"
                textSize = 16f
                gravity = Gravity.CENTER
            }

            // Declare the seekBar outside the layout block so we can access it later
            seekBar = SeekBar(context).apply {
                min = minValue // Minimum value
                max = maxValue // Maximum value
                progress = currentValue // Default value
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        valueText.text = "$progress"
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
            }

            // Add TextView and SeekBar to the layout
            addView(valueText)
            addView(seekBar)
        }

        // Set valueText font and size before showing dialog
        val prefs = Prefs(context)
        val fontFamily = prefs.getFontForContext("settings")
        val customFontPath = prefs.getCustomFontPathForContext("settings")
        val typeface = fontFamily.getFont(context, customFontPath) ?: getTrueSystemFont()
        val textSize = prefs.settingsSize.toFloat()
        valueText.typeface = typeface
        valueText.textSize = textSize

        // Create the dialog
        val dialogBuilder = MaterialAlertDialogBuilder(context).apply {
            setTitle(title)
            setView(seekBarLayout) // Add the slider directly to the dialog
            setPositiveButton(context.getString(R.string.okay)) { _, _ ->
                // Get the progress from the seekBar now that it's accessible
                val finalValue = seekBar.progress
                onValueSelected(finalValue) // Trigger the callback with the selected value
            }
            setNegativeButton(context.getString(R.string.cancel), null)
        }

        // Assign the created dialog to sliderDialog and show it
        sliderDialog = dialogBuilder.create()
        sliderDialog?.show()
        setDialogFontForAllButtonsAndText(sliderDialog, context, "settings")

        // --- Volume key support for SeekBar ---
        if (prefs.useVolumeKeysForPages) {
            sliderDialog?.window?.decorView?.isFocusableInTouchMode = true
            sliderDialog?.window?.decorView?.requestFocus()
            sliderDialog?.window?.decorView?.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (seekBar.progress < seekBar.max) {
                                seekBar.progress = seekBar.progress + 1
                                return@setOnKeyListener true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (seekBar.progress > seekBar.min) {
                                seekBar.progress = seekBar.progress - 1
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }
        }
    }

    var singleChoiceDialog: AlertDialog? = null

    fun <T> showSingleChoiceDialog(
        context: Context,
        options: Array<T>,
        titleResId: Int,
        fonts: List<Typeface>? = null, // Optional fonts
        fontSize: Float = 18f, // Default font size
        selectedIndex: Int? = null, // Index of selected font
        isCustomFont: ((T) -> Boolean)? = null, // Function to check if font is custom
        onItemSelected: (T) -> Unit,
        onItemDeleted: ((T) -> Unit)? = null // Callback for delete
    ) {
        singleChoiceDialog?.dismiss()

        // Move selected font to the top if selectedIndex is provided
        val reorderedOptions = if (selectedIndex != null && selectedIndex in options.indices) {
            val list = options.toMutableList()
            val selected = list.removeAt(selectedIndex)
            list.add(0, selected)
            list
        } else {
            options.toList()
        }
        val reorderedFonts =
            if (fonts != null && selectedIndex != null && selectedIndex in fonts.indices) {
                val list = fonts.toMutableList()
                val selected = list.removeAt(selectedIndex)
                list.add(0, selected)
                list
            } else {
                fonts
            }

        val itemStrings = reorderedOptions.map { option ->
            when (option) {
                is Enum<*> -> option.name
                else -> option.toString()
            }
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_single_choice, null)
        val listView = dialogView.findViewById<ListView>(R.id.dialogListView)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val deleteButton = dialogView.findViewById<View>(R.id.dialogDeleteButton)

        val prefs = Prefs(context)
        val fontFamily = prefs.getFontForContext("settings")
        val customFontPath = prefs.getCustomFontPathForContext("settings")
        val typeface = fontFamily.getFont(context, customFontPath) ?: getTrueSystemFont()
        val textSize = prefs.settingsSize.toFloat()
        titleView.text = context.getString(titleResId)
        titleView.typeface = typeface
        titleView.textSize = textSize

        var selectedListIndex = 0 // Always start with the top item selected

        val adapter =
            object : ArrayAdapter<String>(context, R.layout.item_single_choice, itemStrings) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(R.layout.item_single_choice, parent, false)
                    val textView = view.findViewById<TextView>(R.id.text_item)
                    val deleteIcon = view.findViewById<View>(R.id.delete_icon)

                    textView.text = itemStrings[position]
                    textView.typeface = reorderedFonts?.getOrNull(position) ?: getTrueSystemFont()
                    textView.textSize = fontSize

                    // Highlight selected item with transparent background and black outline
                    if (position == selectedListIndex) {
                        view.setBackgroundResource(0)
                        view.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.TRANSPARENT)
                            setStroke(3, Color.BLACK)
                            cornerRadius = 16f
                        }
                    } else {
                        view.setBackgroundResource(0)
                        view.background = null
                    }

                    // Show delete icon for custom fonts only
                    if (isCustomFont != null && isCustomFont(reorderedOptions[position])) {
                        deleteIcon.visibility = View.VISIBLE
                        deleteIcon.setOnClickListener {
                            onItemDeleted?.invoke(reorderedOptions[position])
                            singleChoiceDialog?.dismiss()
                        }
                    } else {
                        deleteIcon.visibility = View.GONE
                        deleteIcon.setOnClickListener(null)
                    }

                    return view
                }
            }
        listView.adapter = adapter

        // Show delete button if selected is custom
        fun updateDeleteButton() {
            if (isCustomFont != null && isCustomFont(reorderedOptions[selectedListIndex])) {
                deleteButton.visibility = View.VISIBLE
            } else {
                deleteButton.visibility = View.GONE
            }
        }
        updateDeleteButton()

        // Handle item selection
        listView.setOnItemClickListener { parent, view, position, _ ->
            onItemSelected(reorderedOptions[position])
            singleChoiceDialog?.dismiss()
        }

        // Handle delete button click
        deleteButton.setOnClickListener {
            onItemDeleted?.invoke(reorderedOptions[selectedListIndex])
            singleChoiceDialog?.dismiss()
        }

        val dialogBuilder = MaterialAlertDialogBuilder(context)
            .setView(dialogView)

        singleChoiceDialog = dialogBuilder.create()
        singleChoiceDialog?.show()
        setDialogFontForAllButtonsAndText(singleChoiceDialog, context, "settings")
    }

    var multiChoiceDialog: AlertDialog? = null

    fun showMultiChoiceDialog(
        context: Context,
        title: String,
        items: Array<String>,
        initialChecked: BooleanArray,
        onConfirm: (selectedIndices: List<Int>) -> Unit
    ) {
        multiChoiceDialog?.dismiss()
        val checked = initialChecked.copyOf()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(context.getString(R.string.okay)) { _, _ ->
                val selected = mutableListOf<Int>()
                for (idx in checked.indices) {
                    if (checked[idx]) selected.add(idx)
                }
                onConfirm(selected)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()
        dialog.show()
        multiChoiceDialog = dialog
        setDialogFontForAllButtonsAndText(multiChoiceDialog, context, "settings")
    }

    var colorPickerDialog: AlertDialog? = null

    fun showColorPickerDialog(
        context: Context,
        titleResId: Int,
        color: Int,
        onItemSelected: (Int) -> Unit // Callback to handle the selected color
    ) {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        var isUpdatingText = false

        val dialogBuilder = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(titleResId))

        // Create SeekBars for Red, Green, and Blue
        val redSeekBar = createColorSeekBar(context, red)
        val greenSeekBar = createColorSeekBar(context, green)
        val blueSeekBar = createColorSeekBar(context, blue)

        // Create color preview box and RGB Hex input field
        val colorPreviewBox = createColorPreviewBox(context, color)
        val rgbText = createRgbTextField(context, red, green, blue)

        // Layout with SeekBars, Color Preview, and RGB Hex Text Input
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            // Create a horizontal layout for the text box and color preview
            val horizontalLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL // Vertically center the views

                // RGB Text field
                val rgbParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 32 // Optional: Add margin between the text and the color box
                    }
                rgbText.layoutParams = rgbParams
                addView(rgbText)

                // Color preview box
                val colorParams = LinearLayout.LayoutParams(150, 50).apply {
                    marginEnd = 32 // Optional: Add margin between the text and the color box
                }
                colorPreviewBox.layoutParams = colorParams
                addView(colorPreviewBox)
            }

            addView(redSeekBar)
            addView(greenSeekBar)
            addView(blueSeekBar)
            addView(horizontalLayout)
        }

        // Update color preview and text input when SeekBars are adjusted
        val updateColorPreview = {
            val updatedColor = Color.rgb(
                redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress
            )
            colorPreviewBox.setBackgroundColor(updatedColor)

            if (!isUpdatingText) {
                isUpdatingText = true
                rgbText.setText(
                    String.format(
                        "#%02X%02X%02X",
                        redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress
                    )
                )
                isUpdatingText = false
            }
        }

        // Listeners to update color preview when sliders are adjusted
        redSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColorPreview))
        greenSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColorPreview))
        blueSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColorPreview))

        // Listen for text input and update sliders and preview
        rgbText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.trim()?.let { colorString ->
                    if (colorString.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                        val hexColor = colorString.toColorInt()
                        redSeekBar.progress = Color.red(hexColor)
                        greenSeekBar.progress = Color.green(hexColor)
                        blueSeekBar.progress = Color.blue(hexColor)
                        updateColorPreview()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Set up the dialog view and buttons
        dialogBuilder.setView(layout)
        dialogBuilder.setPositiveButton("OK") { _, _ ->
            val pickedColor = Color.rgb(
                redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress
            )
            onItemSelected(pickedColor)
        }
        dialogBuilder.setNegativeButton("Cancel", null)

        // Show the dialog
        dialogBuilder.create().show()
    }

    fun showInputDialog(
        context: Context,
        title: String,
        initialValue: String,
        onValueEntered: (String) -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(context)
        val input = EditText(context)
        input.setText(initialValue)
        // Make input single line and set IME action to DONE
        input.maxLines = 1
        input.isSingleLine = true
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        input.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                onValueEntered(input.text.toString())
                // Dismiss the dialog
                (v.parent as? View)?.let { parentView ->
                    var parent = parentView.parent
                    while (parent != null && parent !is AlertDialog) {
                        parent = (parent as? View)?.parent
                    }
                    (parent as? AlertDialog)?.dismiss()
                }
                true
            } else {
                false
            }
        }

        // Wrap the EditText in a FrameLayout with standard Material dialog padding
        val container = android.widget.FrameLayout(context).apply {
            val padding = (24 * context.resources.displayMetrics.density).toInt() // 24dp
            setPadding(padding, padding, padding, 0)
            // Fix: Remove input from previous parent if needed
            (input.parent as? ViewGroup)?.removeView(input)
            addView(input)
        }

        builder.setTitle(title)
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                onValueEntered(input.text.toString())
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
        // Only call show() once and use the returned dialog
        val dialog = builder.show()
        setDialogFontForAllButtonsAndText(dialog, context, "settings")
    }

    fun showErrorDialog(context: Context, title: String, message: String) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
        setDialogFontForAllButtonsAndText(dialog, context, "settings")
    }

    private fun createColorSeekBar(context: Context, initialValue: Int): SeekBar {
        return SeekBar(context).apply {
            max = 255
            progress = initialValue
        }
    }

    private fun createColorPreviewBox(context: Context, color: Int): View {
        return View(context).apply {
            setBackgroundColor(color)
        }
    }

    private fun createRgbTextField(context: Context, red: Int, green: Int, blue: Int): EditText {
        return EditText(context).apply {
            setText(String.format("#%02X%02X%02X", red, green, blue))
            inputType = InputType.TYPE_CLASS_TEXT

            // Remove the bottom line (underline) from the EditText
            background = null
        }
    }

    private fun createSeekBarChangeListener(updateColorPreview: () -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColorPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    fun setDialogFontForAllButtonsAndText(
        dialog: AlertDialog?,
        context: Context,
        contextKey: String = "settings"
    ) {
        val prefs = Prefs(context)
        val fontFamily = prefs.getFontForContext(contextKey)
        val customFontPath = prefs.getCustomFontPathForContext(contextKey)
        val typeface = fontFamily.getFont(context, customFontPath) ?: getTrueSystemFont()
        val textSize = prefs.settingsSize.toFloat()
        // Set for all buttons
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
            it.typeface = typeface
            it.textSize = textSize
        }
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.let {
            it.typeface = typeface
            it.textSize = textSize
        }
        dialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.let {
            it.typeface = typeface
            it.textSize = textSize
        }
        // Set for all TextViews in the dialog view
        (dialog?.window?.decorView as? ViewGroup)?.let { root ->
            fun applyToAllTextViews(view: View) {
                if (view is TextView) {
                    view.typeface = typeface
                    view.textSize = textSize
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        applyToAllTextViews(view.getChildAt(i))
                    }
                }
            }
            applyToAllTextViews(root)
        }
    }
}

