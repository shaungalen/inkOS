package com.github.gezimos.inkos.services

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class NotificationManager private constructor(private val context: Context) {
    data class NotificationInfo(
        val count: Int,
        val title: String?,
        val text: String?,
        val category: String?
    )

    data class ConversationNotification(
        val conversationId: String,
        val conversationTitle: String?,
        val sender: String?,
        val message: String?,
        val timestamp: Long,
        val category: String? = null
    )

    private val notificationInfo = mutableMapOf<String, NotificationInfo>()
    private val _notificationInfoLiveData = MutableLiveData<Map<String, NotificationInfo>>()
    val notificationInfoLiveData: LiveData<Map<String, NotificationInfo>> =
        _notificationInfoLiveData

    private val conversationNotifications =
        mutableMapOf<String, MutableMap<String, ConversationNotification>>()
    private val _conversationNotificationsLiveData =
        MutableLiveData<Map<String, List<ConversationNotification>>>()
    val conversationNotificationsLiveData: LiveData<Map<String, List<ConversationNotification>>> =
        _conversationNotificationsLiveData

    private val NOTIF_SAVE_FILE = "mlauncher_notifications.json"

    companion object {
        @Volatile
        private var INSTANCE: NotificationManager? = null
        fun getInstance(context: Context): NotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getBadgeNotifications(): Map<String, NotificationInfo> {
        // Only filter by badge allowlist
        val prefs = com.github.gezimos.inkos.data.Prefs(context)
        val allowed = prefs.allowedBadgeNotificationApps
        return if (allowed.isEmpty()) {
            notificationInfo.toMap()
        } else {
            notificationInfo.filter { (pkg, _) -> pkg in allowed }
        }
    }

    fun updateBadgeNotification(packageName: String, info: NotificationInfo?) {
        if (info == null) {
            notificationInfo.remove(packageName)
        } else {
            notificationInfo[packageName] = info
        }
        // Only filter by badge allowlist
        val prefs = com.github.gezimos.inkos.data.Prefs(context)
        val allowed = prefs.allowedBadgeNotificationApps
        val filtered = if (allowed.isEmpty()) {
            notificationInfo.toMap()
        } else {
            notificationInfo.filter { (pkg, _) -> pkg in allowed }
        }
        _notificationInfoLiveData.postValue(filtered)
    }

    fun getConversationNotifications(): Map<String, List<ConversationNotification>> {
        // Only filter by allowlist
        val prefs = com.github.gezimos.inkos.data.Prefs(context)
        val allowed = prefs.allowedNotificationApps
        return conversationNotifications
            .filter { (pkg, _) -> allowed.isEmpty() || pkg in allowed }
            .mapValues { entry ->
                entry.value.values.sortedByDescending { n -> n.timestamp }
            }
    }

    fun updateConversationNotification(
        packageName: String,
        conversation: ConversationNotification
    ) {
        val appMap = conversationNotifications.getOrPut(packageName) { mutableMapOf() }
        appMap[conversation.conversationId] = conversation
        _conversationNotificationsLiveData.postValue(getConversationNotifications())
        saveConversationNotifications()
    }

    fun removeConversationNotification(packageName: String, conversationId: String) {
        val appMap = conversationNotifications[packageName]
        if (appMap != null) {
            appMap.remove(conversationId)
            if (appMap.isEmpty()) {
                conversationNotifications.remove(packageName)
            }
            _conversationNotificationsLiveData.postValue(getConversationNotifications())
            saveConversationNotifications()
        }
    }

    fun saveConversationNotifications() {
        try {
            val file = File(context.filesDir, NOTIF_SAVE_FILE)
            val mapToSave = conversationNotifications.mapValues { it.value.values.toList() }
            val json = Gson().toJson(mapToSave)
            file.writeText(json)
        } catch (_: Exception) {
        }
    }

    fun restoreConversationNotifications() {
        try {
            val file = File(context.filesDir, NOTIF_SAVE_FILE)
            if (!file.exists()) return
            val json = file.readText()
            val type = object : TypeToken<Map<String, List<ConversationNotification>>>() {}.type
            val restored: Map<String, List<ConversationNotification>> = Gson().fromJson(json, type)
            conversationNotifications.clear()
            restored.forEach { (pkg, list) ->
                conversationNotifications[pkg] =
                    list.associateBy { it.conversationId }.toMutableMap()
            }
            _conversationNotificationsLiveData.postValue(getConversationNotifications())
        } catch (_: Exception) {
        }
    }

    fun buildNotificationInfo(
        sbn: android.service.notification.StatusBarNotification,
        prefs: com.github.gezimos.inkos.data.Prefs,
        activeNotifications: Array<android.service.notification.StatusBarNotification>
    ): NotificationInfo? {
        val packageNotifications = activeNotifications.filter { it.packageName == sbn.packageName }
        if (packageNotifications.isNotEmpty()) {
            val latestNotification = packageNotifications.maxByOrNull { it.postTime }
            val extras = latestNotification?.notification?.extras

            val showSender = prefs.showNotificationSenderName
            val showGroup = prefs.showNotificationGroupName
            val showMessage = prefs.showNotificationMessage

            // Sender name logic: use full sender name if enabled
            val sender: String? = if (showSender) {
                extras?.getCharSequence("android.title")?.toString()?.trim()
            } else null

            // Group name (conversation title)
            val group = if (showGroup) {
                extras?.getCharSequence("android.conversationTitle")?.toString()?.trim()
            } else null

            // Message text
            val text = if (showMessage) {
                when {
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
            } else null

            var category = latestNotification?.notification?.category
            var showMedia = true
            if (category == android.app.Notification.CATEGORY_TRANSPORT) {
                showMedia = false
                val token =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        extras?.getParcelable(
                            "android.mediaSession",
                            android.media.session.MediaSession.Token::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        extras?.getParcelable<android.media.session.MediaSession.Token>("android.mediaSession")
                    }
                if (token != null) {
                    try {
                        val controller = android.media.session.MediaController(context, token)
                        val playbackState = controller.playbackState
                        if (playbackState != null && playbackState.state == android.media.session.PlaybackState.STATE_PLAYING) {
                            showMedia = true
                        }
                    } catch (_: Exception) {
                    }
                }
                if (!showMedia) {
                    return null
                }
            }

            // Compose title and text based on toggles
            val notifTitle = buildString {
                if (!sender.isNullOrBlank()) append(sender)
                if (!group.isNullOrBlank()) {
                    if (isNotEmpty()) append(": ")
                    append(group)
                }
            }.ifBlank { null }

            val notifText = text

            return NotificationInfo(
                count = packageNotifications.size,
                title = notifTitle,
                text = if ((showMedia || category != android.app.Notification.CATEGORY_TRANSPORT) && showMessage) notifText else null,
                category = if (showMedia) category else null
            )
        }
        return null
    }
}
