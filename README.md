# Finora — Personal Finance & Market Tracker

Finora is a local-first Android personal finance application combining Copilot Money's budgeting and net-worth engine with Google Finance's watchlist/portfolio module, enriched with camera receipt capture and location-tagged spending.

---

## Getting Started & Configuration

### Prerequisites
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: Java 17
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Target/Compile SDK**: API 35

### Setting Up Market Data API Key
Finora integrates with the **Finnhub API** for real-time stock quotes and symbol searching.

> [!IMPORTANT]
> The Finnhub API key is **never committed into version control**.
>
> 1. Sign up for a free API key at [finnhub.io](https://finnhub.io).
> 2. Open `local.properties` in your project root directory.
> 3. Add your key as follows:
>    ```properties
>    FINNHUB_API_KEY=your_actual_finnhub_api_key
>    ```
> 4. Gradle automatically injects this key into `BuildConfig.FINNHUB_API_KEY` at build time.

---

## Phase 0 Checklist (Setup & Foundation)

- [x] Project configured with Kotlin, ViewBinding, Min SDK 26, Target SDK 35.
- [x] Dependencies configured:
  - Room (runtime, ktx, compiler via kapt)
  - Retrofit 2 + Gson Converter + OkHttp Logging Interceptor
  - Kotlin Coroutines (`kotlinx-coroutines-core`, `kotlinx-coroutines-android`)
  - AndroidX Lifecycle (`viewmodel-ktx`, `livedata-ktx`, `runtime-ktx`)
  - Google Play Services Location (`FusedLocationProviderClient`)
  - MPAndroidChart (charts)
  - Material Components for Android
  - AndroidX Fragment-ktx, RecyclerView, CardView, ConstraintLayout
- [x] All 5 required permissions declared in `AndroidManifest.xml`:
  - `android.permission.INTERNET`
  - `android.permission.CAMERA`
  - `android.permission.ACCESS_FINE_LOCATION`
  - `android.permission.ACCESS_COARSE_LOCATION`
  - `android.permission.ACCESS_NETWORK_STATE`
- [x] `androidx.core.content.FileProvider` configured in `AndroidManifest.xml` with `res/xml/file_paths.xml`.
- [x] Package directory architecture established:
  - `data/` (Room entities, DAOs, AppDatabase)
  - `repository/` (Data abstraction layer)
  - `network/` (Finnhub Retrofit client & DTOs)
  - `ui/` (`dashboard`, `transactions`, `accounts`, `budget`, `watchlist`, `settings`)
  - `util/` (Helpers and utilities)
- [x] `FINNHUB_API_KEY` configured safely via `local.properties` (git-ignored) and injected via `BuildConfig`.
