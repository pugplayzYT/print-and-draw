package com.pugplayzyt.printanddraw

import android.app.Application
import android.content.Context

class PrintAndDrawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        DeveloperConfig.ensureExists(appContext)
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
