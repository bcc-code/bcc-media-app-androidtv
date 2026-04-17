# BCC Media Google TV App

A native Google TV app for [BCC Media](https://app.bcc.media) (brunstad.tv), built from scratch in Kotlin and Jetpack Compose for TV. The app brings the full BCC Media content library to the 10-foot TV experience with complete D-pad navigation, multi-language audio, and deep Google TV integration.

Built by a church member for personal use, using the public BCC Media GraphQL API.

---

## Platform & Stack

| | |
|---|---|
| **Platform** | Google TV / Android TV OS (min SDK 21) |
| **Language** | Kotlin |
| **UI** | Jetpack Compose for TV (`androidx.tv`) |
| **Auth** | Auth0 Android SDK — Device Authorization Flow |
| **GraphQL** | Apollo Android |
| **Playback** | Media3 ExoPlayer |
| **Images** | Coil |
| **DI** | Hilt |

---

## Features

### Authentication
- **Auth0 Device Authorization Flow** — QR code displayed on screen; user scans with phone to log in. Designed for TV where typing a password is impractical.
- Tokens stored in `EncryptedSharedPreferences` with automatic silent refresh via the Auth0 SDK.
- **Auto-detect language on first login** — reads the user's locale from the Auth0 JWT and pre-selects the matching app language.

### Home Screen & Navigation
- **Server-driven navigation** — nav rail items are populated from the GraphQL `GetApplication` query and rendered dynamically. New top-level pages added on the server appear in the app automatically.
- **All section types rendered:** `ItemSection`, `FeaturedSection`, `DefaultSection`, `PosterSection`, `CardSection`, `CardListSection`, `ListSection`, `IconSection`, `LabelSection`, `AvatarSection`, `DefaultGridSection`, `PosterGridSection`, `IconGridSection`.
- **Collapsible nav rail** — icons-only when collapsed, icon + label when expanded. Selected and focused states are visually distinct.
- **Crossfading hero background** — full-bleed backdrop that fades between items as D-pad focus moves across the featured row.
- Category pages (Studies, Children, Short Films, etc.) with grid layouts.
- Sub-page navigation for `Page`-type section items.

### Search
- Full-text search with debounced queries.
- Results show episodes, seasons, and shows with thumbnails and metadata.
- D-pad focus transitions naturally from the search bar to results.

### Content Detail Screens
- **Episode detail** — full-bleed background, title, description, age rating, duration, publish date, show/season breadcrumb, chapter list.
- **Show detail** — header image, description, all seasons as scrollable episode card rows.
- **Season detail** — header, show link, episode cards.
- **Contributor (Person) detail** — circular avatar, episode contributions filterable by content type, random episode play button.

### Video Playback
- **Media3 ExoPlayer** streaming HLS CMAF directly from pre-signed CloudFront URLs — no DRM, no token exchange.
- **18 audio language tracks** — selectable at runtime (bg, de, en, es, fi, fr, hr, hu, it, nl, no, pl, pt, ro, ru, sl, ta, tr).
- **Subtitles** — on/off toggle with language preference, persisted across sessions.
- Audio and subtitle preferences stored in Settings and applied automatically on playback start.

### Chapters
- Chapter list on episode detail — selecting a chapter opens the player and seeks to that position.
- Current chapter name displayed live in the player controls, updating in real time as playback progresses.

### Progress & Continue Watching
- **Watch progress saved to API** — `SetEpisodeProgress` mutation called every 10 seconds and on player exit.
- **Resume from position** — "Continue from X:XX" on episode detail; player seeks to saved position.
- **Watched badge** — episodes marked complete by the API show a green ✓ on their card.
- **Auto-play next episode** — configurable countdown (off / 5s / 10s / 15s / 30s) after an episode ends.
- **Google TV Continue Watching row** — writes to `WatchNextPrograms` so in-progress episodes appear on the Google TV home screen. Deep link from the row opens the episode directly.

### Watchlist (My List)
- Bookmark button on episode and show detail screens.
- Synced to the server via `addEpisodeToMyList` / `addShowToMyList` / `removeEntryFromMyList`.
- My List appears as a top-level nav item.
- Optimistic UI — button state updates instantly while the API call runs in the background.

### Multi-Profile (Household Accounts)
- **Multiple accounts on one device** — each family member can log in with their own Auth0 account and switch between them without re-authenticating.
- Profile switcher accessible from the profile icon in the top-left of the nav rail, and from Settings → Switch Account.
- Per-profile settings — app language, audio language, subtitle preference, and watchlist are all stored independently per profile.
- **Add Account** option in the profile switcher launches the Device Authorization Flow for a new login without disturbing the current session.
- Active profile indicated with a checkmark; focus ring highlights the selected badge on D-pad navigation.

### Localization
- UI fully localized in **18 languages**: bg, de, en, es, fi, fr, hr, hu, it, nl, no, pl, pt, ro, ru, sl, ta, tr.
- Language selector in Settings; change takes effect immediately without an app restart.
- Content language (`Accept-Language` header) and UI language are configured independently.

### Google TV Integration
- **Continue Watching row** on the Google TV home screen for in-progress episodes.
- **Preview Channel** — a branded channel row on the Google TV home screen populated with featured content.
- **Splash screen** with BCC Media jingle on cold start and after login.
- App icon, round icon, and TV banner (320×180) with BCC Media branding.

---

## Building a Release APK

The release signing config is read from `local.properties` (gitignored — never committed). Before building a release APK, add your keystore details to that file:

```properties
signing.storeFile=/path/to/your.jks
signing.storePassword=your-store-password
signing.keyAlias=your-key-alias
signing.keyPassword=your-key-password
```

Generate a new keystore with:
```bash
keytool -genkeypair -v -keystore your.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-key-alias
```

> **Note for Play Store submission:** Google Play permanently associates an app with the key used to sign the first upload. Generate a new keystore specific to your developer account before the first upload — do not reuse a key from another app.

---

## Known Limitations

- **Continue Watching row** may not surface for sideloaded apps — this appears to be a Google TV restriction for apps not installed via the Play Store.
- **Show bookmarks** fetched from `GetMyList` return blank fields for Show entries via the API. As a workaround, show title and image are stored locally at bookmark time, so show bookmarks are lost on reinstall and won't reflect bookmarks made on other clients.
