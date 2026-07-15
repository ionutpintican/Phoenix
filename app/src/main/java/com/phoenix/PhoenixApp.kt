package com.phoenix

import android.app.Application
import com.phoenix.radio.RadioFavorites
import com.phoenix.settings.Settings

class PhoenixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Local prefs (SharedPreferences) — safe to load before any permission grant. Runs in
        // every process that starts the app, including the car's PlaybackService process.
        RadioFavorites.init(this)
        Settings.init(this)
    }
}
