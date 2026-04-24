package tv.brunstad.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import tv.brunstad.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.Button
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.brunstad.app.data.ShowBookmarkItem
import tv.brunstad.app.graphql.GetMyListQuery
import tv.brunstad.app.graphql.GetPageQuery
import tv.brunstad.app.graphql.SearchQuery
import tv.brunstad.app.ui.mylist.MyListViewModel
import tv.brunstad.app.ui.profile.ProfilePickerViewModel
import tv.brunstad.app.util.titleCaseForLanguage

internal data class CardData(val title: String, val imageUrl: String?, val episodeId: String?, val pageCode: String? = null, val seasonId: String? = null, val showId: String? = null, val showTitle: String? = null, val progressFraction: Float? = null, val description: String? = null, val subtitle: String? = null, val watched: Boolean = false, val durationSeconds: Int? = null)

private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "").trim()

private fun parseHexColor(hex: String): androidx.compose.ui.graphics.Color {
    val cleaned = hex.removePrefix("#")
    val argb = when (cleaned.length) {
        6 -> 0xFF000000 or cleaned.toLong(16)
        8 -> cleaned.toLong(16)
        else -> 0xFF888888
    }
    return androidx.compose.ui.graphics.Color(argb.toInt())
}

private fun NavIcon.vector(): ImageVector? = when (this) {
    NavIcon.HOME -> Icons.Default.Home
    NavIcon.SEARCH -> Icons.Default.Search
    NavIcon.SETTINGS -> Icons.Default.Settings
    NavIcon.BOOKMARK -> Icons.Default.Bookmark
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onEpisodeClick: (String) -> Unit = {},
    onPageClick: (String) -> Unit = {},
    onSeasonClick: (String) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAuthRequired: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    myListViewModel: MyListViewModel = hiltViewModel(),
    profileViewModel: ProfilePickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onAuthRequired()
    }

    val profileState by profileViewModel.state.collectAsState()
    val activeInitials = profileState.profiles.find { it.userId == profileState.activeProfileId }?.initials
    var navExpanded by remember { mutableStateOf(false) }

    val homeCode = state.navItems.firstOrNull { it.icon == NavIcon.HOME }?.code ?: ""
    val navFocusRequester = remember { FocusRequester() }
    // Intercept back only when there's something to do before exiting:
    // 1. Nav hidden → focus the nav column (its onFocusChanged sets navExpanded=true,
    //    and Compose routes focus to the selected child automatically)
    // 2. Nav shown + not on Home → go Home, keep nav open
    // When nav is shown and Home is already selected, enabled=false lets the system exit the app.
    BackHandler(enabled = !navExpanded || state.selectedCode != homeCode) {
        if (!navExpanded) {
            runCatching { navFocusRequester.requestFocus() }
        } else {
            viewModel.selectPage(homeCode)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onScreenResumed()
            profileViewModel.refresh()
        }
    }
    val contentFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val myListFocusRequester = remember { FocusRequester() }

    val navItemFocusRequesters = remember(state.navItems.size) {
        List(state.navItems.size) { FocusRequester() }
    }
    val selectedNavIndex = state.navItems.indexOfFirst { it.code == state.selectedCode }

    // One FocusRequester per section (points to the first card of that section row).
    val sections = state.pages[state.selectedCode]?.sections ?: emptyList()
    // Pre-allocate 20 requesters per page so they stay stable as sections load in batches.
    // Recreating on sections.size caused new FocusRequester objects that weren't yet
    // attached to composables, breaking Down navigation from the hero.
    val sectionFocusRequesters = remember(state.selectedCode) {
        List(20) { FocusRequester() }
    }
    // Which section row the user was last on. Null = page just opened, go to row 0.
    // remember(selectedCode) resets to null whenever the page changes.
    var lastFocusedSectionIndex by remember(state.selectedCode) { mutableStateOf<Int?>(null) }

    if (state.isBootstrapping) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.loading), fontSize = 24.sp)
        }
        return
    }

    state.error?.let {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.error_prefix, it), fontSize = 18.sp)
        }
        return
    }

    // Full-screen hero background image — set by HeroSection when it has focus
    var heroBackground by remember(state.selectedCode) { mutableStateOf<String?>(null) }

    val navWidth by animateDpAsState(
        targetValue = if (navExpanded) 190.dp else 68.dp,
        animationSpec = tween(durationMillis = 200),
        label = "navWidth"
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (navExpanded) 0.35f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrimAlpha"
    )
    // Only show text in nav items once the column is wide enough — prevents flicker
    // where text appears while the column is still animating from the narrow width.
    val itemsExpanded = navWidth > 150.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen hero background — crossfades between images as focus moves through hero cards
        Crossfade(targetState = heroBackground, animationSpec = tween(600), label = "heroBg") { imageUrl ->
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient: transparent top half, fades to black on bottom half
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            1f to Color.Black
                        )
                    )
                )
            }
        }

        // Content — always full width; left padding keeps it clear of the collapsed icon strip
        // Initialize with displayMetrics height so the hero has a real size on frame 1,
        // preventing TvLazyColumn from auto-scrolling to section rows before hero loads.
        val context = LocalContext.current
        val density = LocalDensity.current
        var contentHeightPx by remember { mutableStateOf(context.resources.displayMetrics.heightPixels) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 68.dp)
                .focusRequester(contentFocusRequester)
                .onSizeChanged { size ->
                    contentHeightPx = size.height
                }
        ) {
            val isMyList = state.selectedCode == MY_LIST_CODE
            val isSearchPage = state.selectedCode == state.searchPageCode
            val pageState = state.pages[state.selectedCode]
            when {
                isMyList -> {
                    LaunchedEffect(Unit) {
                        myListViewModel.reload()
                        try { myListFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                    MyListContent(
                        viewModel = myListViewModel,
                        title = if (state.language == "en") stringResource(R.string.nav_bookmarks) else state.myListTitle.titleCaseForLanguage(state.language),
                        firstRowFocusRequester = myListFocusRequester,
                        onEpisodeClick = onEpisodeClick,
                        onShowClick = onShowClick
                    )
                }
                pageState == null || pageState.isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.loading), fontSize = 20.sp)
                    }
                pageState.error != null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.error_prefix, pageState.error ?: ""), fontSize = 18.sp)
                    }
                isSearchPage -> {
                    LaunchedEffect(state.selectedCode) { searchBarFocusRequester.requestFocus() }
                    SearchContent(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.onSearchQuery(it) },
                        isSearching = state.isSearching,
                        searchResults = state.searchResults,
                        searchError = state.searchError,
                        pageSections = pageState.sections,
                        language = state.language,
                        searchBarFocusRequester = searchBarFocusRequester,
                        onEpisodeClick = onEpisodeClick,
                        onPageClick = onPageClick,
                        onSeasonClick = onSeasonClick,
                        onShowClick = onShowClick,
                        onPersonClick = onPersonClick,
                        onSearchResultClicked = { position, type, id ->
                            viewModel.trackSearchResultClicked(position, type, id, "search")
                        }
                    )
                }
                else -> {
                    LaunchedEffect(state.selectedCode) { contentFocusRequester.requestFocus() }
                    val heroHeight = with(density) { (contentHeightPx * 0.8f).toDp() }
                    PageContent(
                        sections = pageState.sections,
                        heroHeight = heroHeight,
                        preferGrid = state.selectedCode != "frontpage",
                        pageTitle = pageState.pageTitle,
                        language = state.language,
                        sectionFocusRequesters = sectionFocusRequesters,
                        onSectionFocused = { index -> lastFocusedSectionIndex = index },
                        onHeroFocusChanged = { _, url -> heroBackground = url },
                        onEpisodeClick = onEpisodeClick,
                        onPageClick = onPageClick,
                        onSeasonClick = onSeasonClick,
                        onShowClick = onShowClick,
                        onPersonClick = onPersonClick,
                        onSectionItemClicked = { sectionIndex, sectionId, sectionName, sectionType, elementIndex, elementType, elementId, elementName ->
                            viewModel.trackSectionClicked(sectionId, sectionName, sectionIndex, sectionType, elementIndex, elementType, elementId, elementName)
                        }
                    )
                }
            }
        }

        // Scrim — fades in over content when nav is expanded
        if (scrimAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
        }

        // Nav — overlay on the left, drawn on top of content and scrim
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(navWidth)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .padding(vertical = 16.dp)
                .focusRequester(navFocusRequester)
                .onFocusChanged { fs -> navExpanded = fs.hasFocus }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight)
                        return@onKeyEvent false
                    when {
                        state.selectedCode == state.searchPageCode ->
                            searchBarFocusRequester.requestFocus()
                        state.selectedCode == MY_LIST_CODE ->
                            try { myListFocusRequester.requestFocus() } catch (_: Exception) {}
                        else -> {
                            val idx = lastFocusedSectionIndex ?: 0
                            try {
                                sectionFocusRequesters.getOrNull(idx)?.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }
                    true
                }
        ) {
            NavLogo(expanded = itemsExpanded, activeInitials = activeInitials, onClick = onProfileClick)
            Spacer(modifier = Modifier.height(8.dp))
            state.navItems.forEachIndexed { index, item ->
                val isSelected = item.code == state.selectedCode
                val navTitle = when (item.icon) {
                    NavIcon.HOME -> stringResource(R.string.nav_home)
                    NavIcon.SEARCH -> stringResource(R.string.nav_search)
                    NavIcon.BOOKMARK -> stringResource(R.string.nav_bookmarks)
                    else -> item.title.titleCaseForLanguage(state.language)
                }
                NavRailItem(
                    title = navTitle,
                    icon = item.icon,
                    selected = isSelected,
                    expanded = itemsExpanded,
                    focusable = navExpanded || isSelected,
                    focusRequester = navItemFocusRequesters.getOrNull(index),
                    onClick = { viewModel.selectPage(item.code) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            NavRailItem(
                title = stringResource(R.string.nav_settings),
                icon = NavIcon.SETTINGS,
                selected = false,
                expanded = itemsExpanded,
                focusable = navExpanded,
                onClick = onSettingsClick
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MyListContent(
    viewModel: MyListViewModel,
    title: String,
    firstRowFocusRequester: FocusRequester,
    onEpisodeClick: (String) -> Unit,
    onShowClick: (String) -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val showBookmarks by viewModel.showBookmarks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val hasContent = entries.isNotEmpty() || showBookmarks.isNotEmpty()

    // Retry focus once entries arrive
    LaunchedEffect(hasContent) {
        if (hasContent) {
            try { firstRowFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    when {
        isLoading && !hasContent ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.loading), fontSize = 20.sp)
            }
        error != null && !hasContent ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_prefix, error ?: ""), fontSize = 18.sp)
            }
        !hasContent ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.your_list_is_empty), fontSize = 20.sp)
            }
        else -> TvLazyColumn(
            contentPadding = PaddingValues(vertical = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = androidx.compose.ui.Modifier.padding(start = 48.dp, bottom = 16.dp, top = 8.dp)
                )
            }
            item {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    modifier = androidx.compose.ui.Modifier.focusRequester(firstRowFocusRequester)
                ) {
                    items(showBookmarks) { show ->
                        ContentCard(
                            title = show.title,
                            imageUrl = show.image,
                            style = CardStyle.LANDSCAPE,
                            onClick = { onShowClick(show.id) },
                            modifier = androidx.compose.ui.Modifier.padding(end = 16.dp)
                        )
                    }
                    items(entries) { entry ->
                        val ep = entry.item?.onEpisode ?: return@items
                        val dur = ep.duration?.toInt() ?: 0
                        val prog = ep.progress?.toInt() ?: 0
                        val effectivelyWatched = (ep.watched ?: false) &&
                            (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                        val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                            else (prog.toFloat() / dur).coerceIn(0f, 1f)
                        val timeRemaining = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                            else stringResource(R.string.time_remaining, ((dur - prog) / 60).coerceAtLeast(1))
                        ContentCard(
                            title = ep.title,
                            imageUrl = ep.image,
                            watched = effectivelyWatched,
                            progressFraction = progressFraction,
                            durationSeconds = dur,
                            subtitle = null,
                            style = CardStyle.LANDSCAPE,
                            onClick = { onEpisodeClick(ep.id) },
                            modifier = androidx.compose.ui.Modifier.padding(end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavLogo(expanded: Boolean, activeInitials: String?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small
            )
            .background(Color.Transparent, shape = MaterialTheme.shapes.small)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (expanded) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.bcc_logo),
                    contentDescription = "BCC Media",
                    modifier = Modifier.weight(1f).aspectRatio(129f / 20f)
                )
                if (activeInitials != null) {
                    Spacer(Modifier.width(6.dp))
                    ProfileInitialsBadge(initials = activeInitials, sizeDp = 28)
                }
            }
        } else {
            if (activeInitials != null) {
                ProfileInitialsBadge(initials = activeInitials, sizeDp = 32)
            } else {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.bcc_icon),
                    contentDescription = "BCC Media",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileInitialsBadge(initials: String, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            fontSize = (sizeDp * 0.38f).sp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavRailItem(
    title: String,
    icon: NavIcon,
    selected: Boolean,
    expanded: Boolean,
    focusable: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val background = when {
        selected && focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        focused  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        else     -> Color.Transparent
    }
    val tint = when {
        selected && focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        focused  -> MaterialTheme.colorScheme.onSurface
        else     -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 1.dp)
        .background(background, shape = MaterialTheme.shapes.small)
        .focusProperties { canFocus = focusable }
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
        .padding(vertical = 6.dp)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed-width icon zone = collapsed column width minus outer padding (68 - 16 = 52dp).
        // Icon is always centred here, so it never moves when the menu expands/collapses.
        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.Center
        ) {
            icon.vector()?.let {
                Icon(
                    imageVector = it,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroSection(
    cards: List<CardData>,
    heroHeight: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester?,
    nextRowFocusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onScrollToTop: () -> Unit,
    onHeroFocusChanged: (Boolean, String?) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onPageClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit,
    onShowClick: (String) -> Unit,
) {
    if (cards.isEmpty()) return

    var heroIndex by remember { mutableStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val lastInteractionMs = remember { longArrayOf(0L) }
    // The button is the sole focus target; use the passed-in requester so nav can focus it directly
    val buttonFocusRequester = focusRequester ?: remember { FocusRequester() }

    // Notify parent of focus state + current image URL (drives the full-screen background)
    LaunchedEffect(hasFocus, heroIndex) {
        onHeroFocusChanged(hasFocus, if (hasFocus) cards.getOrNull(heroIndex)?.imageUrl else null)
    }

    // When hero regains focus, scroll the outer list back to top so the full hero is visible
    LaunchedEffect(hasFocus) {
        if (hasFocus) onScrollToTop()
    }

    // Auto-advance while the button has focus
    LaunchedEffect(hasFocus) {
        if (!hasFocus) return@LaunchedEffect
        lastInteractionMs[0] = System.currentTimeMillis()
        while (true) {
            delay(5000)
            val now = System.currentTimeMillis()
            if (now - lastInteractionMs[0] >= 4900L) {
                heroIndex = (heroIndex + 1) % cards.size
            }
            lastInteractionMs[0] = now
        }
    }

    // Re-focus the button when heroIndex changes (auto-advance or L/R nav)
    LaunchedEffect(heroIndex) {
        if (hasFocus) runCatching { buttonFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        val card = cards[heroIndex]

        // Horizontal scrim — darkens the left side so text is readable over any background image.
        // The global vertical gradient (in HomeScreen) handles the bottom; this handles left-to-right.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.55f to Color.Transparent
                    )
                )
        )

        // Text and button overlay — bottom left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 36.dp, end = 280.dp)
        ) {
            val durationLabel = card.durationSeconds?.let { secs ->
                val h = secs / 3600; val m = (secs % 3600) / 60
                if (h > 0) "${h}h ${m}m" else "${m} min"
            }
            val metaLine = listOfNotNull(
                card.showTitle?.takeIf { it.isNotBlank() },
                card.subtitle?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            card.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = desc.stripHtml(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
            // The focusable Play/Open button — left/right navigates between hero cards
            Button(
                onClick = {
                    card.episodeId?.let(onEpisodeClick)
                        ?: card.pageCode?.let(onPageClick)
                        ?: card.seasonId?.let(onSeasonClick)
                        ?: card.showId?.let(onShowClick)
                },
                modifier = Modifier
                    .focusRequester(buttonFocusRequester)
                    .onFocusChanged { fs ->
                        hasFocus = fs.isFocused
                        if (fs.isFocused) onFocused()
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (heroIndex > 0) {
                                    heroIndex--; lastInteractionMs[0] = System.currentTimeMillis(); true
                                } else false
                            }
                            Key.DirectionRight -> {
                                if (heroIndex < cards.size - 1) {
                                    heroIndex++; lastInteractionMs[0] = System.currentTimeMillis(); true
                                } else false
                            }
                            Key.DirectionDown -> nextRowFocusRequester?.let {
                                runCatching { it.requestFocus() }.isSuccess
                            } ?: false
                            else -> false
                        }
                    }
            ) {
                if (card.episodeId != null) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(if (card.episodeId != null) R.string.play else R.string.open))
            }
            if (durationLabel != null) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            } // end Row
        }

        // Dot indicators — bottom right
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cards.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == heroIndex) 8.dp else 5.dp)
                        .background(
                            color = if (i == heroIndex) Color.White else Color.White.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PageContent(
    sections: List<GetPageQuery.Item>,
    heroHeight: androidx.compose.ui.unit.Dp = 0.dp,
    preferGrid: Boolean = false,
    pageTitle: String? = null,
    language: String = "en",
    sectionFocusRequesters: List<FocusRequester> = emptyList(),
    onSectionFocused: (Int) -> Unit = {},
    onHeroFocusChanged: (Boolean, String?) -> Unit = { _, _ -> },
    onEpisodeClick: (String) -> Unit,
    onPageClick: (String) -> Unit = {},
    onSeasonClick: (String) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onSectionItemClicked: ((sectionIndex: Int, sectionId: String, sectionName: String, sectionType: String, elementIndex: Int, elementType: String, elementId: String, elementName: String) -> Unit)? = null
) {
    // If the first section is a FeaturedSection, render it as a full-bleed hero panel
    val heroCards = sections.getOrNull(0)?.onFeaturedSection?.items?.items?.mapNotNull { si ->
        val c = si.item ?: return@mapNotNull null
        CardData(
            title = si.title.stripHtml(),
            imageUrl = si.image,
            episodeId = c.onEpisode?.id,
            pageCode = c.onPage?.code,
            seasonId = c.onSeason?.id?.takeIf { c.onSeason?.show?.id == null },
            showId = c.onShow?.id ?: c.onSeason?.show?.id,
            showTitle = c.onEpisode?.season?.show?.title,
            subtitle = c.onEpisode?.season?.title,
            description = si.description?.takeIf { it.isNotBlank() }
                ?: c.onShow?.description,
            durationSeconds = c.onEpisode?.duration
        )
    }.orEmpty()
    // Only render as full-bleed hero when a measured heroHeight is supplied (from HomeScreen).
    // When called from PageScreen (heroHeight=0.dp), the FeaturedSection falls through to
    // SectionRow rendering as a large first-row with scaled cards and descriptions.
    val hasHero = heroCards.isNotEmpty() && heroHeight > 0.dp
    val sectionStart = if (hasHero) 1 else 0

    val firstRenderedIndex = sections.indexOfFirst { s ->
        s.onFeaturedSection == null
    }.takeIf { it >= sectionStart } ?: sectionStart

    val listState = rememberTvLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Show page title as a header if preferGrid and no PageDetailsSection provides one
    val hasPageDetailsSection = sections.any { it.__typename == "PageDetailsSection" }
    val showFallbackTitle = preferGrid && !hasPageDetailsSection && !pageTitle.isNullOrBlank()

    TvLazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (showFallbackTitle) {
            item {
                Text(
                    text = pageTitle!!.stripHtml(),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 48.dp, top = 32.dp, bottom = 24.dp, end = 200.dp)
                )
            }
        }
        if (hasHero) {
            item {
                HeroSection(
                    cards = heroCards,
                    heroHeight = heroHeight,
                    focusRequester = sectionFocusRequesters.getOrNull(0),
                    nextRowFocusRequester = sectionFocusRequesters.getOrNull(firstRenderedIndex),
                    onFocused = { onSectionFocused(0) },
                    onScrollToTop = { coroutineScope.launch { listState.scrollToItem(0) } },
                    onHeroFocusChanged = onHeroFocusChanged,
                    onEpisodeClick = onEpisodeClick,
                    onPageClick = onPageClick,
                    onSeasonClick = onSeasonClick,
                    onShowClick = onShowClick,
                )
            }
        }
        items(sections.size - sectionStart) { relIndex ->
            val index = relIndex + sectionStart
            val sec = sections[index]
            SectionRow(
                section = sec,
                language = language,
                cardScale = if (sec.onFeaturedSection != null || (index == 0 && !hasHero)) 1.5f else 1f,
                showDescription = sec.onFeaturedSection != null || (index == 0 && !hasHero),
                preferGrid = preferGrid,
                firstCardFocusRequester = sectionFocusRequesters.getOrNull(index),
                onFocused = { onSectionFocused(index) },
                onEpisodeClick = onEpisodeClick,
                onPageClick = onPageClick,
                onSeasonClick = onSeasonClick,
                onShowClick = onShowClick,
                onPersonClick = onPersonClick,
                onSectionItemClicked = onSectionItemClicked?.let { cb ->
                    { elementIndex, elementType, elementId, elementName ->
                        cb(index, sec.id, sec.title ?: "", sec.__typename, elementIndex, elementType, elementId, elementName)
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SectionRow(
    section: GetPageQuery.Item,
    language: String = "en",
    cardScale: Float = 1f,
    preferGrid: Boolean = false,
    firstCardFocusRequester: FocusRequester? = null,
    showDescription: Boolean = false,
    onFocused: () -> Unit = {},
    onEpisodeClick: (String) -> Unit,
    onPageClick: (String) -> Unit = {},
    onSeasonClick: (String) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onSectionItemClicked: ((elementIndex: Int, elementType: String, elementId: String, elementName: String) -> Unit)? = null
) {
    val context = LocalContext.current

    fun trackCardClick(card: CardData, index: Int) {
        val (type, id) = when {
            card.episodeId != null -> "episode" to card.episodeId
            card.pageCode != null -> "page" to card.pageCode
            card.seasonId != null -> "season" to card.seasonId
            card.showId != null -> "show" to card.showId
            else -> return
        }
        onSectionItemClicked?.invoke(index, type, id, card.title)
    }

    // PageDetailsSection carries the page title and description.
    if (section.__typename == "PageDetailsSection") {
        val title = section.title?.takeIf { it.isNotBlank() }
        val desc = section.description?.takeIf { it.isNotBlank() }
        if (title == null && desc == null) return
        Column(modifier = Modifier.padding(start = 48.dp, end = 200.dp, bottom = 40.dp, top = 32.dp)) {
            if (title != null) {
                Text(text = title.stripHtml(), style = MaterialTheme.typography.headlineLarge)
            }
            if (desc != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = desc.stripHtml(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    // MessageSection — system/admin messages
    if (section.onMessageSection != null) {
        val messages = section.onMessageSection!!.messages ?: return
        if (messages.isEmpty()) return
        Column(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .onFocusChanged { if (it.hasFocus) onFocused() },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!section.title.isNullOrBlank()) {
                Text(
                    text = section.title!!.stripHtml().titleCaseForLanguage(language),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            messages.forEach { message ->
                if (message.content.isBlank()) return@forEach
                val bgColor = parseHexColor(message.style.background)
                val textColor = parseHexColor(message.style.text)
                val borderColor = parseHexColor(message.style.border)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        return
    }

    // AvatarSection (contributors/persons) — rendered separately as circular avatar cards
    if (section.onAvatarSection != null) {
        val avatarItems = section.onAvatarSection!!.items.items
        if (avatarItems.isEmpty()) return
        Column(modifier = Modifier
            .padding(bottom = 24.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }
        ) {
            if (!section.title.isNullOrBlank()) {
                Text(
                    text = section.title!!.stripHtml().titleCaseForLanguage(language),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
                )
            }
            TvLazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
                items(avatarItems.size) { index ->
                    val si = avatarItems[index]
                    AvatarCard(
                        name = si.title,
                        imageUrl = si.image,
                        focusRequester = if (index == 0) firstCardFocusRequester else null,
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = {
                            val personId = si.item?.onPerson?.id
                            if (personId != null) onPersonClick(personId)
                        }
                    )
                }
            }
        }
        return
    }

    val title = section.title
    val cardStyle = when {
        section.onPosterSection != null || section.onPosterGridSection != null -> CardStyle.POSTER
        section.onIconSection != null || section.onIconGridSection != null -> CardStyle.SQUARE
        else -> CardStyle.LANDSCAPE
    }

    val cards = buildList<CardData> {
        when {
            section.onItemSection != null ->
                section.onItemSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onFeaturedSection != null ->
                section.onFeaturedSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val timeRemaining = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else { val mins = ((dur - prog) / 60).coerceAtLeast(1); context.getString(R.string.time_remaining, mins) }
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onDefaultSection != null ->
                section.onDefaultSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onPosterSection != null ->
                section.onPosterSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onCardSection != null ->
                section.onCardSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onCardListSection != null ->
                section.onCardListSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onListSection != null ->
                section.onListSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onIconSection != null ->
                section.onIconSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val timeRemaining = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else { val mins = ((dur - prog) / 60).coerceAtLeast(1); context.getString(R.string.time_remaining, mins) }
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onLabelSection != null ->
                section.onLabelSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onDefaultGridSection != null ->
                section.onDefaultGridSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onPosterGridSection != null ->
                section.onPosterGridSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val timeRemaining = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else { val mins = ((dur - prog) / 60).coerceAtLeast(1); context.getString(R.string.time_remaining, mins) }
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
            section.onIconGridSection != null ->
                section.onIconGridSection!!.items.items.forEach { si ->
                    val c = si.item ?: return@forEach
                    val episodeId = c.onEpisode?.id; val pageCode = c.onPage?.code; val seasonId = c.onSeason?.id; val showId = c.onShow?.id
                    val showTitle = c.onEpisode?.season?.show?.title
                    val dur = c.onEpisode?.duration?.toInt() ?: 0
                    val prog = c.onEpisode?.progress?.toInt() ?: 0
                    val effectivelyWatched = (c.onEpisode?.watched ?: false) &&
                        (dur <= 0 || prog <= 0 || prog.toFloat() / dur >= 0.95f)
                    val progressFraction = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else (prog.toFloat() / dur).coerceIn(0f, 1f)
                    val timeRemaining = if (effectivelyWatched || dur <= 0 || prog <= 0) null
                        else { val mins = ((dur - prog) / 60).coerceAtLeast(1); context.getString(R.string.time_remaining, mins) }
                    val description = if (showDescription) si.description else null
                    add(CardData(si.title.stripHtml(), si.image, episodeId, pageCode, seasonId, showId, showTitle, progressFraction, description, null, effectivelyWatched, dur))
                }
        }
    }

    if (cards.isEmpty()) return

    val isGridSection = section.onDefaultGridSection != null
        || section.onPosterGridSection != null
        || (preferGrid && section.onPosterSection != null)

    // Grid sections (e.g. "Previous Bible Study Projects") wrap cards into multiple rows
    // instead of a single horizontally-scrolling row.
    if (isGridSection) {
        Column(modifier = Modifier
            .padding(bottom = 24.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title.stripHtml().titleCaseForLanguage(language),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
                )
            }
            FlowRow(
                modifier = Modifier.padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                cards.forEachIndexed { index, card ->
                    ContentCard(
                        title = card.title,
                        imageUrl = card.imageUrl,
                        subtitle = card.subtitle,
                        showTitle = card.showTitle,
                        watched = card.watched,
                        progressFraction = card.progressFraction,
                        description = card.description,
                        durationSeconds = card.durationSeconds,
                        style = cardStyle,
                        scale = cardScale,
                        focusRequester = if (index == 0) firstCardFocusRequester else null,
                        onClick = {
                            trackCardClick(card, index)
                            card.episodeId?.let(onEpisodeClick)
                                ?: card.pageCode?.let(onPageClick)
                                ?: card.seasonId?.let(onSeasonClick)
                                ?: card.showId?.let(onShowClick)
                        },
                        modifier = Modifier
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier
        .padding(bottom = 24.dp)
        .onFocusChanged { if (it.hasFocus) onFocused() }
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title.stripHtml().titleCaseForLanguage(language),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
            )
        }
        TvLazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
            items(cards.size) { index ->
                val card = cards[index]
                val isPageLink = card.pageCode != null && card.imageUrl == null
                if (isPageLink) {
                    PageLinkCard(
                        title = card.title,
                        focusRequester = if (index == 0) firstCardFocusRequester else null,
                        onClick = { card.pageCode?.let(onPageClick) },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                } else {
                    ContentCard(
                        title = card.title,
                        imageUrl = card.imageUrl,
                        subtitle = card.subtitle,
                        showTitle = card.showTitle,
                        watched = card.watched,
                        progressFraction = card.progressFraction,
                        description = card.description,
                        durationSeconds = card.durationSeconds,
                        style = cardStyle,
                        scale = cardScale,
                        focusRequester = if (index == 0) firstCardFocusRequester else null,
                        onClick = {
                            trackCardClick(card, index)
                            card.episodeId?.let(onEpisodeClick)
                                ?: card.pageCode?.let(onPageClick)
                                ?: card.seasonId?.let(onSeasonClick)
                                ?: card.showId?.let(onShowClick)
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<SearchQuery.Result>,
    searchError: String?,
    pageSections: List<GetPageQuery.Item>,
    language: String = "en",
    searchBarFocusRequester: FocusRequester,
    onEpisodeClick: (String) -> Unit,
    onPageClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit,
    onShowClick: (String) -> Unit,
    onPersonClick: (String) -> Unit = {},
    onSearchResultClicked: ((position: Int, type: String, id: String) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = onQueryChange, focusRequester = searchBarFocusRequester)
        when {
            query.isBlank() ->
                PageContent(sections = pageSections, language = language, onEpisodeClick = onEpisodeClick, onPageClick = onPageClick, onSeasonClick = onSeasonClick, onShowClick = onShowClick, onPersonClick = onPersonClick)
            isSearching ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.searching), fontSize = 20.sp)
                }
            searchError != null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_error, searchError), fontSize = 18.sp)
                }
            searchResults.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results, query), fontSize = 18.sp)
                }
            else -> SearchResults(
                results = searchResults,
                onEpisodeClick = onEpisodeClick,
                onSeasonClick = onSeasonClick,
                onShowClick = onShowClick,
                onSearchResultClicked = onSearchResultClicked
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    var hasFocus by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val textFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Enter edit mode: runs after recomposition so BasicTextField's canFocus is already true.
    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFocusRequester.requestFocus()
            kotlinx.coroutines.delay(50)
            keyboardController?.show()
        }
    }
    // Hide keyboard whenever focus is on the Row (nav mode).
    LaunchedEffect(hasFocus, isEditing) {
        if (hasFocus && !isEditing) {
            kotlinx.coroutines.delay(50)
            keyboardController?.hide()
        }
    }

    val active = hasFocus || isEditing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 20.dp)
            .border(
                BorderStroke(
                    width = if (active) 2.dp else 0.dp,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
                shape = MaterialTheme.shapes.small
            )
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // The Row is the spatial D-pad target in nav mode. Its full width (matching the
            // content padding of the card rows) ensures D-pad down lands on the first card,
            // not the second. canFocus never toggles on this Row, so there is no focus gap
            // that would cause the nav to flicker.
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.hasFocus }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    !isEditing && (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        isEditing = true
                        true
                    }
                    // D-pad down in nav mode: skip the spatial algorithm (which centers on the
                    // full-width Row and lands on card 2+) and move by composition order instead,
                    // which lands on card 1.
                    !isEditing && event.key == Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Next)
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { isEditing = false; focusRequester.requestFocus() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                inner()
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(textFocusRequester)
                // Only focusable in edit mode — prevents the text field from appearing as a
                // D-pad target during spatial navigation, which would skew the overlap
                // calculation away from the first content card.
                .focusProperties { canFocus = isEditing }
                .onFocusChanged { fs ->
                    if (!fs.isFocused && isEditing) isEditing = false
                }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResults(
    results: List<SearchQuery.Result>,
    onEpisodeClick: (String) -> Unit,
    onSeasonClick: (String) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onSearchResultClicked: ((position: Int, type: String, id: String) -> Unit)? = null
) {
    TvLazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = if (results.size == 1) stringResource(R.string.results_count, results.size) else stringResource(R.string.results_count_plural, results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 48.dp, bottom = 12.dp)
            )
        }
        item {
            TvLazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
                items(results.size) { index ->
                    val result = results[index]
                    val subtitle = result.onEpisodeSearchItem?.showTitle
                        ?: result.onSeasonSearchItem?.seasonShowTitle
                    val elementType = when {
                        result.onEpisodeSearchItem != null -> "episode"
                        result.onSeasonSearchItem != null -> "season"
                        else -> "show"
                    }
                    ContentCard(
                        title = result.title.stripHtml(),
                        imageUrl = result.image,
                        subtitle = subtitle,
                        onClick = {
                            onSearchResultClicked?.invoke(index, elementType, result.id)
                            when {
                                result.onEpisodeSearchItem != null -> onEpisodeClick(result.id)
                                result.onSeasonSearchItem != null -> onSeasonClick(result.id)
                                else -> onShowClick(result.id)
                            }
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
    }
}
