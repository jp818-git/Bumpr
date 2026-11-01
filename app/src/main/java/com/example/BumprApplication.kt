package com.example

import android.app.Application

class BumprApplication : Application() {
    companion object {
        lateinit var instance: BumprApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
