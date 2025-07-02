package com.github.gezimos.inkos

// import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.github.gezimos.common.CrashHandler
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Migration
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.databinding.ActivityMainBinding
import com.github.gezimos.inkos.helper.isTablet
import com.github.gezimos.inkos.helper.isinkosDefault
import com.github.gezimos.inkos.ui.dialogs.DialogManager
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {


    private lateinit var prefs: Prefs
    private lateinit var migration: Migration
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var isOnboarding = false

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isOnboarding) {
            // Block back during onboarding
            return
        }
        @Suppress("DEPRECATION")
        if (navController.currentDestination?.id != R.id.mainFragment)
            super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isOnboarding) {
            // Block all key events during onboarding
            return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                when (navController.currentDestination?.id) {
                    R.id.mainFragment -> {
                        this.findNavController(R.id.nav_host_fragment)
                            .navigate(R.id.action_mainFragment_to_appListFragment)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }

            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z -> {
                when (navController.currentDestination?.id) {
                    R.id.mainFragment -> {
                        val bundle = Bundle()
                        bundle.putInt("letterKeyCode", keyCode) // Pass the letter key code
                        this.findNavController(R.id.nav_host_fragment)
                            .navigate(R.id.action_mainFragment_to_appListFragment, bundle)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                backToHomeScreen()
                true
            }

            KeyEvent.KEYCODE_HOME -> {
                // Only send signal and navigate home, do not use shouldResetHomePage
                com.github.gezimos.inkos.ui.HomeFragment.sendGoToFirstPageSignal()
                backToHomeScreen()
                true
            }

            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                // Handle volume keys for page navigation if enabled and on home
                if (navController.currentDestination?.id == R.id.mainFragment && prefs.useVolumeKeysForPages) {
                    val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    val homeFragment =
                        fragment?.childFragmentManager?.fragments?.find { it is com.github.gezimos.inkos.ui.HomeFragment } as? com.github.gezimos.inkos.ui.HomeFragment
                    if (homeFragment != null && com.github.gezimos.inkos.ui.HomeFragment.isHomeVisible) {
                        if (homeFragment.handleVolumeKeyNavigation(keyCode)) return true
                    }
                }
                return super.onKeyDown(keyCode, event)
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (isOnboarding) {
            // Ignore new intents during onboarding to prevent navController crash
            return
        }
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            // Only bring home to front, do not reset page
            backToHomeScreen()
        }
    }

    private fun backToHomeScreen() {
        if (!::navController.isInitialized) return // Prevent crash if navController not ready
        // Pop all fragments and return to home
        navController.popBackStack(R.id.mainFragment, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        migration = Migration(this)

        // Initialize com.github.gezimos.common.CrashHandler to catch uncaught exceptions
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(applicationContext))

        if (prefs.firstOpen) {
            isOnboarding = true
            // Show onboarding screen using ComposeView
            val composeView = androidx.compose.ui.platform.ComposeView(this)
            setContentView(composeView)
            composeView.setContent {
                com.github.gezimos.inkos.ui.compose.OnboardingScreen.Show(
                    onFinish = {
                        prefs.firstOpen = false
                        isOnboarding = false
                        recreate() // Restart activity to show main UI
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(
                                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                    1001
                                )
                            }
                        } else {
                            val areEnabled =
                                NotificationManagerCompat.from(this).areNotificationsEnabled()
                            if (!areEnabled) {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    }
                                startActivity(intent)
                            }
                        }
                    }
                )
            }
            return
        }

        val themeMode = when (prefs.appTheme) {
            Constants.Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
            Constants.Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            Constants.Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        migration.migratePreferencesOnVersionUpdate(prefs)

        // Set window background color dynamically from prefs
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(prefs.backgroundColor))

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        // Remove firstOpen logic here, handled above
        viewModel.getAppList(includeHiddenApps = true)
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        // Request notification access if not granted
        if (!isNotificationServiceEnabled()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // Add bottom padding if navigation bar/gestures are present
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                navBarInset
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // Post a delayed hide to ensure it runs after window focus changes
        window.decorView.postDelayed({
            // Request focus on decorView to steal it from any EditText
            window.decorView.requestFocus()
            val imm =
                getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            currentFocus?.let { view ->
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }, 100)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {

            Constants.REQUEST_SET_DEFAULT_HOME -> {
                val isDefault = isinkosDefault(this) // Check again if the app is now default

                if (isDefault) {
                    viewModel.setDefaultLauncher(false)
                } else {
                    viewModel.setDefaultLauncher(true)
                }
            }

            Constants.BACKUP_READ -> {
                data?.data?.also { uri ->
                    // Try to get file name from content resolver if lastPathSegment is not reliable
                    val fileName = uri.lastPathSegment ?: ""
                    val isJsonExtension = fileName.endsWith(".json", ignoreCase = true) ||
                        applicationContext.contentResolver.getType(uri)?.contains("json") == true
                    if (!isJsonExtension) {
                        DialogManager(this, this).showErrorDialog(
                            this,
                            getString(R.string.error_invalid_file_title),
                            getString(R.string.error_invalid_file_message)
                        )
                        return
                    }
                    applicationContext.contentResolver.openInputStream(uri).use { inputStream ->
                        val stringBuilder = StringBuilder()
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line: String? = reader.readLine()
                            while (line != null) {
                                stringBuilder.append(line)
                                line = reader.readLine()
                            }
                        }
                        val string = stringBuilder.toString()
                        // Try to parse as JSON
                        try {
                            org.json.JSONObject(string) // Throws if not valid JSON
                        } catch (e: Exception) {
                            DialogManager(this, this).showErrorDialog(
                                this,
                                getString(R.string.error_invalid_file_title),
                                getString(R.string.error_invalid_file_message)
                            )
                            return
                        }
                        val prefs = Prefs(applicationContext)
                        prefs.clear()
                        prefs.loadFromString(string)
                    }
                }
                startActivity(Intent.makeRestartActivityTask(this.intent?.component))
            }

            Constants.BACKUP_WRITE -> {
                data?.data?.also { uri ->
                    applicationContext.contentResolver.openFileDescriptor(uri, "w")?.use { file ->
                        FileOutputStream(file.fileDescriptor).use { stream ->
                            val prefs = Prefs(applicationContext).saveToString()
                            stream.channel.truncate(0)
                            stream.write(prefs.toByteArray())
                        }
                    }
                }
            }
        }
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }


    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this)) return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Forward window focus changes to HomeFragment
        if (hasFocus) {
            val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            val homeFragment = fragment?.childFragmentManager?.fragments?.find { it is com.github.gezimos.inkos.ui.HomeFragment } as? com.github.gezimos.inkos.ui.HomeFragment
            if (homeFragment != null && com.github.gezimos.inkos.ui.HomeFragment.isHomeVisible) {
                homeFragment.onWindowFocusGained()
            }
        }
    }
}