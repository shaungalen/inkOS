package com.github.gezimos.inkos.services

import android.content.Context
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManager.getInstance(applicationContext)
        notificationManager.restoreConversationNotifications()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Only restore active media playback notifications after service (re)start
        activeNotifications?.filter {
            it.notification.category == android.app.Notification.CATEGORY_TRANSPORT
        }?.forEach { sbn ->
            updateBadgeNotification(sbn)
            if (shouldShowNotification(sbn.packageName)) {
                updateConversationNotifications(sbn)
            }
        }
    }

    private fun shouldShowNotification(packageName: String): Boolean {
        val prefs = com.github.gezimos.inkos.data.Prefs(this)
        val allowed = prefs.allowedNotificationApps
        // If allowlist is empty, allow all. Otherwise, only allow if in allowlist.
        return allowed.isEmpty() || allowed.contains(packageName)
    }

    private fun shouldShowBadgeNotification(packageName: String): Boolean {
        val prefs = com.github.gezimos.inkos.data.Prefs(this)
        val allowed = prefs.allowedBadgeNotificationApps
        // If allowlist is empty, allow all. Otherwise, only allow if in allowlist.
        return allowed.isEmpty() || allowed.contains(packageName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Always update badge notification, let NotificationManager filter by allowlist
        updateBadgeNotification(sbn)
        // Only update conversation notifications if allowed in notification allowlist
        if (shouldShowNotification(sbn.packageName)) {
            updateConversationNotifications(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Always update badge notification, let NotificationManager filter by allowlist
        updateBadgeNotification(sbn)
    }

    private fun updateBadgeNotification(sbn: StatusBarNotification) {
        val activeNotifications = getActiveNotifications()
        val packageNotifications = activeNotifications.filter { it.packageName == sbn.packageName }
        val prefs = com.github.gezimos.inkos.data.Prefs(applicationContext)
        if (packageNotifications.isNotEmpty()) {
            val latestNotification = packageNotifications.maxByOrNull { it.postTime }
            val extras = latestNotification?.notification?.extras
            val title = extras?.getCharSequence("android.title")?.toString()
            val text = when {
                extras?.getCharSequence("android.bigText") != null ->
                    extras.getCharSequence("android.bigText")?.toString()?.take(30)

                extras?.getCharSequence("android.text") != null ->
                    extras.getCharSequence("android.text")?.toString()?.take(30)

                extras?.getCharSequenceArray("android.textLines") != null -> {
                    val lines = extras.getCharSequenceArray("android.textLines")
                    lines?.lastOrNull()?.toString()?.take(30)
                }

                else -> null
            }
            var category = latestNotification?.notification?.category
            var showMedia = true
            if (category == android.app.Notification.CATEGORY_TRANSPORT) {
                showMedia = false
                val token =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        extras?.getParcelable(
                            "android.mediaSession",
                            MediaSession.Token::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        extras?.getParcelable<MediaSession.Token>("android.mediaSession")
                    }
                if (token != null) {
                    try {
                        val controller = android.media.session.MediaController(this, token)
                        val playbackState = controller.playbackState
                        if (playbackState != null && playbackState.state == android.media.session.PlaybackState.STATE_PLAYING) {
                            showMedia = true
                        }
                    } catch (_: Exception) {
                    }
                }
                if (!showMedia) {
                    notificationManager.updateBadgeNotification(sbn.packageName, null)
                    return
                }
            }
            val showSender = prefs.showNotificationSenderName
            val showGroup = prefs.showNotificationGroupName
            val showMessage = prefs.showNotificationMessage
            notificationManager.updateBadgeNotification(
                sbn.packageName,
                NotificationManager.NotificationInfo(
                    count = packageNotifications.size,
                    title = when {
                        showSender && showGroup && !title.isNullOrBlank() -> title
                        showSender && !showGroup && !title.isNullOrBlank() -> title
                        !showSender && showGroup -> null
                        else -> null
                    },
                    text = if ((showMedia || category != android.app.Notification.CATEGORY_TRANSPORT) && showMessage) text else null,
                    category = if (showMedia) category else null
                )
            )
        } else {
            notificationManager.updateBadgeNotification(sbn.packageName, null)
        }
    }

    private fun updateConversationNotifications(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val conversationId = extras.getString("android.conversationTitle")
            ?: extras.getString("android.title")
            ?: "default"
        val conversationTitle = extras.getString("android.conversationTitle")
        val sender = extras.getString("android.title")
        val message = when {
            extras.getCharSequence("android.bigText") != null ->
                extras.getCharSequence("android.bigText")?.toString()

            extras.getCharSequence("android.text") != null ->
                extras.getCharSequence("android.text")?.toString()

            extras.getCharSequenceArray("android.textLines") != null -> {
                val lines = extras.getCharSequenceArray("android.textLines")
                lines?.lastOrNull()?.toString()
            }

            else -> null
        }
        val timestamp = sbn.postTime
        notificationManager.updateConversationNotification(
            packageName,
            NotificationManager.ConversationNotification(
                conversationId = conversationId,
                conversationTitle = conversationTitle,
                sender = sender,
                message = message,
                timestamp = timestamp,
                category = sbn.notification.category
            )
        )
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }
}
