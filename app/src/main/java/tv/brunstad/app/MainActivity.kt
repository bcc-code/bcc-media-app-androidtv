package tv.brunstad.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import java.util.Locale
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import tv.brunstad.app.auth.AuthRepository
import tv.brunstad.app.auth.ProfileStore
import tv.brunstad.app.data.LanguageRepository
import tv.brunstad.app.ui.episode.EpisodeDetailScreen
import tv.brunstad.app.ui.home.HomeScreen
import tv.brunstad.app.ui.login.LoginScreen
import tv.brunstad.app.ui.page.PageScreen
import tv.brunstad.app.ui.player.PlayerScreen
import tv.brunstad.app.ui.settings.SettingsScreen
import tv.brunstad.app.ui.season.SeasonDetailScreen
import tv.brunstad.app.ui.person.PersonDetailScreen
import tv.brunstad.app.ui.show.ShowDetailScreen
import tv.brunstad.app.ui.profile.ProfilePickerScreen
import tv.brunstad.app.ui.splash.SplashScreen
import tv.brunstad.app.ui.theme.BCCMediaTVTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private val SUPPORTED_LANGUAGE_CODES = setOf(
    "bg", "de", "en", "es", "fi", "fr", "hr", "hu", "it", "nl", "no", "pl", "pt", "ro", "ru", "sl", "ta", "tr"
)

