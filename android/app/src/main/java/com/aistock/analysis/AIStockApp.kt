package com.aistock.analysis

import android.app.Application
import com.aistock.analysis.di.ServiceLocator

class AIStockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
