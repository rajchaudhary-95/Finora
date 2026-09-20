# Finora — Personal Finance & Market Portfolio for Android

Finora combines **Copilot Money’s** budgeting and net-worth tracking with **Google Finance’s** real-time market watchlists and portfolio tracking — augmented with **camera-based receipt capture** and **location-tagged expense logging**. Built natively in Kotlin for Android using modern MVVM-lite architecture, Room SQLite, Retrofit, and Material 3 design.

---

## 1. Project Overview

### 1.1 Problem Statement
Two dominant financial apps each capture one half of the personal wealth equation well, yet neither addresses the full picture on Android:
- **Google Finance** excels at market-tracking — watchlists, real-time quotes, and investment portfolio values — but offers zero budgeting, no transaction tracking, and no expense categorization. It is an investing lens, not a spending lens.
- **Copilot Money** excels at personal budgeting and net-worth management — categorized transactions, monthly category budgets, recurring charge detection, and unified cash + investment tracking — but has **no Android application** and relies strictly on third-party bank aggregators with no physical receipt scanning or cash transaction support.

**Finora’s Premise:** Build an offline-first Android application at this exact intersection — delivering Copilot's budgeting, net-worth tracking, and recurring detection alongside Google Finance's market watchlist and holdings tracking, plus physical receipt photo capture and GPS location tagging.

### 1.2 Target User
Students and early-career professionals who manage personal accounts (checking, savings, credit card, cash), want monthly budgets without paid bank aggregators, invest in equities, and need an effortless way to record transactions with physical receipts and geographical context.

### 1.3 Comparative Feature Matrix

| Capability | Google Finance | Copilot Money | **Finora** |
|---|---|---|---|
| Stock Watchlist & Quotes | ✅ | ❌ | ✅ (Live via Finnhub REST API) |
| Portfolio Value Tracking | ✅ | ✅ (Via linked brokerage) | ✅ (Manually entered holdings, live-priced) |
| Bank-Synced Transactions | ❌ | ✅ (Paid aggregator) | ❌ (Manual entry — documented assumption) |
| Automatic Categorization | ❌ | ✅ (ML-based) | ✅ (Transparent keyword-rule engine) |
| Monthly Budgets w/ Rollover | ❌ | ✅ | ✅ (Color-coded progress & rollover) |
| Recurring-Charge Detection | ❌ | ✅ | ✅ (Multi-month pattern detector) |
| Combined Net Worth (Cash + Stocks) | ❌ | ✅ | ✅ (Real-time hand-verifiable formula) |
| Receipt Photo Capture | ❌ | ❌ | ✅ (**Key differentiator** via Camera Intent) |
| Location-Tagged Spending | ❌ | ❌ | ✅ (**Key differentiator** via FusedLocation) |
| Android Native App | ✅ | ❌ | ✅ (API 26+, Material 3, Dark & Light) |

---

## 2. Requirement-to-Feature Mapping Table

This mapping details concrete implementation evidence across the codebase corresponding to the foundational project requirements:

