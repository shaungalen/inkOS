/**
 * Prepare the data for the app drawer, which is the list of all the installed applications.
 */

package com.github.gezimos.inkos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.github.gezimos.common.isSystemApp
import com.github.gezimos.common.showKeyboard
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.AppListItem
import com.github.gezimos.inkos.data.Constants.AppDrawerFlag
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.databinding.AdapterAppDrawerBinding
import com.github.gezimos.inkos.helper.dp2px

class AppDrawerAdapter(
    val context: Context,
    private val flag: AppDrawerFlag,
    private val drawerGravity: Int,
    private val clickListener: (AppListItem) -> Unit,
    private val deleteListener: (AppListItem) -> Unit,
    private val renameListener: (String, String) -> Unit,
    private val showHideListener: (AppDrawerFlag, AppListItem) -> Unit,
    private val infoListener: (AppListItem) -> Unit,
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

    private lateinit var prefs: Prefs
    private var appFilter = createAppFilter()
    var appsList: MutableList<AppListItem> = mutableListOf()
    var appFilteredList: MutableList<AppListItem> = mutableListOf()
    private lateinit var binding: AdapterAppDrawerBinding

    private var lastQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        binding =
            AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        prefs = Prefs(parent.context)
        val fontColor = prefs.appColor
        binding.appTitle.setTextColor(fontColor)

        binding.appTitle.textSize = prefs.appSize.toFloat()
        val padding: Int = prefs.textPaddingSize
        binding.appTitle.setPadding(0, padding, 0, padding)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (appFilteredList.isEmpty()) return
        val appModel = appFilteredList[holder.absoluteAdapterPosition]
        holder.bind(
            flag,
            drawerGravity,
            appModel,
            clickListener,
            infoListener,
            deleteListener,
            renameListener,
            showHideListener
        )

        holder.textView.apply {
            // Always use customLabel if available, otherwise fall back to original label
            text = if (appModel.customLabel.isNotEmpty()) appModel.customLabel else appModel.label
            gravity = drawerGravity
            textSize = Prefs(context).appSize.toFloat()
            // Use universal font logic for app names
            typeface = Prefs(context).getFontForContext("apps")
                .getFont(context, Prefs(context).getCustomFontPathForContext("apps"))
            setTextColor(Prefs(context).appColor)
        }

        holder.appHide.setOnClickListener {
            appFilteredList.removeAt(holder.absoluteAdapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.absoluteAdapterPosition)
            showHideListener(flag, appModel)
        }

        holder.appLock.setOnClickListener {
            val appName = appModel.activityPackage
            // Access the current locked apps set
            val currentLockedApps = prefs.lockedApps

            if (currentLockedApps.contains(appName)) {
                holder.appLock.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    R.drawable.padlock_off,
                    0,
                    0
                )
                holder.appLock.text = context.getString(R.string.lock)
                // If appName is already in the set, remove it
                currentLockedApps.remove(appName)
            } else {
                holder.appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                holder.appLock.text = context.getString(R.string.unlock)
                // If appName is not in the set, add it
                currentLockedApps.add(appName)
            }

            // Update the lockedApps value (save the updated set back to prefs)
            prefs.lockedApps = currentLockedApps
            Log.d("lockedApps", prefs.lockedApps.toString())
        }

        holder.appSaveRename.setOnClickListener {
            val name = holder.appRenameEdit.text.toString().trim()
            appModel.customLabel = name
            // Re-sort the list after renaming
            sortAppList()
            notifyDataSetChanged()
            renameListener(appModel.activityPackage, appModel.customLabel)
        }
    }

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                lastQuery = charSearch?.toString() ?: ""
                val filterResults = FilterResults()
                filterResults.values = appsList  // Return all apps since we're removing search
                return filterResults
            }

            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results?.values is MutableList<*>) {
                    appFilteredList = results.values as MutableList<AppListItem>
                    notifyDataSetChanged()
                } else {
                    return
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setAppList(appsList: MutableList<AppListItem>) {
        this.appsList = appsList
        this.appFilteredList = appsList
        sortAppList()
        notifyDataSetChanged()
    }

    private fun sortAppList() {
        val comparator = compareBy<AppListItem> { it.customLabel.ifEmpty { it.label }.lowercase() }
        appsList.sortWith(comparator)
        appFilteredList.sortWith(comparator)
    }

    fun launchFirstInList() {
        if (appFilteredList.isNotEmpty())
            clickListener(appFilteredList[0])
    }

    class ViewHolder(itemView: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(itemView.root) {
        val appHide: TextView = itemView.appHide
        val appLock: TextView = itemView.appLock
        val appRenameEdit: EditText = itemView.appRenameEdit
        val appSaveRename: TextView = itemView.appSaveRename
        val textView: TextView = itemView.appTitle

        private val appHideLayout: LinearLayout = itemView.appHideLayout
        private val appRenameLayout: LinearLayout = itemView.appRenameLayout
        private val appRename: TextView = itemView.appRename
        private val appTitleFrame: FrameLayout = itemView.appTitleFrame
        private val appClose: TextView = itemView.appClose
        private val appInfo: TextView = itemView.appInfo
        private val appDelete: TextView = itemView.appDelete

        @SuppressLint("RtlHardcoded", "NewApi")
        fun bind(
            flag: AppDrawerFlag,
            appLabelGravity: Int,
            appListItem: AppListItem,
            appClickListener: (AppListItem) -> Unit,
            appInfoListener: (AppListItem) -> Unit,
            appDeleteListener: (AppListItem) -> Unit,
            renameListener: (String, String) -> Unit,
            showHideListener: (AppDrawerFlag, AppListItem) -> Unit
        ) =
            with(itemView) {
                val prefs = Prefs(context)
                appHideLayout.visibility = View.GONE
                appRenameLayout.visibility = View.GONE

                // set show/hide icon
                if (flag == AppDrawerFlag.HiddenApps) {
                    appHide.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.visibility, 0, 0)
                    appHide.text = context.getString(R.string.show)
                } else {
                    appHide.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        R.drawable.visibility_off,
                        0,
                        0
                    )
                    appHide.text = context.getString(R.string.hide)
                }

                val appName = appListItem.activityPackage
                // Access the current locked apps set
                val currentLockedApps = prefs.lockedApps

                if (currentLockedApps.contains(appName)) {
                    appLock.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.padlock, 0, 0)
                    appLock.text = context.getString(R.string.unlock)
                } else {
                    appLock.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        R.drawable.padlock_off,
                        0,
                        0
                    )
                    appLock.text = context.getString(R.string.lock)
                }

                appRename.apply {
                    setOnClickListener {
                        if (appListItem.activityPackage.isNotEmpty()) {
                            appRenameEdit.setText(appListItem.customLabel.ifEmpty { appListItem.label })
                            appRenameLayout.visibility = View.VISIBLE
                            appHideLayout.visibility = View.GONE
                            appRenameEdit.showKeyboard()
                            appRenameEdit.setSelection(appRenameEdit.text.length)
                        }
                    }
                }

                appRenameEdit.apply {
                    addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable) {}

                        override fun beforeTextChanged(
                            s: CharSequence, start: Int,
                            count: Int, after: Int
                        ) {
                        }

                        override fun onTextChanged(
                            s: CharSequence, start: Int,
                            before: Int, count: Int
                        ) {
                            if (appRenameEdit.text.isEmpty()) {
                                appSaveRename.text = context.getString(R.string.reset)
                            } else if (appRenameEdit.text.toString() == appListItem.customLabel) {
                                appSaveRename.text = context.getString(R.string.cancel)
                            } else {
                                appSaveRename.text = context.getString(R.string.rename)
                            }
                        }
                    })
                    text = Editable.Factory.getInstance().newEditable(appListItem.label)
                    setOnEditorActionListener { v, actionId, event ->
                        if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                            val name = appRenameEdit.text.toString().trim()
                            appListItem.customLabel = name
                            (bindingAdapter as AppDrawerAdapter).notifyItemChanged(
                                absoluteAdapterPosition
                            )
                            renameListener(appListItem.activityPackage, name)
                            appRenameLayout.visibility = View.GONE
                            true
                        } else {
                            false
                        }
                    }
                }

                appSaveRename.setOnClickListener {
                    val name = appRenameEdit.text.toString().trim()
                    appListItem.customLabel = name
                    (bindingAdapter as AppDrawerAdapter).notifyItemChanged(absoluteAdapterPosition)
                    renameListener(appListItem.activityPackage, name)
                    appRenameLayout.visibility = View.GONE
                }

                textView.apply {
                    val customLabel =
                        Prefs(context).getAppAlias("app_alias_${appListItem.activityPackage}")
                    text = if (customLabel.isNotEmpty()) customLabel else appListItem.label
                    gravity = appLabelGravity
                    textSize = Prefs(context).appSize.toFloat()
                    // Use universal font logic for app names
                    typeface = Prefs(context).getFontForContext("apps")
                        .getFont(context, Prefs(context).getCustomFontPathForContext("apps"))
                    setTextColor(Prefs(context).appColor)
                }

                // set text gravity
                val params = textView.layoutParams as FrameLayout.LayoutParams
                params.gravity = appLabelGravity
                textView.layoutParams = params

                textView.setCompoundDrawables(null, null, null, null)

                val padding = dp2px(resources, 24)
                textView.updatePadding(left = padding, right = padding)

                appHide.setOnClickListener {
                    (bindingAdapter as AppDrawerAdapter).let { adapter ->
                        // Remove from current visible list
                        adapter.appFilteredList.removeAt(absoluteAdapterPosition)
                        adapter.notifyItemRemoved(absoluteAdapterPosition)
                        // Remove from full list as well
                        adapter.appsList.remove(appListItem)
                        showHideListener(flag, appListItem)
                        appHideLayout.visibility = View.GONE

                        // Reapply current filter to refresh the list
                        adapter.filter.filter(adapter.lastQuery)
                    }
                }

                appTitleFrame.apply {
                    setOnClickListener {
                        appClickListener(appListItem)
                    }
                    setOnLongClickListener {
                        val openApp =
                            flag == AppDrawerFlag.LaunchApp || flag == AppDrawerFlag.HiddenApps
                        if (openApp) {
                            try {
                                appDelete.alpha =
                                    if (context.isSystemApp(appListItem.activityPackage)) 0.3f else 1.0f
                                appHideLayout.visibility = View.VISIBLE
                                appRenameLayout.visibility =
                                    View.GONE // Make sure rename layout is hidden
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        true
                    }
                }

                appInfo.apply {
                    setOnClickListener {
                        appInfoListener(appListItem)
                    }
                }

                appDelete.apply {
                    setOnClickListener {
                        appDeleteListener(appListItem)
                    }
                }

                appClose.apply {
                    setOnClickListener {
                        appHideLayout.visibility = View.GONE
                        appRenameLayout.visibility = View.GONE
                    }
                }
            }
    }
}
