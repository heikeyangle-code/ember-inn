package com.emberinn.app.data

import android.content.Context

/** 首启引导标记（README：仅首次展示，之后直接进角色列表）。 */
object OnboardingPrefs {

    private const val PREFS = "emberinn_prefs"
    private const val KEY_DONE = "onboarding_done"

    fun done(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}
