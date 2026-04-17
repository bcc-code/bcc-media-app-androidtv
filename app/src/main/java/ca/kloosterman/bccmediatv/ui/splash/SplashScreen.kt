package ca.kloosterman.bccmediatv.ui.splash

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import ca.kloosterman.bccmediatv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Held at file scope so the MediaPlayer survives composable disposal
private var splashPlayer: MediaPlayer? = null

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val logoAlpha = remember { Animatable(0f) }

    // MediaPlayer lifecycle — held in file-scope var so GC doesn't kill it after navigation
    DisposableEffect(Unit) {
        splashPlayer = MediaPlayer.create(context, R.raw.bmm_jingle)
        splashPlayer?.setOnCompletionListener {
            it.release()
            splashPlayer = null
        }
        splashPlayer?.start()
        onDispose { /* audio plays to end; released via onCompletionListener */ }
    }

    // Animation sequence
    LaunchedEffect(Unit) {
        // Fade in over 1800ms
        launch { logoAlpha.animateTo(1f, tween(1800)) }
        // Hold until the 4-second mark, then fade out slowly
        delay(4000)
        logoAlpha.animateTo(0f, tween(1500))
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.bcc_logo_raster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(logoAlpha.value)
        )
    }
}
