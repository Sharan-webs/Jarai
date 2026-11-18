package com.kitagawh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

sealed class SimResult {
    object Match : SimResult()
    object NoSim : SimResult()
    object Unreadable : SimResult()
    class Mismatch(val ends: List<String>) : SimResult()
}

object Sim {
    fun last10(s: String) = s.filter { it.isDigit() }.takeLast(10)

    fun verify(c: Context, typed: String): SimResult {
        val want = last10(typed)
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return SimResult.Unreadable
        val found = mutableListOf<String>()
        var subs = 0
        try {
            val sm = c.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val list = sm.activeSubscriptionInfoList ?: emptyList()
            subs = list.size
            for (info in list) {
                @Suppress("DEPRECATION")
                val legacy: String = info.number ?: ""
                val n = try {
                    if (Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(info.subscriptionId) else legacy
                } catch (e: Exception) { "" }
                if (n.isNotBlank()) found.add(last10(n))
            }
        } catch (e: Exception) { }
        if (found.isEmpty()) {
            try {
                val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                @Suppress("DEPRECATION", "HardwareIds")
                val n = tm.line1Number ?: ""
                if (n.isNotBlank()) found.add(last10(n))
            } catch (e: Exception) { }
        }
        if (found.isNotEmpty()) {
            return if (found.any { it == want }) SimResult.Match
            else SimResult.Mismatch(found.map { "ending " + it.takeLast(2) })
        }
        return if (subs == 0) SimResult.NoSim else SimResult.Unreadable
    }
}
