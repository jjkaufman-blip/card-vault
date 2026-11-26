package com.cardvault

import android.app.Application
import com.cardvault.data.AppDatabase
import com.cardvault.data.Backup
import com.cardvault.data.Repository
import com.cardvault.data.Settings
import com.cardvault.work.PriceRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CardVaultApp : Application() {
    /** For work that should finish even after leaving a screen (e.g. pricing a just-saved card). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repo: Repository
        private set
    lateinit var backup: Backup
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val settings = Settings(this)
        repo = Repository(this, AppDatabase.build(this), settings)
        backup = Backup(this, repo)
        PriceRefreshWorker.schedule(this, settings.autoRefresh)
    }

    companion object {
        lateinit var instance: CardVaultApp
            private set
    }
}
