package tv.brunstad.app

import android.app.Application
import com.npaw.NpawPluginProvider
import com.npaw.core.options.AnalyticsOptions
import com.npaw.core.util.extensions.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BccMediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val options = AnalyticsOptions().apply {
            isAutoDetectBackground = true
            isParseManifest = true
            isEnabled = true
            appName = "bccm-androidtv"
            userObfuscateIp = true
            deviceIsAnonymous = false
            appReleaseVersion = BuildConfig.VERSION_NAME
        }
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
    }
}
