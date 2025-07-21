package com.github.gezimos.inkos.helper.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.SuperscriptSpan
import android.widget.TextView
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.services.NotificationManager

object NotificationBadgeUtil {
    fun updateNotificationForView(
        context: Context,
        prefs: Prefs,
        textView: TextView,
        notifications: Map<String, NotificationManager.NotificationInfo>
    ) {
        val appModel = prefs.getHomeAppModel(textView.id)
        val packageName = appModel.activityPackage
        val notificationInfo = notifications[packageName]
        // Filtering is now handled in NotificationManager, so no need to filter here
        val customLabel = prefs.getAppAlias("app_alias_$packageName")
        val displayName = if (customLabel.isNotEmpty()) customLabel else appModel.activityLabel

        // Filter out unwanted Signal (or similar) system messages
        val unwantedMessages =
            listOf("background connection established", "background connection enabled")
        val isUnwanted = notificationInfo != null && (
                (notificationInfo.title?.trim()?.let {
                    unwantedMessages.any { msg ->
                        it.equals(
                            msg,
                            ignoreCase = true
                        )
                    }
                } == true) ||
                        (notificationInfo.text?.trim()?.let {
                            unwantedMessages.any { msg ->
                                it.equals(
                                    msg,
                                    ignoreCase = true
                                )
                            }
                        } == true)
                )

        if (notificationInfo != null && prefs.showNotificationBadge && !isUnwanted) {
            val spanBuilder = SpannableStringBuilder()

            // Add notification dot if notification exists and is not media
            val appFont = prefs.getFontForContext("apps")
                .getFont(context, prefs.getCustomFontPathForContext("apps"))
            val appNameSpan = SpannableString(displayName)
            if (appFont != null) {
                appNameSpan.setSpan(
                    CustomTypefaceSpan(appFont),
                    0,
                    displayName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val title = notificationInfo.title
            val text = notificationInfo.text
            val isMedia = notificationInfo.category == android.app.Notification.CATEGORY_TRANSPORT
            val isMediaPlaying = isMedia && (!title.isNullOrBlank() || !text.isNullOrBlank())

            // Only show music note if media is actually playing
            if (isMedia && isMediaPlaying && prefs.showMediaIndicator) {
                // Music note as superscript (exponent)
                val musicNote = SpannableString("\u266A")
                musicNote.setSpan(
                    SuperscriptSpan(),
                    0,
                    musicNote.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                musicNote.setSpan(
                    AbsoluteSizeSpan((textView.textSize * 0.8).toInt()),
                    0,
                    musicNote.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spanBuilder.append(appNameSpan)
                spanBuilder.append(musicNote)
            } else if (!isMedia && notificationInfo.count > 0) {
                // Solid dot (bullet) to the left of the app name
                val bulletSpan = SpannableString("\u2022 ")
                // Optionally style bulletSpan here (font/size)
                spanBuilder.append(bulletSpan)
                spanBuilder.append(appNameSpan)
            } else {
                // No badge, just the app name
                spanBuilder.append(appNameSpan)
            }

            // Notification text logic
            if (isMedia && isMediaPlaying && prefs.showMediaName) {
                // For media, show only the first part (title or artist), not the full name
                spanBuilder.append("\n")
                val charLimit = prefs.homeAppCharLimit
                // Split by common separators and take the first part
                val firstPart = title?.split(" - ", ":", "|")?.firstOrNull()?.trim() ?: ""
                val notifText =
                    if (!firstPart.isNullOrBlank() && firstPart.lowercase() != "transport") firstPart.take(
                        charLimit
                    ) else ""
                val notifSpan = SpannableString(notifText)
                notifSpan.setSpan(
                    AbsoluteSizeSpan(prefs.labelnotificationsTextSize, true),
                    0,
                    notifText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val notificationFont = prefs.getFontForContext("notification")
                    .getFont(context, prefs.getCustomFontPathForContext("notification"))
                if (notificationFont != null) {
                    notifSpan.setSpan(
                        CustomTypefaceSpan(notificationFont),
                        0,
                        notifText.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                spanBuilder.append(notifSpan)
            } else if (!isMedia && prefs.showNotificationText && (!title.isNullOrBlank() || !text.isNullOrBlank())) {
                // For messaging/other notifications, apply toggles
                spanBuilder.append("\n")
                val charLimit = prefs.homeAppCharLimit
                val showName = prefs.showNotificationSenderName
                val showGroup = prefs.showNotificationGroupName
                val showMessage = prefs.showNotificationMessage

                // Parse sender and group from title, avoid duplication
                var sender = ""
                var group = ""
                if (!title.isNullOrBlank()) {
                    val parts = title.split(": ", limit = 2)
                    if (packageName == "org.thoughtcrime.securesms") { // Signal
                        if (parts.size == 1) {
                            // Single-person conversation: treat as sender
                            sender = parts[0]
                            group = ""
                        } else {
                            group = parts.getOrNull(0) ?: ""
                            sender = parts.getOrNull(1) ?: ""
                            // If group is empty or same as sender, treat as single-person
                            if (group.isBlank() || group == sender) {
                                sender = group
                                group = ""
                            }
                        }
                    } else {
                        sender = parts.getOrNull(0) ?: ""
                        group = parts.getOrNull(1) ?: ""
                    }
                }
                // If group is same as sender, don't show group
                if (group == sender) group = ""

                val message = if (showMessage) text ?: "" else ""

                val notifText = buildString {
                    if (showName && sender.isNotBlank()) append(sender)
                    if (showGroup && group.isNotBlank()) {
                        if (isNotEmpty()) append(": ")
                        append(group)
                    }
                    if (showMessage && message.isNotBlank()) {
                        if (isNotEmpty()) append(": ")
                        append(message)
                    }
                }.take(charLimit)
                val notifSpan = SpannableString(notifText)
                notifSpan.setSpan(
                    AbsoluteSizeSpan(prefs.labelnotificationsTextSize, true),
                    0,
                    notifText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val notificationFont = prefs.getFontForContext("notification")
                    .getFont(context, prefs.getCustomFontPathForContext("notification"))
                if (notificationFont != null) {
                    notifSpan.setSpan(
                        CustomTypefaceSpan(notificationFont),
                        0,
                        notifText.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                spanBuilder.append(notifSpan)
            }

            textView.text = spanBuilder
        } else {
            // No notification badge, just show the app name
            val appFont = prefs.getFontForContext("apps")
                .getFont(context, prefs.getCustomFontPathForContext("apps"))
            val appNameSpan = SpannableString(displayName)
            if (appFont != null) {
                appNameSpan.setSpan(
                    CustomTypefaceSpan(appFont),
                    0,
                    displayName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            textView.text = appNameSpan
        }
    }
}
