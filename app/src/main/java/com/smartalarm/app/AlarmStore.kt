package com.smartalarm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlarmStore {
    private const val PREF = "smart_alarm"
    private const val KEY = "alarms"

    fun load(c: Context): MutableList<AlarmData> {
        val raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val a = JSONArray(raw)
        val out = mutableListOf<AlarmData>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val id = o.getLong("id")
            // Filter out temporary test alarms if any were saved
            if (id == 999999L) continue
            out += AlarmData(
                id = id,
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.optBoolean("enabled", true),
                sound = o.optString("sound", "Extreme Siren"),
                customUri = o.optString("customUri", null),
                math = o.optBoolean("math", true),
                camera = o.optBoolean("camera", true),
                simon = o.optBoolean("simon", true)
            )
        }
        return out
    }

    fun save(c: Context, list: List<AlarmData>) {
        val a = JSONArray()
        list.forEach { x ->
            if (x.id != 999999L) {
                a.put(JSONObject().apply {
                    put("id", x.id)
                    put("hour", x.hour)
                    put("minute", x.minute)
                    put("enabled", x.enabled)
                    put("sound", x.sound)
                    x.customUri?.let { put("customUri", it) }
                    put("math", x.math)
                    put("camera", x.camera)
                    put("simon", x.simon)
                })
            }
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    fun delete(c: Context, id: Long) {
        val list = load(c).filter { it.id != id }
        save(c, list)
    }

    fun clearAll(c: Context) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, "[]").apply()
    }
}
