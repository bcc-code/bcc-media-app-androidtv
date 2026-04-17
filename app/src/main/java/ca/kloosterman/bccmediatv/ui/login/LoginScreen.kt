package ca.kloosterman.bccmediatv.ui.login

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import ca.kloosterman.bccmediatv.R
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.kloosterman.bccmediatv.auth.LoginUiState
import ca.kloosterman.bccmediatv.auth.LoginViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is LoginUiState.Authenticated) onAuthenticated()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is LoginUiState.Loading -> {
                    Text(stringResource(R.string.login_connecting), fontSize = 24.sp)
                }

                is LoginUiState.ShowCode -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(48.dp)
                    ) {
                        QrCode(content = s.verificationUriComplete, modifier = Modifier.size(240.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = stringResource(R.string.login_sign_in_title),
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.login_scan_qr), fontSize = 20.sp)
                            Text(
                                text = stringResource(R.string.login_site),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.login_enter_code), fontSize = 20.sp)
                            Text(
                                text = s.userCode,
                                fontSize = 56.sp,
                                letterSpacing = 6.sp,
                                style = MaterialTheme.typography.displaySmall
                            )
                        }
                    }
                }

                is LoginUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.login_failed, s.message), fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.startDeviceFlow() }) {
                            Text(stringResource(R.string.login_try_again))
                        }
                    }
                }

                is LoginUiState.Authenticated -> { /* handled by LaunchedEffect */ }
            }
        }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }
    Box(
        modifier = modifier.background(androidx.compose.ui.graphics.Color.White).padding(8.dp)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Sign-in QR code",
            modifier = Modifier.fillMaxSize()
        )
    }
}
