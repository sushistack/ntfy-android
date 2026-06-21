package io.heckel.ntfy.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.heckel.ntfy.R

/**
 * Standalone host for [FeedFragment] (deep-link / external entry).
 *
 * The feed UI itself lives in [FeedFragment] so the same surface can be embedded directly
 * in MainActivity's drawer shell (the app's primary UI). This activity is a thin wrapper that
 * forwards its intent extras into a fresh fragment.
 */
class FeedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_host)

        if (savedInstanceState == null) {
            val fragment = FeedFragment.newInstance(
                subscriptionId = intent.getLongExtra(EXTRA_SUBSCRIPTION_ID, ALL_SUBSCRIPTIONS_ID),
                topic = intent.getStringExtra(EXTRA_SUBSCRIPTION_TOPIC),
                deepLinkNotificationId = intent.getStringExtra(EXTRA_DEEP_LINK_NOTIFICATION_ID),
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.feed_host_container, fragment)
                .commit()
        }
    }

    companion object {
        const val TAG = "NtfyFeedActivity"
        const val EXTRA_SUBSCRIPTION_ID = "subscriptionId"
        const val EXTRA_SUBSCRIPTION_TOPIC = "subscriptionTopic"
        const val EXTRA_DEEP_LINK_NOTIFICATION_ID = "deepLinkNotificationId"
        /** @deprecated Use [ALL_SUBSCRIPTIONS_ID] from FeedViewModel */
        const val ALL_SUBSCRIPTIONS = ALL_SUBSCRIPTIONS_ID
    }
}
