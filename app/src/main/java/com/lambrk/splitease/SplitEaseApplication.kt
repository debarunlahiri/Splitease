package com.lambrk.splitease

import android.app.Application

/** Gives code outside the UI (such as the network client) access to the application context. */
class SplitEaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        /** Null only in plain JVM unit tests, where no Application is created. */
        var instance: SplitEaseApplication? = null
            private set
    }
}