/** Map locale strings (e.g. "nb", "nb-NO", "no-NO") to supported language codes. */
private fun mapLocaleToSupportedCode(locale: String): String? {
    val lang = locale.substringBefore("-").substringBefore("_").lowercase()
    // Norwegian variants
    if (lang == "nb" || lang == "nn") return "no"
    return if (lang in SUPPORTED_LANGUAGE_CODES) lang else null
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var languageRepository: LanguageRepository

    @Inject
    lateinit var analyticsManager: tv.brunstad.app.data.AnalyticsManager

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var navController: NavController? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent, navController)
    }

    private fun handleDeepLinkIntent(intent: Intent?, nav: NavController?) {
        val uri = intent?.data ?: return
        analyticsManager.trackDeepLinkOpened(uri.toString())
        if (uri.scheme == "bccmediatv" && uri.host == "episode") {
            val episodeId = uri.lastPathSegment ?: return
            nav?.navigate("episode/$episodeId") {
                // If already on the episode page for this ID, don't duplicate it
                launchSingleTop = true
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Apply locale before Compose inflates — guarantees correct strings from the first frame.
    // Hilt is not yet available here so we read SharedPreferences directly.
    override fun attachBaseContext(newBase: Context) {
        val activeId = newBase.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ProfileStore.KEY_ACTIVE_PROFILE_ID, null)
        val langKey = LanguageRepository.profileLanguageKey(activeId)
        val lang = newBase.getSharedPreferences("bccmedia_prefs", Context.MODE_PRIVATE)
            .getString(langKey, LanguageRepository.DEFAULT) ?: LanguageRepository.DEFAULT
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val activeId = getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ProfileStore.KEY_ACTIVE_PROFILE_ID, null)
            val langKey = LanguageRepository.profileLanguageKey(activeId)
            val lang = getSharedPreferences("bccmedia_prefs", Context.MODE_PRIVATE)
                .getString(langKey, LanguageRepository.DEFAULT) ?: LanguageRepository.DEFAULT
            overrideConfiguration.setLocale(Locale(lang))
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Feature #25: auto-detect language from JWT if user hasn't explicitly set one
        if (!languageRepository.hasLanguageSet()) {
            val tokenLocale = authRepository.getLocaleFromToken()
            if (tokenLocale != null) {
                val detected = mapLocaleToSupportedCode(tokenLocale)
                if (detected != null && detected != LanguageRepository.DEFAULT) {
                    languageRepository.setLanguage(detected)
                    // Recreate so attachBaseContext picks up the new locale for stringResource
                    recreate()
                    return
                }
            }
        }

        val openReason = when {
            intent?.data != null -> "deep_link"
            savedInstanceState == null -> "cold_start"
            else -> "warm_start"
        }
        analyticsManager.trackApplicationOpened(openReason, coldStart = savedInstanceState == null)

        setContent {
            BCCMediaTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val navController = rememberNavController().also { this@MainActivity.navController = it }
                    val loggedIn = authRepository.isLoggedIn()
                    val start = if (loggedIn) "home" else "login"
                    // Show splash only on a true fresh launch — not on Activity recreation
                    // (e.g. language change causes a recreation with non-null savedInstanceState)
                    // Also skip splash when launched from a deep link (Continue Watching)
                    val hasDeepLink = intent?.data?.scheme == "bccmediatv"
                    var showSplash by remember { mutableStateOf(loggedIn && savedInstanceState == null && !hasDeepLink) }

                    Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = start) {
                        composable("login") {
                            LoginScreen(onAuthenticated = {
                                // Feature #25: after fresh login, detect locale from JWT
                                if (!languageRepository.hasLanguageSet()) {
                                    val tokenLocale = authRepository.getLocaleFromToken()
                                    if (tokenLocale != null) {
                                        val detected = mapLocaleToSupportedCode(tokenLocale)
                                        if (detected != null && detected != LanguageRepository.DEFAULT) {
                                            languageRepository.setLanguage(detected)
                                        }
                                    }
                                }
                                showSplash = true
                                navController.navigate("home") {
                                    popUpTo(0) { inclusive = true }
                                }
                            })
                        }
                        composable("home") {
                            HomeScreen(
                                onEpisodeClick = { id -> navController.navigate("episode/$id") },
                                onPageClick = { code -> navController.navigate("page/$code") },
                                onSeasonClick = { id -> navController.navigate("season/$id") },
                                onShowClick = { id -> navController.navigate("show/$id") },
                                onPersonClick = { id -> navController.navigate("person/$id") },
                                onSettingsClick = { navController.navigate("settings") },
                                onProfileClick = { navController.navigate("profile_picker") },
                                onAuthRequired = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onLogout = { hasOtherProfiles ->
                                    if (hasOtherProfiles) {
                                        recreate()
                                    } else {
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                },
                                onSwitchAccount = { navController.navigate("profile_picker") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("profile_picker") {
                            ProfilePickerScreen(
                                onAddAccount = {
                                    // Do NOT pop profile_picker — back from login returns here
                                    navController.navigate("login")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "episode/{episodeId}?offset={offset}&autoplay={autoplay}",
                            arguments = listOf(
                                androidx.navigation.navArgument("episodeId") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("offset") {
                                    type = androidx.navigation.NavType.IntType
                                    defaultValue = 0
                                },
                                androidx.navigation.navArgument("autoplay") {
                                    type = androidx.navigation.NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStack ->
                            val id = backStack.arguments?.getString("episodeId") ?: return@composable
                            val offset = backStack.arguments?.getInt("offset") ?: 0
                            val autoplay = backStack.arguments?.getBoolean("autoplay") ?: false
                            EpisodeDetailScreen(
                                onPlay = { progress -> navController.navigate("player/$id?progress=$progress") },
                                onBack = { navController.popBackStack() },
                                onShowClick = { showId -> navController.navigate("show/$showId") },
                                onSeasonClick = { seasonId -> navController.navigate("season/$seasonId") },
                                chapterOffset = offset,
                                fromAutoplay = autoplay
                            )
                        }
                        composable(
                            route = "player/{episodeId}?progress={progress}",
                            arguments = listOf(
                                androidx.navigation.navArgument("episodeId") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("progress") {
                                    type = androidx.navigation.NavType.IntType
                                    defaultValue = 0
                                }
                            )
                        ) {
                            PlayerScreen(
                                onBack = { navController.popBackStack() },
                                onEpisodeEnded = { nextId ->
                                    // Always pop the player first, then push next episode detail if there is one
                                    navController.popBackStack()
                                    if (nextId != null) {
                                        navController.navigate("episode/$nextId?autoplay=true")
                                    }
                                }
                            )
                        }
                        composable("page/{code}") {
                            PageScreen(
                                onEpisodeClick = { id -> navController.navigate("episode/$id") },
                                onPageClick = { code -> navController.navigate("page/$code") },
                                onSeasonClick = { id -> navController.navigate("season/$id") },
                                onShowClick = { id -> navController.navigate("show/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("show/{showId}") {
                            ShowDetailScreen(
                                onEpisodeClick = { id -> navController.navigate("episode/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("season/{seasonId}") {
                            SeasonDetailScreen(
                                onEpisodeClick = { id -> navController.navigate("episode/$id") },
                                onShowClick = { id -> navController.navigate("show/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("person/{personId}") {
                            PersonDetailScreen(
                                onEpisodeClick = { id, startPos ->
                                    navController.navigate("episode/$id?offset=$startPos")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // Handle cold-start deep link (e.g. tapped from Google TV Continue Watching)
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (loggedIn) handleDeepLinkIntent(intent, navController)
                    }

                    // Navigate to login if the refresh token is rejected by Auth0
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        authRepository.authExpiredEvent.collect {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    if (showSplash) {
                        SplashScreen(onComplete = { showSplash = false })
                    }
                    } // end Box
                }
            }
        }
    }
}
