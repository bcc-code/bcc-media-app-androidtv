package tv.brunstad.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

enum class CardStyle {
    LANDSCAPE,  // 16:9, 240dp wide — default
    POSTER,     // 2:3, 160dp wide — for poster/portrait content
    SQUARE,     // 1:1, 180dp wide — for icons/avatars
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AvatarCard(
    name: String,
    imageUrl: String?,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(shape = CircleShape),
            modifier = Modifier
                .size(96.dp)
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = name.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(96.dp)
        )
    }
}

/** Compact text-only card for Page-type items that have no image (e.g. category links). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PageLinkCard(
    title: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = ButtonDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp, MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1.05f),
        glow = ButtonDefaults.glow(),
        modifier = modifier
            .width(240.dp)
            .height(48.dp)
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun CardStyle.cardWidth(): Dp = when (this) {
    CardStyle.LANDSCAPE -> 240.dp
    CardStyle.POSTER    -> 160.dp
    CardStyle.SQUARE    -> 120.dp
}

private fun CardStyle.cardAspectRatio(): Float = when (this) {
    CardStyle.LANDSCAPE -> 16f / 9f
    CardStyle.POSTER    -> 2f / 3f
    CardStyle.SQUARE    -> 1f
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContentCard(
    title: String,
    imageUrl: String?,
    subtitle: String? = null,
    showTitle: String? = null,
    watched: Boolean = false,
    progressFraction: Float? = null,
    description: String? = null,
    durationSeconds: Int? = null,
    style: CardStyle = CardStyle.LANDSCAPE,
    scale: Float = 1f,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val cardWidth = style.cardWidth() * scale
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(cardWidth)
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged { isFocused = it.hasFocus }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(style.cardAspectRatio())
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Watched checkmark — top-right corner
                if (watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(Color(0xFF43A047), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Duration badge — bottom-right corner
                if (durationSeconds != null && durationSeconds > 0) {
                    val h = durationSeconds / 3600
                    val m = (durationSeconds % 3600) / 60
                    val s = durationSeconds % 60
                    val label = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }

                // Progress bar at the bottom of the image
                if (progressFraction != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // Text beneath the image
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showTitle != null) {
                Text(
                    text = showTitle,
                    fontSize = (11 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Text(
                text = title,
                fontSize = (14 * scale).sp,
                lineHeight = (18 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = (11 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    fontSize = (10 * scale).sp,
                    lineHeight = (14 * scale).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
