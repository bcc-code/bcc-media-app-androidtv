# BCC Media Google TV App — Claude Code Briefing

## Coding Rules

- **No hardcoded UI strings.** Every user-visible string must use `stringResource(R.string.xxx)` in composables (or `context.getString()` where a Context is available). Add new strings to `values/strings.xml` and all 17 locale files under `values-{lang}/strings.xml`. This includes button labels, screen titles, error messages, dialog text, and fallback labels. ViewModels must not format user-visible strings directly — pass raw data (e.g. season number as `Int`) to the composable and format there.

---

## What Was Built

A native Google TV app for **BCC Media** (app.bcc.media / brunstad.tv): browse and play video content with multi-language audio, multi-profile household support, and deep Google TV integration.

**Platform:** Google TV / Android TV OS (min SDK 21), Kotlin + Jetpack Compose for TV (`androidx.tv`).

---

## Technology Stack

| Concern | Library | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose for TV (`androidx.tv`) | D-pad navigation built in |
| Auth | **Hand-rolled** Device Authorization Flow | OkHttp + OkHttp coroutines; Auth0 SDK not used |
| GraphQL | Apollo Android (`com.apollographql.apollo3`) | Codegen from .graphql files |
| Media playback | Media3 ExoPlayer | HLS CMAF, multi-audio |
| Images | Coil | Async image loading, TV-friendly |
| DI | Hilt | Standard Android DI |
| Async | Kotlin Coroutines + Flow | |

---

## Authentication — Device Authorization Flow (Hand-Rolled)

Auth0 SDK is **not used**. The login flow is hand-rolled with OkHttp:

1. POST to `https://login.bcc.no/oauth/device/code` → get `device_code`, `user_code`, `verification_uri`
2. Display QR code + URL + code on screen
3. Poll `https://login.bcc.no/oauth/token` until approved or expired
4. Store `access_token`, `refresh_token`, `id_token` in `EncryptedSharedPreferences`

```
Auth0 domain:    login.bcc.no
Client ID:       iaDsfutxWw4eoRHHVryW65JHd49kXaP0
Audience:        api.bcc.no
Scope:           openid church profile country offline_access
```

Token refresh is also hand-rolled: POST to `https://login.bcc.no/oauth/token` with `grant_type=refresh_token`. The Auth0 SDK's claim that it handles this automatically does not apply here.

Token keys are **namespaced by userId**: `profile_{userId}_access_token`, `profile_{userId}_refresh_token`, `profile_{userId}_id_token`. One-time migration from legacy un-namespaced keys is handled by `TokenStore.loadLegacy()` / `clearLegacy()`.

---

## Multi-Profile System

Multiple Auth0 accounts can be stored on one device. Key classes:

- **`auth/ProfileStore.kt`** — stores profile list as JSON + active profile ID in plain `SharedPreferences` (prefs name: `bccmedia_profiles`). Uses `.commit()` (not `.apply()`) everywhere — synchronous writes are required so `Activity.recreate()` reads correct state.
- **`auth/TokenStore.kt`** — token storage keyed by userId (see above).
- **`auth/AuthRepository.kt`** — `getValidAccessToken()` uses `profileStore.activeProfileId`. On first login, extracts userId/displayName/initials from JWT and calls `profileStore.saveProfile()`.
- **`data/LanguageRepository.kt`** — all pref keys are profile-namespaced via `key(base)` helper (e.g. `profile_{userId}_language`). Lazy one-time migration from legacy un-keyed settings. Static companion: `profileLanguageKey(userId)` used in `attachBaseContext` before Hilt is available.
- **`ui/profile/ProfilePickerViewModel.kt`** — `UiState(profiles, activeProfileId)`, `refresh()`, `switchTo()`.
- **`ui/profile/ProfilePickerScreen.kt`** — full-screen overlay. Switching calls `viewModel.switchTo(profile)` then `context.findActivity()?.recreate()`.

### Critical: `findActivity()` in Compose Navigation
`LocalContext.current` inside a Compose Navigation composable is a `ContextThemeWrapper`, not an `Activity`. `(context as? Activity)` always returns null. Use:
```kotlin
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
```

### Profile-aware locale in `attachBaseContext`
`attachBaseContext` runs before Hilt is available, so profile language is read via raw SharedPreferences using static helpers:
```kotlin
val activeId = newBase.getSharedPreferences(ProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
    .getString(ProfileStore.KEY_ACTIVE_PROFILE_ID, null)
val langKey = LanguageRepository.profileLanguageKey(activeId)
val lang = newBase.getSharedPreferences("bccmedia_prefs", Context.MODE_PRIVATE)
    .getString(langKey, LanguageRepository.DEFAULT) ?: LanguageRepository.DEFAULT
```

---

