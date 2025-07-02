package com.github.gezimos.inkos.helper.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import com.github.gezimos.inkos.data.Prefs
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EinkScrollBehavior(
    private val context: Context,
    private var touchThreshold: Float = 50f,     // Threshold to detect significant movement
    // Scroll by full page height by default
    private var timeThresholdMs: Long = 300,     // Minimum time between page turns (milliseconds)
    private val prefs: Prefs = Prefs(context)    // Add Prefs for vibration preference
) {
    private var lastY: Float = 0f
    private var startY: Float = 0f
    private var lastScrollTime: Long = 0         // Track time of last scroll action
    private var contentHeight: Int = 0
    private var viewportHeight: Int = 0
    private var hasScrolled: Boolean = false     // Track if scroll has occurred in this gesture

    // Fixed vibrator initialization
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun attachToScrollView(scrollView: View) {
        when (scrollView) {
            is ScrollView -> attachToRegularScrollView(scrollView)
            is NestedScrollView -> attachToNestedScrollView(scrollView)
        }
    }

    private fun attachToRegularScrollView(scrollView: ScrollView) {
        scrollView.isSmoothScrollingEnabled = false
        setupScrollView(scrollView)
    }

    private fun attachToNestedScrollView(scrollView: NestedScrollView) {
        scrollView.isSmoothScrollingEnabled = false
        setupScrollView(scrollView)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupScrollView(view: View) {
        // Wait for layout to calculate dimensions
        view.post {
            updateDimensions(view)
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    lastY = startY
                    hasScrolled = false // Reset scroll state for new gesture
                    // Update dimensions in case content has changed
                    updateDimensions(view)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (hasScrolled) return@setOnTouchListener true // Only allow one scroll per gesture
                    val deltaY = lastY - event.y
                    val currentTime = System.currentTimeMillis()

                    // Only handle significant movements and respect time threshold
                    if (abs(deltaY) > touchThreshold && (currentTime - lastScrollTime > timeThresholdMs)) {
                        // Get current scroll position
                        val currentScroll = view.scrollY

                        // Calculate overlap (20% of viewport height)
                        val overlap = (viewportHeight * 0.2).toInt()

                        val maxScroll = contentHeight - viewportHeight

                        if (deltaY > 0) {
                            // Scroll down
                            if (currentScroll < maxScroll) {
                                val nextScroll = min(
                                    maxScroll,
                                    currentScroll + viewportHeight - overlap
                                )
                                scrollToPosition(view, nextScroll)
                                performHapticFeedback()
                                lastScrollTime = currentTime
                                hasScrolled = true // Mark as scrolled for this gesture
                            }
                        } else if (deltaY < 0) {
                            // Scroll up
                            if (currentScroll > 0) {
                                val prevScroll = max(
                                    0,
                                    currentScroll - (viewportHeight - overlap)
                                )
                                // Prevent jumping to last page if already at top
                                scrollToPosition(view, prevScroll)
                                performHapticFeedback()
                                lastScrollTime = currentTime
                                hasScrolled = true // Mark as scrolled for this gesture
                            }
                        }
                        lastY = event.y
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    hasScrolled = false // Reset for next gesture
                    true
                }

                else -> false
            }
        }
    }

    private fun updateDimensions(view: View) {
        contentHeight = when (view) {
            is ScrollView -> view.getChildAt(0)?.height ?: 0
            is NestedScrollView -> view.getChildAt(0)?.height ?: 0
            else -> 0
        }
        viewportHeight = view.height
    }

    private fun scrollToPosition(view: View, targetY: Int) {
        // Ensure we don't scroll outside content bounds
        val boundedTargetY = when (view) {
            is ScrollView -> {
                val maxScroll = getMaxScrollY(view)
                targetY.coerceIn(0, maxScroll)
            }

            is NestedScrollView -> {
                val maxScroll = getMaxScrollY(view)
                targetY.coerceIn(0, maxScroll)
            }

            else -> targetY
        }

        // Apply the scroll without any animation
        when (view) {
            is ScrollView -> view.scrollTo(0, boundedTargetY)
            is NestedScrollView -> view.scrollTo(0, boundedTargetY)
        }
    }

    private fun getMaxScrollY(view: View): Int {
        updateDimensions(view)
        return max(0, contentHeight - viewportHeight)
    }

    private fun performHapticFeedback() {
        if (!prefs.useVibrationForPaging) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (_: Exception) {
            // Silently handle any vibration-related errors
        }
    }
}