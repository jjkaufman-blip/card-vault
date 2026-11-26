package com.cardvault.data

import android.content.Context

/** API keys and preferences, stored only on the phone. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ebayClientId: String
        get() = prefs.getString("ebay_client_id", "") ?: ""
        set(v) = prefs.edit().putString("ebay_client_id", v.trim()).apply()

    var ebayClientSecret: String
        get() = prefs.getString("ebay_client_secret", "") ?: ""
        set(v) = prefs.edit().putString("ebay_client_secret", v.trim()).apply()

    var psaToken: String
        get() = prefs.getString("psa_token", "") ?: ""
        set(v) = prefs.edit().putString("psa_token", v.trim()).apply()

    var autoRefresh: Boolean
        get() = prefs.getBoolean("auto_refresh", true)
        set(v) = prefs.edit().putBoolean("auto_refresh", v).apply()

    var lastRefreshAll: Long
        get() = prefs.getLong("last_refresh_all", 0L)
        set(v) = prefs.edit().putLong("last_refresh_all", v).apply()

    // Cached eBay app token
    var ebayToken: String
        get() = prefs.getString("ebay_token", "") ?: ""
        set(v) = prefs.edit().putString("ebay_token", v).apply()

    var ebayTokenExpires: Long
        get() = prefs.getLong("ebay_token_expires", 0L)
        set(v) = prefs.edit().putLong("ebay_token_expires", v).apply()

    val hasEbay: Boolean get() = ebayClientId.isNotBlank() && ebayClientSecret.isNotBlank()
    val hasPsa: Boolean get() = psaToken.isNotBlank()
}
