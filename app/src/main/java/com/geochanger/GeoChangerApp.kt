package com.geochanger

import android.app.Application
import org.osmdroid.config.Configuration

class GeoChangerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
    }
}
