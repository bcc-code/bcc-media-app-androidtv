package tv.brunstad.app

import android.app.Application
import com.npaw.NpawPluginProvider
import com.npaw.core.options.AnalyticsOptions
import com.npaw.core.util.extensions.Log
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import tv.brunstad.app.data.AnalyticsManager

@HiltAndroidApp
class BccMediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.release = BuildConfig.VERSION_NAME
                options.isAnrEnabled = true
                options.environment = if (BuildConfig.DEBUG) "development" else "production"
            }
        }

        val options = AnalyticsOptions().apply {
            isAutoDetectBackground = true
            isParseManifest = true
            isEnabled = true
            appName = "bccm-androidtv"
            userObfuscateIp = true
            deviceIsAnonymous = false
            appReleaseVersion = BuildConfig.VERSION_NAME
        }
        if (BuildConfig.NPAW_ACCOUNT_CODE.isNotEmpty()) {
            try {
                NpawPluginProvider.initialize(
                    BuildConfig.NPAW_ACCOUNT_CODE,
                    this,
                    options,
                    null,
                    null,
                    Log.Level.INFO
                )
                // Set after initialize — setting in the options constructor may not persist
                NpawPluginProvider.getInstance()?.analyticsOptions?.appReleaseVersion = BuildConfig.VERSION_NAME
            } catch (e: Exception) {
                android.util.Log.e("BccMediaApplication", "NPAW initialization failed", e)
            }
        } else {
            android.util.Log.w("BccMediaApplication", "NPAW_ACCOUNT_CODE is empty, skipping NPAW initialization")
        }

        // Initialize Rudderstack analytics
        AnalyticsManager.initialize(
            BuildConfig.RUDDERSTACK_WRITE_KEY,
            BuildConfig.RUDDERSTACK_DATA_PLANE_URL,
            this
        )
    }
}
