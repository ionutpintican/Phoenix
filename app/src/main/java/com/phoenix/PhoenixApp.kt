package com.phoenix

import android.app.Application
import com.phoenix.radio.RadioFavorites

class PhoenixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Favorites are local (SharedPreferences) — safe to load before any permission grant.
        RadioFavorites.init(this)
    }
}
