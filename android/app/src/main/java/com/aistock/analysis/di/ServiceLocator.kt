package com.aistock.analysis.di

import android.content.Context
import com.aistock.analysis.auth.GoogleAuth
import com.aistock.analysis.billing.BillingManager
import com.aistock.analysis.data.AppRepository
import com.aistock.analysis.data.Prefs
import com.aistock.analysis.net.ApiClient

object ServiceLocator {
    lateinit var prefs: Prefs
        private set
    lateinit var api: ApiClient
        private set
    lateinit var repo: AppRepository
        private set
    lateinit var auth: GoogleAuth
        private set
    lateinit var billing: BillingManager
        private set

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        prefs = Prefs(app)
        api = ApiClient(prefs)
        repo = AppRepository(api, prefs)
        auth = GoogleAuth(app)
        billing = BillingManager(app, repo)
    }
}