## Content API — GraphQL

Single endpoint: `https://api.brunstad.tv/query`

All requests are HTTP POST with `Content-Type: application/json`. Required headers on every request:
```
Authorization:    Bearer <access_token>
X-Application:    bccm-androidtv
X-Session-Id:     <timestamp-ms>      (generate once per app session)
Accept-Language:  <ISO 639-1 code>    (e.g. "en", "no")
```

### Key GraphQL Operations

**App bootstrap:**
```graphql
query application {
  application {
    code
    page { code }
    searchPage { code }
  }
}
```

**Home page sections (paginated):**
```graphql
query getPage($code: String!, $first: Int, $offset: Int,
              $sectionFirst: Int, $sectionOffset: Int) {
  page(code: $code) {
    id title code
    sections(first: $first, offset: $offset) {
      total offset first
      items { __typename id title description ...ItemSection }
    }
  }
}
# Use code: "frontpage" for home screen
```

**Episode detail:**
```graphql
query getEpisode($episodeId: ID!) {
  episode(id: $episodeId) {
    id uuid title description image
    publishDate productionDate duration
    number progress locked ageRating
    season { id title number show { id type title } }
    chapters { id title start }
  }
}
```

**Stream URL — use UUID, not integer ID:**
```graphql
query getEpisode($ID: ID!) {
  episode(id: $ID) {
    image
    streams { url type audioLanguages subtitleLanguages videoLanguage }
  }
}
```

**Season episodes:**
```graphql
query getSeasonOnEpisodePage($seasonId: ID!, $firstEpisodes: Int) {
  season(id: $seasonId) {
    id title image number
    episodes(first: $firstEpisodes) {
      total
      items { id uuid title image publishDate duration number progress description ageRating }
    }
  }
}
```

### Content Data Model

Hierarchy: **Show → Season → Episode**

IDs come in two forms — integer string IDs (e.g. `"3003"`) for most queries, UUIDs (e.g. `"645b1fbf-..."`) required for stream URL queries. The `uuid` field on episode is the one to use for streams.

Section types on the home page: `FeaturedSection`, `DefaultSection`, `GridSection`, `PosterSection`, `CardSection`, `ListSection`, and others. Each contains `SectionItem` entries with an `item` field that can be `Episode`, `Season`, `Show`, `Page`, `Playlist`, or `Link`.

---

## Streaming — HLS CMAF, No DRM

Stream URL: pre-signed AWS CloudFront HLS, valid ~6 hours. No DRM, no token exchange.
CDN: `vod2.stream.brunstad.tv`

Single HLS manifest contains 18 audio tracks (bg, de, en, es, fi, fr, hr, hu, it, nl, no, pl, pt, ro, ru, sl, ta, tr). Use Media3 ExoPlayer with `TrackSelector` for runtime audio selection.

Re-fetch the stream URL if the user resumes after more than ~6 hours.

Images: `imgix.bcc.media` with query params for resizing: `?w=320&h=180&fit=crop&crop=faces`

---

## Known Gotchas & Implementation Notes

### Older Android TV devices (Android 9 / API 28)
- `isSystemInDarkTheme()` returns false → white background. Fix: always force dark theme in `themes.xml` and `Theme.kt`, never conditionally check system theme.
- `PlayerView` defaults to `SurfaceView` which shows white on older devices. Fix: use `app:surface_type="texture_view"` in `res/layout/player_view.xml` and inflate it.

### Compose Navigation ViewModel scoping
Each back-stack entry gets its own `HiltViewModel` instance. Two screens navigating to the same route have separate ViewModels — they don't share state. When returning to `HomeScreen` from `ProfilePickerScreen`, `HomeScreen`'s `profileViewModel` is a different instance than the picker's. Fix: call `profileViewModel.refresh()` inside the `RESUMED` lifecycle effect in `HomeScreen`.

### D-pad focus in Compose
- Do not use `.focusable()` before `.onFocusChanged` + `.clickable()` — it creates inconsistent focus node behavior.
- For a "focus ring" effect, use an outer Box with a primary-colored background that's only visible when focused, wrapping an inner Box with the actual content.
- `NavRailItem` selected+focused states: always check `selected && focused` before `selected` alone, otherwise there's no visual difference when a selected item gains focus.

### SharedPreferences commit() vs apply()
Any write that must be read by `Activity.recreate()` **must** use `.commit()`. The `.apply()` write is async and the new Activity can read stale state. This affects `ProfileStore.activeProfileId` and `persistProfiles()`.

### Open Source Reference
The official BCC Media app is open source at `github.com/bcc-code/bcc-media-app` (Flutter). Use it to understand the content model and GraphQL queries. The GraphQL schema is at `github.com/bcc-code/bcc-media-platform` under `backend/graph/api/schema/`.
