/**
 * The view for the list of all the installed applications.
 */

package com.github.gezimos.inkos.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.gezimos.common.isSystemApp
import com.github.gezimos.common.showShortToast
import com.github.gezimos.inkos.MainViewModel
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.AppListItem
import com.github.gezimos.inkos.data.Constants.AppDrawerFlag
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.databinding.FragmentAppDrawerBinding
import com.github.gezimos.inkos.helper.getHexForOpacity
import com.github.gezimos.inkos.helper.openAppInfo

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var viewModel: MainViewModel  // Add viewModel property

    // --- Add uninstall launcher and package tracking ---
    private var pendingUninstallPackage: String? = null
    private val uninstallLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (pendingUninstallPackage != null) {
                viewModel.refreshAppListAfterUninstall(includeHiddenApps = false)
                pendingUninstallPackage = null
            }
        }

    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        prefs = Prefs(requireContext())

        // Initialize viewModel
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        return binding.root
    }

    @SuppressLint("RtlHardcoded")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (prefs.firstSettingsOpen) {
            prefs.firstSettingsOpen = false
        }

        arguments?.getInt("letterKeyCode", -1)

        val backgroundColor = getHexForOpacity(prefs)
        binding.mainLayout.setBackgroundColor(backgroundColor)

        val flagString = arguments?.getString("flag", AppDrawerFlag.LaunchApp.toString())
            ?: AppDrawerFlag.LaunchApp.toString()
        val flag = AppDrawerFlag.valueOf(flagString)
        val n = arguments?.getInt("n", 0) ?: 0

        // Include hidden apps only for SetHomeApp flag or HiddenApps flag
        val includeHidden = flag == AppDrawerFlag.SetHomeApp || flag == AppDrawerFlag.HiddenApps
        viewModel.getAppList(includeHiddenApps = includeHidden)

        when (flag) {
            AppDrawerFlag.SetHomeApp,
            AppDrawerFlag.SetSwipeUp,
            AppDrawerFlag.SetSwipeDown,
            AppDrawerFlag.SetSwipeLeft,
            AppDrawerFlag.SetSwipeRight,
            AppDrawerFlag.SetClickClock -> {
                binding.drawerButton.setOnClickListener {
                    findNavController().popBackStack()
                }
            }

            AppDrawerFlag.HiddenApps,
            AppDrawerFlag.LaunchApp,
            AppDrawerFlag.PrivateApps,
            AppDrawerFlag.SetDoubleTap -> {
                // No action needed
            }
        }

        // Always use center gravity for consistency
        val gravity = Gravity.CENTER

        val appAdapter = context?.let {
            AppDrawerAdapter(
                it,
                flag,
                gravity,
                appClickListener(viewModel, flag, n),
                appDeleteListener(),
                this.appRenameListener(),
                appShowHideListener(),
                appInfoListener()
            )
        }

        if (appAdapter != null) {
            adapter = appAdapter
        }

        // Hide the search view completely
        binding.search.visibility = View.GONE

        // Apply apps font to listEmptyHint text
        binding.listEmptyHint.typeface = prefs.appsFont.getFont(requireContext())

        if (appAdapter != null) {
            initViewModel(flag, viewModel, appAdapter)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appAdapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())

        binding.listEmptyHint.text =
            applyTextColor(getString(R.string.drawer_list_empty_hint), prefs.appColor)
    }

    private fun applyTextColor(text: String, color: Int): SpannableString {
        val spannableString = SpannableString(text)
        spannableString.setSpan(
            ForegroundColorSpan(color),
            0,
            text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannableString
    }

    private fun convertKeyCodeToLetter(keyCode: Int): Char {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> 'A'
            KeyEvent.KEYCODE_B -> 'B'
            KeyEvent.KEYCODE_C -> 'C'
            KeyEvent.KEYCODE_D -> 'D'
            KeyEvent.KEYCODE_E -> 'E'
            KeyEvent.KEYCODE_F -> 'F'
            KeyEvent.KEYCODE_G -> 'G'
            KeyEvent.KEYCODE_H -> 'H'
            KeyEvent.KEYCODE_I -> 'I'
            KeyEvent.KEYCODE_J -> 'J'
            KeyEvent.KEYCODE_K -> 'K'
            KeyEvent.KEYCODE_L -> 'L'
            KeyEvent.KEYCODE_M -> 'M'
            KeyEvent.KEYCODE_N -> 'N'
            KeyEvent.KEYCODE_O -> 'O'
            KeyEvent.KEYCODE_P -> 'P'
            KeyEvent.KEYCODE_Q -> 'Q'
            KeyEvent.KEYCODE_R -> 'R'
            KeyEvent.KEYCODE_S -> 'S'
            KeyEvent.KEYCODE_T -> 'T'
            KeyEvent.KEYCODE_U -> 'U'
            KeyEvent.KEYCODE_V -> 'V'
            KeyEvent.KEYCODE_W -> 'W'
            KeyEvent.KEYCODE_X -> 'X'
            KeyEvent.KEYCODE_Y -> 'Y'
            KeyEvent.KEYCODE_Z -> 'Z'
            else -> throw IllegalArgumentException("Invalid key code: $keyCode")
        }
    }

    private fun initViewModel(
        flag: AppDrawerFlag,
        viewModel: MainViewModel,
        appAdapter: AppDrawerAdapter
    ) {
        viewModel.hiddenApps.observe(viewLifecycleOwner, Observer {
            if (flag != AppDrawerFlag.HiddenApps) return@Observer
            it?.let { appList ->
                binding.listEmptyHint.visibility =
                    if (appList.isEmpty()) View.VISIBLE else View.GONE
                populateAppList(appList, appAdapter)
            }
        })

        viewModel.appList.observe(viewLifecycleOwner, Observer {
            if (flag == AppDrawerFlag.HiddenApps) return@Observer
            if (it == appAdapter.appsList) return@Observer
            it?.let { appList ->
                binding.listEmptyHint.visibility =
                    if (appList.isEmpty()) View.VISIBLE else View.GONE
                populateAppList(appList, appAdapter)
            }
        })

        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it) binding.appDrawerTip.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        // No need to show keyboard since search is hidden
    }

    override fun onStop() {
        super.onStop()
        // No need to hide keyboard since search is hidden
    }

    private fun View.hideKeyboard() {
        val imm: InputMethodManager? =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(windowToken, 0)
        this.clearFocus()
    }

    private fun populateAppList(apps: List<AppListItem>, appAdapter: AppDrawerAdapter) {
        val animation =
            AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
        binding.recyclerView.layoutAnimation = animation
        appAdapter.setAppList(apps.toMutableList())
    }

    private fun appClickListener(
        viewModel: MainViewModel,
        flag: AppDrawerFlag,
        n: Int = 0
    ): (appListItem: AppListItem) -> Unit =
        { appModel ->
            viewModel.selectedApp(this, appModel, flag, n)
        }

    private fun appDeleteListener(): (appListItem: AppListItem) -> Unit =
        { appModel ->
            if (requireContext().isSystemApp(appModel.activityPackage))
                showShortToast(getString(R.string.can_not_delete_system_apps))
            else {
                val appPackage = appModel.activityPackage
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = "package:$appPackage".toUri()
                pendingUninstallPackage = appPackage
                uninstallLauncher.launch(intent)
                // Do NOT refresh here; refresh will happen in the result callback
            }
        }

    private fun appRenameListener(): (String, String) -> Unit = { packageName, newName ->
        viewModel.renameApp(packageName, newName)
        adapter.notifyDataSetChanged()
    }

    private fun renameListener(flag: AppDrawerFlag, i: Int) {
        // No search functionality, so no longer needed
    }

    private fun appShowHideListener(): (flag: AppDrawerFlag, appListItem: AppListItem) -> Unit =
        { flag, appModel ->
            viewModel.hideOrShowApp(flag, appModel)
            if (flag == AppDrawerFlag.HiddenApps && Prefs(requireContext()).hiddenApps.isEmpty()) {
                findNavController().popBackStack()
            }
        }

    private fun appInfoListener(): (appListItem: AppListItem) -> Unit =
        { appModel ->
            openAppInfo(
                requireContext(),
                appModel.user,
                appModel.activityPackage
            )
            findNavController().popBackStack(R.id.mainFragment, false)
        }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop && !recyclerView.canScrollVertically(1)) {
                            findNavController().popBackStack()
                        }
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1)) {
                            // No need to hide keyboard since search is removed
                        } else if (!recyclerView.canScrollVertically(-1)) {
                            if (onTop) {
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }
}