package com.github.gezimos.inkos.helper.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.appcompat.widget.AppCompatTextView
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Prefs

class BatteryReceiver : BroadcastReceiver() {

    private lateinit var prefs: Prefs

    override fun onReceive(context: Context, intent: Intent) {
        prefs = Prefs(context)
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val contextBattery = context as? Activity
        val batteryTextView = (contextBattery)?.findViewById<AppCompatTextView>(R.id.battery)

        val batteryLevel = level * 100 / scale.toFloat()

        val batteryLevelInt = batteryLevel.toInt()
        batteryTextView?.text = buildString {
            append(batteryLevelInt)
            append("%")
        }

    }
}