| # | Requirement | Concrete Feature(s) in Finora | Primary Classes & Source Files |
|---|---|---|---|
| **a** | **Layouts** | `ConstraintLayout` for responsive screen layouts; `RecyclerView` + `CardView` for Transactions, Accounts, Budgets, and Watchlist; `BottomNavigationView` with Fragment navigation; `TabLayout` + `ViewPager2` on Stock Detail; Material 3 forms with color-coded `ProgressBar`s. | [`activity_main.xml`](file:///app/src/main/res/layout/activity_main.xml)<br>[`fragment_dashboard.xml`](file:///app/src/main/res/layout/fragment_dashboard.xml)<br>[`item_transaction.xml`](file:///app/src/main/res/layout/item_transaction.xml)<br>[`activity_stock_detail.xml`](file:///app/src/main/res/layout/activity_stock_detail.xml)<br>[`fragment_budget.xml`](file:///app/src/main/res/layout/fragment_budget.xml) |
| **b** | **Intents** | **Implicit Intents:**<br>1. `MediaStore.ACTION_IMAGE_CAPTURE` with `FileProvider` URI for receipt camera capture.<br>2. `Intent.ACTION_SEND` (text/plain) to share net-worth and monthly spending reports to any app via the system share sheet.<br>3. `geo:0,0?q=lat,lng(label)` URI intent to open external mapping apps from transaction details.<br>**Explicit Intents:** Type-safe screen-to-screen navigation passing entity IDs via Intent extras. | [`AddEditTransactionActivity.kt`](file:///app/src/main/java/com/example/finora/ui/transactions/AddEditTransactionActivity.kt)<br>[`DashboardFragment.kt`](file:///app/src/main/java/com/example/finora/ui/dashboard/DashboardFragment.kt)<br>[`TransactionDetailActivity.kt`](file:///app/src/main/java/com/example/finora/ui/transactions/TransactionDetailActivity.kt)<br>[`StockDetailActivity.kt`](file:///app/src/main/java/com/example/finora/ui/watchlist/StockDetailActivity.kt) |
| **c** | **Activity Lifecycle** | 9 distinct Activities handling state restoration, `ActivityResultLauncher` contracts, edge-to-edge system insets, and configuration changes without data loss. | [`SplashActivity.kt`](file:///app/src/main/java/com/example/finora/ui/splash/SplashActivity.kt)<br>[`MainActivity.kt`](file:///app/src/main/java/com/example/finora/ui/main/MainActivity.kt)<br>[`AccountsActivity.kt`](file:///app/src/main/java/com/example/finora/ui/accounts/AccountsActivity.kt)<br>[`AddEditAccountActivity.kt`](file:///app/src/main/java/com/example/finora/ui/accounts/AddEditAccountActivity.kt)<br>[`AddEditTransactionActivity.kt`](file:///app/src/main/java/com/example/finora/ui/transactions/AddEditTransactionActivity.kt)<br>[`TransactionDetailActivity.kt`](file:///app/src/main/java/com/example/finora/ui/transactions/TransactionDetailActivity.kt)<br>[`BudgetSetupActivity.kt`](file:///app/src/main/java/com/example/finora/ui/budget/BudgetSetupActivity.kt)<br>[`WatchlistActivity.kt`](file:///app/src/main/java/com/example/finora/ui/watchlist/WatchlistActivity.kt)<br>[`StockDetailActivity.kt`](file:///app/src/main/java/com/example/finora/ui/watchlist/StockDetailActivity.kt)<br>[`NearbySpendingActivity.kt`](file:///app/src/main/java/com/example/finora/ui/nearby/NearbySpendingActivity.kt)<br>[`SettingsActivity.kt`](file:///app/src/main/java/com/example/finora/ui/settings/SettingsActivity.kt) |
| **d** | **SQLite / Room** | Type-safe Room SQLite implementation: 6 entities (`Account`, `Category`, `Transaction`, `Budget`, `WatchlistStock`, `PortfolioHolding`), 6 DAOs, database migrations, and automatic seeding of default categories on first launch. Atomic `@Transaction` methods for balance integrity. | [`FinoraDatabase.kt`](file:///app/src/main/java/com/example/finora/data/db/FinoraDatabase.kt)<br>[`AccountDao.kt`](file:///app/src/main/java/com/example/finora/data/db/dao/AccountDao.kt)<br>[`TransactionDao.kt`](file:///app/src/main/java/com/example/finora/data/db/dao/TransactionDao.kt)<br>[`BudgetDao.kt`](file:///app/src/main/java/com/example/finora/data/db/dao/BudgetDao.kt)<br>[`WatchlistDao.kt`](file:///app/src/main/java/com/example/finora/data/db/dao/WatchlistDao.kt)<br>[`PortfolioDao.kt`](file:///app/src/main/java/com/example/finora/data/db/dao/PortfolioDao.kt)<br>[`CategoryDao.kt`](file:///app/src/main/java/com/example/finora/data/db/dao/CategoryDao.kt) |
| **e** | **Camera** | Full receipt photo capture flow using system camera intent and secure `FileProvider` (`com.example.finora.fileprovider`). Downsampled thumbnail caching in RecyclerView lists and full-fidelity display in transaction detail. | [`AddEditTransactionActivity.kt`](file:///app/src/main/java/com/example/finora/ui/transactions/AddEditTransactionActivity.kt)<br>[`ImageUtils.kt`](file:///app/src/main/java/com/example/finora/util/ImageUtils.kt)<br>[`file_paths.xml`](file:///app/src/main/res/xml/file_paths.xml) |
| **f** | **Location API** | `FusedLocationProviderClient` fetches GPS coordinates upon user toggle; Android `Geocoder` reverse-geocodes coordinates into human-readable street addresses; "Nearby Spending" computes Haversine distances to order transactions closest to user. | [`AddEditTransactionActivity.kt`](file:///app/src/main/java/com/example/finora/ui/transactions/AddEditTransactionActivity.kt)<br>[`NearbySpendingActivity.kt`](file:///app/src/main/java/com/example/finora/ui/nearby/NearbySpendingActivity.kt)<br>[`HaversineUtil.kt`](file:///app/src/main/java/com/example/finora/util/HaversineUtil.kt) |
| **g** | **Signed APK** | Production release APK signed with V1 (JAR) and V2 (Full APK Signature) schemes, built with `minifyEnabled false`. Keystore and signing config fully integrated into Gradle build scripts. Verified via `apksigner`. | [`app/build.gradle.kts`](file:///app/build.gradle.kts)<br>[`app/finora-release.jks`](file:///app/finora-release.jks)<br>Output: `app/build/outputs/apk/release/app-release.apk` |

---

## 3. Installation & Run Instructions

### 3.1 Prerequisites
- **Android Studio:** Hedgehog (2023.1.1) or newer / Ladybug / Meerkat
- **Android SDK:** Minimum SDK 26 (Android 8.0 Oreo), Target/Compile SDK 34/35
- **JDK:** Java 17 (recommended default bundled with Android Studio)
- **Finnhub API Key:** Free personal API key from [finnhub.io](https://finnhub.io/)

### 3.2 Setting up the API Key (`local.properties`)
To prevent leaking credentials into version control, Finora reads your Finnhub token during Gradle builds:
1. Obtain a free API key by signing up at [https://finnhub.io/register](https://finnhub.io/register).
2. Open or create the `local.properties` file in the project root directory (`C:\Users\rajsc\AndroidStudioProjects\Finora\local.properties`).
3. Add the following property:
   ```properties
   FINNHUB_API_KEY=your_finnhub_api_key_here
   ```
4. Perform a **Gradle Sync**. Gradle will automatically inject this value into `BuildConfig.FINNHUB_API_KEY` for both Debug and Release build variants.

### 3.3 Building & Sideloading the Signed Release APK
To assemble the signed release APK directly from terminal or PowerShell:
```bash
./gradlew assembleRelease
```
The resulting APK is generated at:
```
app/build/outputs/apk/release/app-release.apk
```

To sideload and run the APK onto an attached device or emulator via `adb`:
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.example.finora/.ui.splash.SplashActivity
```

---

## 4. Architecture Overview

Finora adheres to a clean **MVVM-lite (Model-View-ViewModel)** architectural pattern. Strict separation of concerns ensures that Activities and Fragments never communicate directly with Room DAOs or Retrofit services.

```
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer                              │
│  Activities, Fragments, ViewBinding, Adapters, ItemViews   │
└──────────────────────────────▲──────────────────────────────┘
                               │ Observes StateFlow / Dispatches User Actions
┌──────────────────────────────▼──────────────────────────────┐
│                    ViewModel Layer                          │
│   DashboardViewModel, TransactionsViewModel, BudgetViewModel│
│   WatchlistViewModel, AccountsViewModel                     │
└──────────────────────────────▲──────────────────────────────┘
                               │ Executes Coroutines / Suspend Functions
┌──────────────────────────────▼──────────────────────────────┐
│                   Repository Layer                          │
│   NetWorthRepository, TransactionRepository, AccountRepo,   │
│   BudgetRepository, WatchlistRepository, CategoryRepository │
└──────────────────────────────▲──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
┌──────────────────────────────┐ ┌──────────────────────────────┐
│     Room SQLite Database     │ │    Retrofit Network API      │
│  6 DAOs, Entities, Seedings  │ │   FinnhubApiService + OkHttp │
└──────────────────────────────┘ └──────────────────────────────┘
```

### 4.1 Package Structure Tree
```
com.example.finora/
├── FinoraApp.kt                     # Application class initializing Room DB & seeding
├── data/
│   ├── db/
│   │   ├── FinoraDatabase.kt        # Room Database with 6 entities & SeedCallback
│   │   ├── Converters.kt            # Room TypeConverters (Enums, Dates)
│   │   ├── dao/
│   │   │   ├── AccountDao.kt
│   │   │   ├── CategoryDao.kt
│   │   │   ├── TransactionDao.kt
│   │   │   ├── BudgetDao.kt
│   │   │   ├── WatchlistDao.kt
│   │   │   └── PortfolioDao.kt
│   │   └── entity/
│   │       ├── Account.kt
│   │       ├── Category.kt
│   │       ├── Transaction.kt
│   │       ├── Budget.kt
│   │       ├── WatchlistStock.kt
│   │       └── PortfolioHolding.kt
│   └── network/
│       ├── FinnhubApiService.kt     # Retrofit interface (/quote, /search)
│       ├── RetrofitProvider.kt      # OkHttp client with auth interceptor
│       └── dto/
│           ├── QuoteDto.kt
│           └── SymbolSearchResponseDto.kt
├── repository/
│   ├── AccountRepository.kt
│   ├── CategoryRepository.kt
│   ├── TransactionRepository.kt
│   ├── BudgetRepository.kt
│   ├── WatchlistRepository.kt
│   ├── PortfolioRepository.kt
│   └── NetWorthRepository.kt
├── ui/
│   ├── splash/SplashActivity.kt
│   ├── main/MainActivity.kt
│   ├── dashboard/
│   │   ├── DashboardFragment.kt
│   │   └── DashboardViewModel.kt
│   ├── transactions/
│   │   ├── TransactionsFragment.kt
│   │   ├── TransactionsViewModel.kt
│   │   ├── AddEditTransactionActivity.kt
│   │   └── TransactionDetailActivity.kt
│   ├── accounts/
│   │   ├── AccountsActivity.kt
│   │   ├── AddEditAccountActivity.kt
│   │   └── AccountsViewModel.kt
│   ├── budget/
│   │   ├── BudgetFragment.kt
│   │   ├── BudgetSetupActivity.kt
│   │   └── BudgetViewModel.kt
│   ├── watchlist/
│   │   ├── WatchlistActivity.kt
│   │   ├── StockDetailActivity.kt
│   │   └── WatchlistViewModel.kt
│   ├── nearby/NearbySpendingActivity.kt
│   └── settings/SettingsActivity.kt
└── util/
    ├── CategorizationRuleEngine.kt  # Substring rule matcher
    ├── RecurringDetector.kt         # Multi-month recurring charge detector
    ├── HaversineUtil.kt             # Great-circle distance calculations
    ├── PermissionUtils.kt           # Runtime permission helpers & rationale
    ├── CurrencyFormatter.kt         # Currency display formatting
    └── ImageUtils.kt                # Subsampled bitmap downsampling
```

### 4.2 Reactive State Flow Pattern
All ViewModels expose immutable `StateFlow<UiState>` streams. Activities and Fragments collect these flows inside:
```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            renderState(state)
        }
    }
}
```
This guarantees backgrounded screens halt UI updates, avoiding memory leaks and window-manager crashes.

---

## 5. Net Worth Calculation & Worked Example

Net Worth in Finora is a combined measure of liquid cash reserves and market equity holdings.

### 5.1 Mathematical Formula
$$\text{Net Worth} = \sum_{i} \text{Account.balance}_i + \sum_{j} (\text{PortfolioHolding.sharesOwned}_j \times \text{WatchlistStock.lastKnownPrice}_j)$$

Where:
- $\text{Account.balance}_i$ is positive for depository/cash accounts (Checking, Savings, Cash) and negative for liability accounts (Credit Cards, Personal Loans).
- $\text{WatchlistStock.lastKnownPrice}_j$ is the real-time or cached market price for symbol $j$ from Finnhub.

### 5.2 Hand-Verifiable Worked Example

Consider a realistic user portfolio:
- **Bank Checking Account:** $\$45,000.00$
- **Physical Cash Wallet:** $\$2,500.00$
- **Credit Card Balance:** $-\$3,200.00$ (Owed liability)
- **Portfolio Holding (AAPL):** $10$ shares @ current market price $\$215.00$

$$\begin{aligned}
\text{Total Liquid Cash} &= \$45,000.00 + \$2,500.00 + (-\$3,200.00) = \$44,300.00 \\
\text{Total Investments} &= 10 \text{ shares} \times \$215.00 = \$2,150.00 \\
\mathbf{\text{Total Net Worth}} &= \$44,300.00 + \$2,150.00 = \mathbf{\$46,450.00}
\end{aligned}$$

The Dashboard hero card computes and displays this exact total, breaking it down into liquid cash and investment components.

---

## 6. Smart Logic: Auto-Categorization & Recurring Detection

Finora deliberately implements transparent, rule-based algorithms rather than unpredictable black-box ML models.

### 6.1 Auto-Categorization Rule Engine ([`CategorizationRuleEngine.kt`](file:///app/src/main/java/com/example/finora/util/CategorizationRuleEngine.kt))
- **Mechanism:** Contains an internal map of lowercase merchant keywords mapped to standard category names (e.g., `uber`, `lyft`, `ola` $\rightarrow$ *Transport*; `swiggy`, `zomato`, `starbucks`, `mcdonald` $\rightarrow$ *Food & Dining*; `amazon`, `flipkart` $\rightarrow$ *Shopping*; `netflix`, `spotify` $\rightarrow$ *Subscriptions*).
- **Debounced Text Change:** As the user types into the merchant name field, a 400ms coroutine debounce invokes `suggestCategory(merchantText)`.
- **Manual Override Protection:** If a match is found and the user has not manually touched the category spinner in the current session, the suggested category is pre-selected and tagged with `isAutoCategorized = true`. If the user manually changes the spinner at any point, `isAutoCategorized` flips to `false`, permanently locking the user’s choice for that session.
- **UI Visibility:** In the transactions list, auto-categorized items display an auto-tag badge so the user clearly understands why a category was chosen.

### 6.2 Recurring-Charge Detection ([`RecurringDetector.kt`](file:///app/src/main/java/com/example/finora/util/RecurringDetector.kt))
- **Execution:** Runs asynchronously on app launch in the background.
- **Grouping:** Fetches all expense transactions from the past 3 calendar months. It groups entries by:
  $$(\text{merchant.lowercase().trim()}, \text{round}(\text{amount}))$$
- **Multi-Month Qualification:** For each group, it counts the number of distinct calendar months present.
- **Rule:** If $\text{distinctMonthCount} \ge 2$, all transactions in that group are marked `isRecurring = true`.
- **Auto-Decay:** If a recurring subscription is cancelled or stops appearing in recent months, subsequent evaluations reset `isRecurring = false`, ensuring outdated subscriptions drop off the Dashboard.

---

## 7. Assumptions & Design Choices

1. **Manual Account Entry:** No third-party bank aggregators (e.g., Plaid, Yodlee) are used. Transactions and initial balances are entered manually, giving users complete control and zero reliance on paid API tiers.
2. **Manual Portfolio Holdings:** Stock holdings (shares owned, average buy price) are entered manually, while current market valuations are fetched live via Finnhub.
3. **Finnhub Free-Tier Caching Strategy:** Finnhub's free API provides a rate limit of ~60 requests per minute. Finora implements a **60-second in-memory and Room SQLite cache** window. If a quote was retrieved less than 60 seconds ago, it is served instantly from local storage. Users can force a refresh at any time via pull-to-refresh on the Watchlist screen.
4. **Offline Resilience & Data Privacy:** All financial records, receipt images, and GPS coordinates remain strictly on the local device. The app is fully operational without an internet connection (displaying cached quotes when offline).
5. **Emulator GPS Testing:** Location tagging supports both real device hardware and emulator mock GPS locations (`adb emu geo fix <lon> <lat>`).

---

## 8. Known Limitations

1. **Price History Chart Depth:** Because Finnhub’s free tier restricts intraday historical candlestick queries, the stock price chart accumulates local sample points from successive quote fetches rather than rendering years of historical backdata.
2. **Recurring Amount Matching Simplification:** Recurring detection uses `amount.roundToInt()` to accommodate small fee differences (e.g., rounding tax variances). Variable utility bills that swing widely from month to month will not trigger the recurring detection rule.
3. **Single Device / Local SQLite Scope:** Data is kept private to the local device; there is no cloud synchronization or multi-user sharing mechanism.
4. **Single Currency:** All currency figures are formatted using a single global currency symbol ($) without multi-currency FX conversion.

---

## 9. Visual Walkthrough & Screenshots

Below is the verified screenshot gallery captured from a fresh install of the signed release APK on an emulator running Android 14/15:

| Screen | Description | File Link |
|---|---|---|
| **Dashboard** | Net worth hero card, cash/investments breakdown, Quick Action navigation buttons, and monthly summary | [01_dashboard.png](file:///screenshots/01_dashboard.png) |
| **Settings & Categories** | Seeded system default categories with icon badges, budget rollover toggle, and app info | [02_settings.png](file:///screenshots/02_settings.png) |
| **Settings (About)** | Default budget rollover setting, version info (1.0 Release) | [02b_settings_about.png](file:///screenshots/02b_settings_about.png) |
| **Transactions List** | Filterable transactions list with category/account chips, empty state handling, and FAB | [03_transactions.png](file:///screenshots/03_transactions.png) |
| **Add Transaction Form** | Date picker, account selector, merchant auto-categorization, camera receipt scan, and location toggle | [04_add_transaction.png](file:///screenshots/04_add_transaction.png) |
| **Accounts Management** | Multi-account overview (Bank, Savings, Cash, Credit Card) with balance summaries | [05_accounts.png](file:///screenshots/05_accounts.png) |
| **Budget Overview** | Category budgets, monthly limit progress bars with color-coded status, and rollover support | [06_budgets.png](file:///screenshots/06_budgets.png) |
| **Watchlist & Portfolio** | Live Finnhub stock quotes, day change percentages, search modal, and portfolio holdings | [07_watchlist.png](file:///screenshots/07_watchlist.png) |

---

## 10. Release Artifacts & Signing

- **Signed Release APK Location:**
  ```
  app/build/outputs/apk/release/app-release.apk
  ```
- **Size:** 7.19 MB
- **Keystore:** `app/finora-release.jks`
- **Key Alias:** `finora_release_key`
- **Signing Scheme:** Both **V1 (JAR Signature)** and **V2 (Full APK Signature)** are enabled and verified via Android SDK `apksigner`:
  ```
  Verifies: true
  Number of signers: 1
  Signer #1 certificate DN: CN=Finora, OU=Android, O=Finora, L=Mountain View, ST=CA, C=US
  ```
- **R8 / Minification:** Kept at `minifyEnabled = false` for deterministic release execution and stability.
