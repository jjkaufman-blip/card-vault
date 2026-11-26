package com.cardvault.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cardvault.CardVaultApp
import java.util.concurrent.TimeUnit

/** Re-prices the whole collection once a day in the background (on Wi-Fi or data). */
class PriceRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as CardVaultApp).repo
        if (!repo.settings.hasEbay) return Result.success()
        return try {
            repo.refreshAll()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "daily-price-refresh"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
