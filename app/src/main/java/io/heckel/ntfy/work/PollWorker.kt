package io.heckel.ntfy.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.heckel.ntfy.BuildConfig
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.msg.ApiService
import io.heckel.ntfy.msg.NotificationDispatcher
import io.heckel.ntfy.msg.Poller
import io.heckel.ntfy.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    // IMPORTANT:
    //   Every time the worker is changed, the periodic work has to be REPLACEd.
    //   This is facilitated in the MainActivity using the VERSION below.

    init {
        Log.init(ctx) // Init in all entrypoints
    }

    // Expedited work (FCM poll_request) runs as a short foreground service below API 31,
    // which needs a notification. Reuses the low-importance subscriber-service channel.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(FOREGROUND_CHANNEL_ID,
            applicationContext.getString(R.string.channel_subscriber_service_name), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setSilent(true)
            .build()
        return ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Polling for new notifications")
            val repository = Repository.getInstance(applicationContext)
            val dispatcher = NotificationDispatcher(applicationContext, repository)
            val api = ApiService(applicationContext)
            val poller = Poller(api, repository)

            val baseUrl = inputData.getString(INPUT_DATA_BASE_URL)
            val topic = inputData.getString(INPUT_DATA_TOPIC)
            val subscriptions = if (baseUrl != null && topic != null) {
                val subscription = repository.getSubscription(baseUrl, topic) ?: return@withContext Result.success()
                listOf(subscription)
            } else {
                repository.getSubscriptions()
            }

            subscriptions.forEach{ subscription ->
                try {
                    val newNotifications = poller.poll(subscription)
                    newNotifications.forEach { notification ->
                        dispatcher.dispatch(subscription, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed checking messages: ${e.message}", e)
                }
            }
            Log.d(TAG, "Finished polling for new notifications")
            return@withContext Result.success()
        }
    }

    companion object {
        const val VERSION =  BuildConfig.VERSION_CODE
        const val TAG = "NtfyPollWorker"
        private const val FOREGROUND_CHANNEL_ID = "ntfy-subscriber" // same as SubscriberService
        private const val FOREGROUND_NOTIFICATION_ID = 2587
        const val WORK_NAME_PERIODIC_ALL = "NtfyPollWorkerPeriodic" // Do not change
        const val WORK_NAME_ONCE_SINGE_PREFIX = "NtfyPollWorkerSingle" // e.g. NtfyPollWorkerSingle_https://ntfy.sh_mytopic
        const val INPUT_DATA_BASE_URL = "baseUrl"
        const val INPUT_DATA_TOPIC = "topic"
    }
}
