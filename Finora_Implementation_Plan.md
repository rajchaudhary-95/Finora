# Finora — Master Implementation Plan
## Android Mini-Project: A Google Finance × Copilot Money Combined App

**Status of this document:** This is the full execution-ready spec for the project solution already agreed in chat — a local-first Android personal finance app that combines Copilot Money's budgeting/net-worth engine with Google Finance's watchlist/portfolio module, plus camera receipt capture and location-tagged spending as the two features neither real app does well on Android. Confirmed decisions baked into this plan: **Room** for SQLite, and a **rule-based (non-ML) auto-categorization engine** as a real feature, not a stretch goal.

This version expands every section of the original plan with implementation-level detail — exact class shapes, formulas, edge cases, permission matrices, and API contracts — so a coding agent or a solo/small team can build directly from it without needing to re-derive design decisions mid-build.

---

## 1. Project Overview

### 1.1 Problem statement
Two well-known finance apps each do one half of "understand my money" well, and neither is available (or complete) on Android in the way a student project needs:

- **Google Finance** is a market-tracking tool — watchlist, quotes, portfolio value, news — but has no budgeting, no transaction categorization, and no receipt/expense tracking at all. It's an investing lens, not a spending lens.
- **Copilot Money** is a budgeting and net-worth tool — categorized transactions, monthly budgets, recurring-charge detection, a combined cash+investment net worth view — but it has **no Android app**, and even on iOS it has **no receipt scanning**: it reads bank transactions, it doesn't read paper receipts or handle cash.

**Finora's premise:** build the Android app that sits at the intersection — Copilot's budgeting/net-worth engine, using a Google-Finance-style watchlist as its investment data source, plus the one capability neither app offers: a camera pointed at a physical receipt, and a GPS pin on where the money was spent.

### 1.2 Target user (for framing UI decisions, not a formal persona exercise)
A student or early-career individual who: keeps a couple of manual accounts (a bank account, maybe a credit card, some cash), wants to see a monthly budget without linking a bank via a paid aggregator, occasionally buys small amounts of stock and wants to see it reflected in their overall net worth, and wants an easy way to log a cash purchase from a physical receipt without typing every line item by hand.

### 1.3 Comparative feature table

| Capability | Google Finance | Copilot Money | **Finora** |
|---|---|---|---|
| Stock watchlist & quotes | ✅ | ❌ | ✅ (via Finnhub API) |
| Portfolio value tracking | ✅ | ✅ (via linked brokerage) | ✅ (manually entered holdings, live-priced) |
| Bank-synced transactions | ❌ | ✅ (paid aggregator) | ❌ — manual entry only (documented assumption, see §1.4) |
| Automatic categorization | ❌ | ✅ (ML-based) | ✅ (rule-based keyword engine — see §8) |
| Monthly budgets w/ rollover | ❌ | ✅ | ✅ |
| Recurring-charge detection | ❌ | ✅ | ✅ (rule-based, see §8.2) |
| Combined cash+investment net worth | ❌ | ✅ | ✅ |
| Receipt photo capture | ❌ | ❌ | ✅ — the differentiator |
| Location-tagged spending | ❌ | ❌ | ✅ — the other differentiator |
| Android app | ✅ | ❌ | ✅ |

### 1.4 Deliberate scope exclusions (state these explicitly in the final report/README)
- **No real bank sync (no Plaid or equivalent).** Accounts and transactions are manually entered. Copilot itself relies on a paid third-party aggregator for this — not reproducible in an academic timeline, and not something a mini-project should attempt.
- **No real brokerage integration.** Portfolio holdings (shares owned, buy price) are manually entered; only the *current market price* comes from a live API.
- **No LLM/ML-based categorization or "AI research tool."** Replaced with a transparent, rule-based auto-categorizer — both achievable without external dependencies and easier to explain and defend in a viva than "we called an LLM API and hoped."
- **No multi-user accounts / cloud sync.** Single local user, single device, per the assignment's local-SQLite framing. This is a deliberate simplicity choice, not an oversight — flag it as a "Known Limitation" in the README rather than silently building toward it and running out of time.
- **No multi-currency support.** All amounts are treated as a single implicit currency (pick one, e.g. INR or USD, and be consistent — this avoids an entire class of conversion-rate complexity that isn't the point of the assignment).

---

## 2. Requirement-to-Feature Mapping

This mapping is the actual grading contract. Every phase in the companion build plan traces back to one of these seven rows. Where useful, a row also names the *specific class* expected to satisfy it, so there's no ambiguity later about whether a requirement was "technically" met.

| # | Requirement | Concrete feature(s) | Primary class(es)/file(s) |
|---|---|---|---|
| a | **Layouts** | `ConstraintLayout` on every screen; `RecyclerView`+`CardView` for Transactions/Watchlist/Accounts/Budgets lists; `BottomNavigationView` + `Fragment`s for the 4 main tabs; `TabLayout`+`ViewPager2` on Stock Detail; `LinearLayout` forms for Add/Edit screens | `activity_main.xml`, `fragment_dashboard.xml`, `item_transaction.xml`, `activity_stock_detail.xml` |
| b | **Intents** | Implicit: `MediaStore.ACTION_IMAGE_CAPTURE` (receipt photo), `Intent.ACTION_SEND` (share report), `geo:` URI intent (open Maps). Explicit: every activity-to-activity navigation, passing IDs via `Intent` extras | `AddEditTransactionActivity` (camera), `TransactionDetailActivity` (geo intent), `DashboardFragment` (share intent), all Activity pairs (explicit nav) |
| c | **Activity** | 9 activities total, full lifecycle handling (`onSaveInstanceState`, `ActivityResultLauncher`) | See §5's full activity table |
| d | **SQLite** | Room (compiles to real SQLite; schema + DAOs fully specified in §4) — 6 entities, 6 DAOs, 1 database class | `data/*.kt`, `FinoraDatabase.kt` |
| e | **Camera** | Receipt photo capture on Add/Edit Transaction, app-private storage, path stored in the `Transaction` row, thumbnail in lists, full image in detail | `AddEditTransactionActivity`, `TransactionDetailActivity`, `FileProvider` config |
| f | **Location API** | `FusedLocationProviderClient` captures lat/lng on transaction creation; `Geocoder` reverse-geocodes to an address; "Nearby Spending" sorts recent transactions by distance | `AddEditTransactionActivity`, `NearbySpendingActivity` |
| g | **Generate APK** | `Build > Generate Signed Bundle/APK` in Android Studio, documented step-by-step in §11 | build config / keystore |

**Cross-check discipline:** before final submission, walk this table top to bottom and, for each row, point to the actual running feature in the app (not just the code existing) — a requirement satisfied only in code that's never reachable from the UI is a real risk in a project this size (e.g. an orphaned camera-capture method never wired to a button).

---

## 3. Tech Stack

### 3.1 Core choices

| Layer | Choice | Version guidance | Rationale |
|---|---|---|---|
| Language | Kotlin | current stable (1.9.x/2.0.x line) | current standard for Android coursework and tooling; null-safety reduces a whole class of crash during a time-boxed build |
| IDE / build | Android Studio (latest stable channel), Gradle | AGP matching the Studio version's default | — |
| Min SDK | 26 (Android 8.0) | — | supports `FusedLocationProviderClient`, modern runtime permissions, and Room without any compatibility shims; still covers the overwhelming majority of real devices |
| Target/Compile SDK | latest stable at build time | — | keeps Play Store / sideload compatibility current, avoids deprecated-API warnings cluttering the build |
| Persistence | **Room** (SQLite) | `androidx.room:room-runtime` + `room-ktx` + `room-compiler` (via KSP, not KAPT, for faster builds) | confirmed choice — compiles and validates real SQL at build time, still literally SQLite under the hood |
| Networking | Retrofit + OkHttp + Moshi (or Gson) | current stable | Retrofit's coroutine `suspend fun` support pairs directly with Room's coroutine DAOs, avoiding any callback-vs-coroutine mismatch in the repository layer |
| Market-data API | **Finnhub** (primary) | REST, free tier | ~60 calls/minute free tier, real-time-ish US quotes, symbol search, and company news — see §3.2 for the detailed comparison and fallback plan |
| Location | `com.google.android.gms:play-services-location` + `android.location.Geocoder` | current stable | standard, well-documented, suf「ficient within a mini-project timeline |
| Camera | System camera via `Intent` (`MediaStore.ACTION_IMAGE_CAPTURE`) | — | deliberately simpler than CameraX — satisfies both Camera and Intents requirements at once with far less code |
| Charts | MPAndroidChart | current stable via JitPack | net-worth trend line, spending-by-category pie chart, stock price line chart — widely documented, low learning curve |
| Architecture | MVVM-lite | — | `ViewModel` + `Flow`/`StateFlow` + `Repository` layer over Room DAOs — enough structure to look deliberate without over-engineering a mini-project |
| Async | Kotlin Coroutines | `kotlinx-coroutines-android` | Room and Retrofit both have first-class coroutine support, avoiding AsyncTask/callback boilerplate entirely |
| Image loading (optional) | Manual `BitmapFactory` + `inSampleSize`, or a lightweight loader (Coil) | — | either is acceptable; manual downsampling avoids an extra dependency, Coil is less code — pick one and standardize, don't mix |

### 3.2 Market-data API decision detail

Two realistic free options were evaluated:

- **Alpha Vantage:** broad data coverage (50+ technical indicators, FX, fundamentals) but its free tier is now capped at **25 requests/day** with a 5-requests/minute ceiling — workable for a one-time demo but too restrictive for a watchlist screen a user might open repeatedly during a viva.
- **Finnhub:** a free tier offering **~60 API calls per minute**, real-time US stock quotes, symbol search, and company news, with real-time data delayed roughly 20 minutes on the free tier — more than sufficient for this project's needs and dramatically more forgiving during active development/demoing than Alpha Vantage's daily cap.

**Decision: Finnhub is primary.** Alpha Vantage is documented as a fallback only in case Finnhub's sign-up is blocked for a given student's account/region — if used as a fallback, the daily-cap constraint must be respected by caching aggressively (see §7.5) and by not polling on every screen open.

### 3.3 Dependencies NOT used, and why
- **No Dagger/Hilt.** Full dependency injection is unnecessary ceremony for an app this size; a manual singleton (Room database, Retrofit service) and constructor-injected ViewModels are simpler to explain and debug under a deadline.
- **No Jetpack Navigation Component.** A `BottomNavigationView` with manual `FragmentTransaction` swapping is sufficient for 4 tabs; Navigation Component's graph XML adds setup overhead disproportionate to the benefit here.
- **No Jetpack Compose.** The assignment specifically names "Layouts," strongly implying classic XML View-based layouts are the expected artifact — Compose is a legitimate but riskier choice to introduce alongside everything else in a time-boxed project.
- **No WorkManager.** Recurring-detection and price-cache-refresh logic run as simple in-process coroutines triggered on app open, not as scheduled background work — background scheduling isn't a stated requirement and adds real complexity (battery constraints, doze mode) for no grading benefit.

---

## 4. Architecture Overview

### 4.1 Layered structure

```
UI layer (Activities, Fragments, Adapters)
        │  observes StateFlow / calls suspend functions
        ▼
ViewModel layer (one per screen area)
        │  calls suspend functions / collects Flows
        ▼
Repository layer (one per entity area — the ONLY layer that talks to both Room and Retrofit)
        │                                   │
        ▼                                   ▼
Room (DAOs → SQLite)              Retrofit (FinnhubApiService → network)
```

**Hard rule:** no Activity or Fragment ever imports a DAO, the `FinoraDatabase` class, or `FinnhubApiService` directly. Everything routes through a Repository. This isn't stylistic — it's what makes Phase-based, one-feature-at-a-time building safe: a UI screen built in an early phase can have its data source upgraded (e.g. plugging in live pricing in a later phase) without the screen's own code changing.

### 4.2 Package structure

```
com.example.finora/
├── FinoraApplication.kt              # Application class, holds the DB/Retrofit singletons
├── data/
│   ├── entity/
│   │   ├── Account.kt
│   │   ├── Category.kt
│   │   ├── Transaction.kt
│   │   ├── Budget.kt
│   │   ├── WatchlistStock.kt
│   │   └── PortfolioHolding.kt
│   ├── dao/
│   │   ├── AccountDao.kt
│   │   ├── CategoryDao.kt
│   │   ├── TransactionDao.kt
│   │   ├── BudgetDao.kt
│   │   ├── WatchlistDao.kt
│   │   └── PortfolioDao.kt
│   ├── FinoraDatabase.kt
│   └── Converters.kt                 # TypeConverters for enums, if not stored as plain Strings
├── network/
│   ├── FinnhubApiService.kt
│   ├── dto/                          # raw API response shapes (QuoteDto, SymbolSearchResultDto, etc.)
│   └── RetrofitProvider.kt
├── repository/
│   ├── AccountRepository.kt
│   ├── TransactionRepository.kt
│   ├── BudgetRepository.kt
│   ├── WatchlistRepository.kt
│   ├── PortfolioRepository.kt
│   └── NetWorthRepository.kt
├── logic/
│   ├── CategorizationRuleEngine.kt
│   ├── RecurringDetector.kt
│   └── HaversineUtil.kt
├── ui/
│   ├── splash/SplashActivity.kt
│   ├── main/MainActivity.kt
│   ├── dashboard/DashboardFragment.kt, DashboardViewModel.kt
│   ├── transactions/
│   │   ├── TransactionsFragment.kt, TransactionsViewModel.kt
│   │   ├── AddEditTransactionActivity.kt
│   │   └── TransactionDetailActivity.kt
│   ├── accounts/AccountsActivity.kt, AddEditAccountActivity.kt, AccountsViewModel.kt
│   ├── budget/BudgetFragment.kt, BudgetSetupActivity.kt, BudgetViewModel.kt
│   ├── watchlist/WatchlistActivity.kt, StockDetailActivity.kt, WatchlistViewModel.kt
│   ├── nearby/NearbySpendingActivity.kt
│   └── settings/SettingsActivity.kt
└── util/
    ├── PermissionUtils.kt
    ├── ImageUtils.kt                 # downsampling helpers
    └── CurrencyFormatter.kt
```

### 4.3 State management convention
Pick **one** reactive style and apply it everywhere: **Kotlin `Flow`/`StateFlow`** is recommended over `LiveData` since it composes better with Room's and Retrofit's coroutine support and avoids importing the (now legacy-leaning) `lifecycle-livedata-ktx` artifact purely out of habit. Every ViewModel exposes `StateFlow<UiState>` properties; every Fragment/Activity collects them in `lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { ... } }` to avoid collecting while backgrounded.

### 4.4 Data flow example (concrete walkthrough, for onboarding whoever reads this)
Adding a transaction: `AddEditTransactionActivity` collects form input → calls `TransactionsViewModel.saveTransaction(...)` → ViewModel calls `TransactionRepository.insert(...)` → Repository writes via `TransactionDao.insert(...)` (Room/SQLite) **and** calls `AccountRepository.adjustBalance(...)` to keep the account balance consistent **and** calls `CategorizationRuleEngine`/`RecurringDetector` as needed → Activity receives a success/failure result via the ViewModel's `StateFlow` and finishes, returning to the caller.

---

## 5. Screens & Activities (full detail)

| # | Activity | Purpose | Layout type | Key UI elements | Launched from | Launches |
|---|---|---|---|---|---|---|
| 1 | `SplashActivity` | branding + first-launch routing | `ConstraintLayout` | centered logo/app name, no nav chrome | app launcher | `MainActivity` (explicit intent, `finish()` self) |
| 2 | `MainActivity` | hosts the 4 bottom-nav fragments | `ConstraintLayout` root + `BottomNavigationView` + `FragmentContainerView` | 4 nav items: Dashboard, Transactions, Budget, Watchlist | `SplashActivity` | `AccountsActivity`, `AddEditTransactionActivity`, `BudgetSetupActivity`, `WatchlistActivity`, `SettingsActivity`, `NearbySpendingActivity` (all explicit) |
| 3 | `AccountsActivity` | list accounts | `RecyclerView` + `CardView` items | account name, type icon, balance, FAB to add | `MainActivity` / `DashboardFragment` | `AddEditAccountActivity` |
| 4 | `AddEditAccountActivity` | create/edit an account | `LinearLayout` form | name `EditText`, type `Spinner`, balance `EditText` (numeric) | `AccountsActivity` | — (returns via `finish()`) |
| 5 | `AddEditTransactionActivity` | create/edit a transaction; hosts camera + location capture + auto-categorization | `LinearLayout` (scrollable via `ScrollView`/`NestedScrollView`) | account `Spinner`, amount `EditText`, merchant `EditText`, category `Spinner` (auto-filled), note `EditText`, `DatePickerDialog` trigger, "Scan Receipt" button + thumbnail `ImageView`, "Tag Location" `Switch` + resolved-address `TextView` | `TransactionsFragment` (new) or `TransactionDetailActivity` (edit) | system camera app (implicit intent), returns via `finish()` |
| 6 | `TransactionDetailActivity` | read-only single-transaction view | `ConstraintLayout` | all fields, full-size receipt `ImageView` (if present), address + "Open in Maps" button (if present), Edit/Delete actions | `TransactionsFragment` | `AddEditTransactionActivity` (edit), Maps app (implicit `geo:` intent) |
| 7 | `BudgetSetupActivity` | list + add/edit budgets | `RecyclerView` + `ProgressBar` per row | category, spent/limit, color-coded progress bar, FAB to add | `BudgetFragment` | add/edit dialog or sub-screen |
| 8 | `WatchlistActivity` | search + manage watchlisted stocks | `RecyclerView` (search results) + `RecyclerView` (watchlist) | search `EditText`, result rows with "+ Add", watchlist rows with price + day-change (colored) | `MainActivity` (Watchlist tab) or directly as the fragment's Activity-backed content | `StockDetailActivity` |
| 9 | `StockDetailActivity` | one stock's detail + chart + (if held) portfolio position | `TabLayout` + `ViewPager2` | Overview tab (price, day change, position/gain-loss), Chart tab (`MPAndroidChart` line chart) | `WatchlistActivity` | portfolio add/edit form |
| 10 | `NearbySpendingActivity` | geotagged transactions sorted by distance | `RecyclerView` | merchant, amount, computed distance, tap to open `TransactionDetailActivity` | `DashboardFragment` | `TransactionDetailActivity` |
| 11 | `SettingsActivity` | category management, defaults, about | `RecyclerView` or simple `LinearLayout` of setting rows | manage categories, default rollover toggle, app version | `MainActivity` toolbar/overflow | category add/edit dialog |

**Fragments hosted inside `MainActivity`:** `DashboardFragment`, `TransactionsFragment`, `BudgetFragment`, a `WatchlistFragment` wrapper (or `WatchlistActivity` itself serves this tab's content directly — see the note in the original plan: keep Watchlist and Stock Detail as genuine Activities, since the assignment specifically wants multiple Activities demonstrated, not everything folded into fragments for "efficiency").

### 5.1 Navigation flow diagram (textual)

```
SplashActivity
   │
   ▼
MainActivity ── BottomNav ──┬── DashboardFragment ──┬── AccountsActivity ── AddEditAccountActivity
                             │                        ├── NearbySpendingActivity ── TransactionDetailActivity
                             │                        └── (Share Intent → system share sheet)
                             │
                             ├── TransactionsFragment ──┬── AddEditTransactionActivity ── (camera intent)
                             │                          └── TransactionDetailActivity ──┬── AddEditTransactionActivity (edit)
                             │                                                          └── (geo: intent → Maps)
                             │
                             ├── BudgetFragment ── BudgetSetupActivity
                             │
                             └── WatchlistActivity ── StockDetailActivity ── (portfolio add/edit)

MainActivity toolbar ── SettingsActivity
```

---

## 6. Data Model (Room Entities) — full detail

### 6.1 `Account`
| Field | Type | Constraints/Notes |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` |
| `name` | `String` | not blank (validate in the form, not just the entity) |
| `type` | `String` | one of `CASH`, `BANK`, `CREDIT_CARD` — enforce via a Kotlin enum wrapper at the repository boundary even if stored as a plain String |
| `balance` | `Double` | running balance; **credit card balances are stored as negative-when-owing or positive-when-owing** — pick one convention explicitly (recommend: positive `balance` always means "money available to you," so a credit card balance is negative when you owe money) and document it once, since it affects net worth math |
| `createdAt` | `Long` | epoch millis, set once at insert |

### 6.2 `Category`
| Field | Type | Constraints/Notes |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` |
| `name` | `String` | unique in practice (not strictly enforced via a DB constraint unless desired — a simple app-level check before insert is acceptable) |
| `type` | `String` | `INCOME` or `EXPENSE` |
| `iconRes` | `Int` | drawable resource id |
| `isSystemDefault` | `Boolean` | `true` for the 9 seeded categories from §6.7; system-default categories cannot be deleted (enforced in the repository/UI layer, not the DB) |

### 6.3 `Transaction`
| Field | Type | Constraints/Notes |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` |
| `accountId` | `Int` | `@ForeignKey` → `Account.id`; decide and document `onDelete` behavior (recommend `RESTRICT` or a soft-delete pattern for accounts with transactions, rather than `CASCADE`, so a user can't accidentally wipe transaction history by deleting an account) |
| `categoryId` | `Int?` | `@ForeignKey` → `Category.id`, nullable until categorized (should be rare in practice once the rule engine exists, but the schema should tolerate it) |
| `amount` | `Double` | **always stored as a positive magnitude**; sign/direction is derived from the linked `Category.type` (income vs. expense) — this avoids the classic bug class of a negative amount times a negative category meaning something different than a positive amount times a negative category |
| `merchant` | `String` | free text; this is what `CategorizationRuleEngine` and `RecurringDetector` both key off of |
| `note` | `String?` | optional |
| `date` | `Long` | epoch millis, user-editable (not necessarily "now") |
| `receiptImagePath` | `String?` | absolute file path under app-private external storage; `null` if no receipt |
| `latitude` | `Double?` | `null` if not tagged or permission denied |
| `longitude` | `Double?` | |
| `address` | `String?` | reverse-geocoded string; can be `null` even when lat/lng are present (geocoding can fail) |
| `isRecurring` | `Boolean` | set exclusively by `RecurringDetector`, never by direct user input |
| `isAutoCategorized` | `Boolean` | `true` if `CategorizationRuleEngine` set the category and the user hasn't overridden it since; flips to `false` the moment the user manually changes the category |
| `createdAt` / `updatedAt` | `Long` | for audit/debugging, not shown in UI necessarily |

### 6.4 `Budget`
| Field | Type | Constraints/Notes |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` |
| `categoryId` | `Int` | `@ForeignKey` → `Category.id`; should logically only apply to `EXPENSE`-type categories — validate this in the form (don't let a user budget an income category) |
| `monthlyLimit` | `Double` | > 0, validate in the form |
| `rolloverEnabled` | `Boolean` | |
| `month` | `String` | `"YYYY-MM"` — budgets are period-scoped rows, not a single global row per category; a `UNIQUE(categoryId, month)` constraint is recommended so a category can't accidentally get two budgets in the same month |

### 6.5 `WatchlistStock`
| Field | Type | Constraints/Notes |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` |
| `symbol` | `String` | `@ColumnInfo(index = true)` and enforced unique via a `@Index(unique = true)` on the entity — e.g. `"AAPL"` |
| `displayName` | `String` | e.g. `"Apple Inc."` |
| `lastKnownPrice` | `Double` | cached from the last successful API call |
| `dayChangePercent` | `Double` | cached alongside price |
| `lastFetchedAt` | `Long` | epoch millis — the field the caching logic in §7.5 keys off of |

### 6.6 `PortfolioHolding`
| Field | Type | Constraints/Notes |
|---|---|---|
| `id` | `Int` | `@PrimaryKey(autoGenerate = true)` |
| `symbol` | `String` | **design decision, must be made explicitly and documented:** either (a) require the symbol to already exist in `WatchlistStock` (simpler — one pricing pathway, enforced via a `@ForeignKey` on `symbol`), or (b) allow any symbol independently and have `PortfolioRepository` fetch/cache its own price (more flexible, slightly more code). **Recommendation for this project's scope: option (a)** — require watchlisting before holding — since it keeps exactly one pricing/caching pathway in the whole app, which is easier to reason about and test |
| `sharesOwned` | `Double` | fractional shares allowed, > 0 |
| `avgBuyPrice` | `Double` | > 0 |

### 6.7 Default seed data (Category)
Seeded once on first launch (`isSystemDefault = true` for all): `Groceries` (Expense), `Food & Dining` (Expense), `Transport` (Expense), `Shopping` (Expense), `Subscriptions` (Expense), `Bills & Utilities` (Expense), `Entertainment` (Expense), `Salary` (Income), `Other Income` (Income), `Other Expense` (Expense — the fallback for anything the rule engine can't match).

### 6.8 Derived values (computed at read time, never stored)
- **Signed transaction effect on balance** = `if (category.type == INCOME) +amount else -amount`
- **Net worth** = `Σ(Account.balance)` + `Σ(PortfolioHolding.sharesOwned × WatchlistStock.lastKnownPrice for matching symbol)`
- **Holding gain/loss** = `(currentPrice − avgBuyPrice) × sharesOwned`
- **Holding gain/loss %** = `((currentPrice − avgBuyPrice) / avgBuyPrice) × 100`
- **Budget spent-so-far** = `Σ(Transaction.amount WHERE categoryId = budget.categoryId AND strftime('%Y-%m', date/1000, 'unixepoch') = budget.month)`
- **Budget effective limit** (with rollover) = `monthlyLimit + max(0, previousMonth.monthlyLimit − previousMonth.spentSoFar)` if `rolloverEnabled`, else `monthlyLimit`
- **Budget remaining** = `effectiveLimit − spentSoFar`
- **Budget status** = `OK` (< 75% used), `AT_RISK` (75–100%), `OVER` (> 100%) — drives the progress bar color

### 6.9 DAO query catalogue
One `@Dao` interface per entity, each exposing standard `insert`/`update`/`delete`/`getById`/`getAll` as `suspend fun` or `Flow<...>`, plus these entity-specific queries:

- `TransactionDao.getSpendingByCategory(month: String): Flow<List<CategorySpending>>` — `SELECT categoryId, SUM(amount) as total FROM "Transaction" WHERE strftime('%Y-%m', date/1000, 'unixepoch') = :month GROUP BY categoryId`
- `TransactionDao.getRecurringCandidates(): List<Transaction>` — either a raw `GROUP BY merchant, ROUND(amount)` query with a `HAVING COUNT(DISTINCT month) >= 2` clause, or a broader fetch-and-group-in-Kotlin approach if the SQL becomes unwieldy (acceptable, document which was chosen)
- `TransactionDao.getGeotaggedTransactions(): Flow<List<Transaction>>` — `WHERE latitude IS NOT NULL`
- `BudgetDao.getForMonth(month: String): Flow<List<Budget>>`
- `WatchlistDao.getBySymbol(symbol: String): WatchlistStock?`
- `PortfolioDao.getBySymbol(symbol: String): PortfolioHolding?`

---

## 7. Detailed Feature Specifications

### 7.1 Camera flow — full sequence

1. User taps **"Scan Receipt"** on `AddEditTransactionActivity`.
2. App checks `CAMERA` permission via `ContextCompat.checkSelfPermission`; if not granted, launches `ActivityResultContracts.RequestPermission()`.
3. **If denied:** show a `Snackbar` — *"Camera permission is needed to scan receipts. You can still save this transaction without one."* — and leave the form otherwise fully usable.
4. **If granted:** create a destination file: `File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "receipt_${System.currentTimeMillis()}.jpg")`.
5. Wrap it via `FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)`.
6. Launch `Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)` through an `ActivityResultLauncher<Intent>` registered in `onCreate`/`onAttach` (never inline at click-time, per modern Activity Result API rules).
7. On a successful result callback, the file at the known path now contains the photo. Store the **absolute path string** (not the `content://` URI, which isn't guaranteed valid across app restarts the same way a file path is) into the in-memory form state.
8. Downscale for display: `BitmapFactory.Options().apply { inSampleSize = calculateInSampleSize(...) }` before setting into the thumbnail `ImageView` — loading a full 12MP camera image directly into a 100dp thumbnail is a real, easily-hit performance/memory bug in student projects.
9. On form save, persist `receiptImagePath` into the `Transaction` row via Room.
10. In `TransactionDetailActivity`, if `receiptImagePath != null`, decode and display at a larger (but still capped) sample size; if `null`, hide the entire receipt section rather than showing an empty placeholder.

**Edge cases to explicitly handle:** user cancels the camera app without taking a photo (the destination file may exist but be empty/zero-byte — check file size > 0 before treating it as valid); user replaces an existing receipt on an edit (overwrite the path, and consider deleting the old file to avoid orphaned images accumulating in storage — a nice-to-have, not required); storage permission edge cases on very old vs. very new Android versions (scoped storage since Android 10 means app-private external files directories don't need `WRITE_EXTERNAL_STORAGE` at all — do not request that permission, it's unnecessary noise and a common student mistake).

### 7.2 Location flow — full sequence

1. User toggles **"Tag Location"** on `AddEditTransactionActivity`.
2. App checks `ACCESS_FINE_LOCATION`; if absent, checks `ACCESS_COARSE_LOCATION` as a fallback; if both absent, requests fine location via `ActivityResultContracts.RequestPermission()`.
3. **If denied:** toggle switches back off automatically, `Snackbar` explains the transaction can still be saved without location.
4. **If granted:** call `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)` — **not** `getLastLocation()`, which can return `null` on a fresh device/emulator or stale data that misrepresents the actual transaction location.
5. Add a client-side timeout (e.g. 10 seconds via a coroutine `withTimeoutOrNull`) — if no fix arrives, toggle off and show a message rather than leaving the UI in an indefinite "loading" state.
6. On success, call `Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)` (API 33+ has an async overload — use whichever matches the project's min/target SDK approach consistently) to resolve an address string.
7. **Geocoder can legitimately return an empty list** (common on emulators without Google Play services configured correctly, or in areas with poor geocoding coverage) — store the raw `latitude`/`longitude` regardless, and leave `address = null` if resolution fails; never block the save on a failed geocode.
8. On `TransactionDetailActivity`, display `address` if present, else a formatted `"$latitude, $longitude"` fallback string, plus an **"Open in Maps"** button.
9. The Maps button fires: `Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(merchant)})"))`. Guard with `intent.resolveActivity(packageManager) != null` (or a `try/catch ActivityNotFoundException`) since not every emulator image has a Maps-capable app installed.
10. **"Nearby Spending" (`NearbySpendingActivity`):** on open, get current location once (same permission/flow as above, but read-only — no need to re-tag anything), fetch `TransactionDao.getGeotaggedTransactions()`, compute distance to each via a plain Haversine formula (`HaversineUtil.kt`, no dependency needed), sort ascending, display with the computed distance (e.g. `"0.8 km away"`) per row.

**Testing note (repeat prominently in the README):** Android emulators support **mock/injected location** via Extended Controls → Location — this is the standard way to test this feature convincingly without a physical device, and should be explicitly demonstrated in any recorded demo, since a fresh emulator's default location is often unset or a generic default (e.g. Mountain View, CA).

### 7.3 Auto-Categorization Engine — algorithm detail

```
class CategorizationRuleEngine(private val categoryLookup: Map<String, Category>) {

    private val keywordMap: Map<String, String> = mapOf(
        "uber" to "Transport", "ola" to "Transport", "lyft" to "Transport",
        "rapido" to "Transport", "metro" to "Transport",
        "swiggy" to "Food & Dining", "zomato" to "Food & Dining",
        "doordash" to "Food & Dining", "starbucks" to "Food & Dining",
        "mcdonald" to "Food & Dining", "dominos" to "Food & Dining",
        "amazon" to "Shopping", "flipkart" to "Shopping", "myntra" to "Shopping",
        "netflix" to "Subscriptions", "spotify" to "Subscriptions",
        "prime video" to "Subscriptions", "hotstar" to "Subscriptions",
        "electricity" to "Bills & Utilities", "broadband" to "Bills & Utilities",
        "recharge" to "Bills & Utilities",
        "bigbasket" to "Groceries", "grofers" to "Groceries", "dmart" to "Groceries",
        "salary" to "Salary"
        // extend further; aim for 3-4+ keywords per default category
    )

    fun suggestCategory(merchantText: String): Category? {
        val lower = merchantText.trim().lowercase()
        if (lower.isEmpty()) return null
        val match = keywordMap.entries.firstOrNull { (keyword, _) -> lower.contains(keyword) }
        return match?.let { categoryLookup[it.value] }
    }
}
```

**Wiring into the UI:** the merchant `EditText`'s `addTextChangedListener` is debounced (≈300–500ms, via a `Handler.postDelayed` reset on every keystroke, or a coroutine `debounce()` operator over a `Flow` of text changes) before calling `suggestCategory`. If a match is found **and** the user has not manually touched the category `Spinner` in this editing session, pre-select it and set `isAutoCategorized = true`. The moment the user manually changes the `Spinner` (at any point), set `isAutoCategorized = false` and stop auto-overriding for the remainder of that session — a later debounced suggestion firing after a manual choice must not clobber it.

**UI transparency requirement:** the transaction list shows a small indicator (e.g. a subtle icon badge) on rows where `isAutoCategorized == true`, so the categorization behavior is visible and explainable, not a hidden black box — this is also a strong point to highlight in a viva, since it directly answers "how is this different from just hardcoding a category picker?"

### 7.4 Recurring-Charge Detection — algorithm detail

Runs once per app-open (a coroutine launched from `FinoraApplication.onCreate()` or lazily from `DashboardViewModel.init`), not on a schedule:

```
1. Fetch all transactions from the last 3 full calendar months.
2. Group by (merchant.lowercase().trim(), amount.roundToInt()).
3. For each group, count the DISTINCT calendar months present.
4. If distinctMonthCount >= 2, mark every transaction in that group isRecurring = true.
5. Any transaction NOT in a qualifying group has isRecurring reset to false
   (so a merchant that WAS recurring but has since stopped repeating
   eventually falls out of the recurring list, rather than staying
   permanently flagged from one historical coincidence).
```

**Edge cases:** a single one-off transaction never qualifies (distinctMonthCount = 1). Two transactions with the same merchant but meaningfully different amounts (e.g. a variable electricity bill) should NOT match — this is exactly why grouping uses `amount.roundToInt()` rather than an exact-decimal match, but even rounding won't catch a genuinely variable bill, and that's fine — it's an intentional simplification, not a bug, and worth stating as such in the README rather than over-engineering a fuzzy-matching threshold for a mini-project.

### 7.5 Watchlist / Portfolio — Finnhub API integration detail

**Endpoints used:**
- `GET /quote?symbol={symbol}&token={apiKey}` → returns current price, change, percent change, high/low/open, previous close
- `GET /search?q={query}&token={apiKey}` → returns matching symbols/company names for the search screen

**Example response shape (quote):**
```json
{ "c": 261.74, "d": 4.28, "dp": 1.66, "h": 264.89, "l": 259.51, "o": 260.5, "pc": 257.46, "t": 1699999999 }
```
Map `c` (current price) → `WatchlistStock.lastKnownPrice`, `dp` (day change percent) → `dayChangePercent`.

**Caching discipline (critical given rate limits):**
```
fun shouldRefetch(stock: WatchlistStock): Boolean {
    val ageMillis = System.currentTimeMillis() - stock.lastFetchedAt
    return ageMillis > 60_000  // 60-second cache window
}
```
Before any quote fetch, check `shouldRefetch`; if false, serve `lastKnownPrice` from Room directly with no network call. A manual **pull-to-refresh** (`SwipeRefreshLayout`) on the watchlist screen bypasses this check for an explicit, user-initiated refresh.

**Error handling matrix:**

| Failure | Handling |
|---|---|
| No internet connectivity | Catch `IOException` from Retrofit/OkHttp; show a `Snackbar` ("No internet connection — showing last known prices"); fall back to cached `lastKnownPrice` |
| Rate limit exceeded (HTTP 429) | Catch the `HttpException`, back off, show cached data, do not retry immediately in a loop |
| Invalid/unrecognized symbol | Finnhub returns a quote with all-zero fields for an invalid symbol rather than an HTTP error — explicitly check for this (`c == 0.0 && pc == 0.0`) and treat it as "symbol not found," not as a valid zero-price stock |
| Malformed/unexpected JSON | Wrap Moshi/Gson parsing in try/catch; never let a parsing exception propagate to crash the Activity |

**Chart data:** Finnhub's free tier has limited historical-candle access. A pragmatic approach for this project's scope: build the price-history chart from **locally accumulated samples** — every successful quote fetch appends a timestamped price point to a small local cache (could be its own lightweight table, or even an in-memory list persisted via `SharedPreferences` as a simple JSON blob if a full new Room table feels like overkill for this one chart) — rather than depending on a potentially-restricted bulk historical endpoint. Document this choice plainly: the chart shows "price history since you started watching this stock in this app," not a deep multi-year history, which is an honest and explainable scope limitation.

### 7.6 Net Worth calculation — worked example (for README/viva clarity)

Given: Account "Bank" balance = ₹45,000; Account "Cash" balance = ₹2,500; Account "Credit Card" balance = −₹3,200 (owed); PortfolioHolding of 10 shares of `AAPL` at `lastKnownPrice` = ₹21,500 (converted/illustrative).

```
Net Worth = (45,000 + 2,500 + (-3,200)) + (10 × 21,500)
          = 44,300 + 215,000
          = ₹259,300
```

This worked example should appear in the README verbatim (with real seeded numbers from the actual seed data) so a grader can hand-verify the Dashboard's displayed figure against it in seconds.

---

## 8. Permissions — full manifest & runtime matrix

| Permission | Manifest declaration | Requested at runtime? | When | Graceful-denial behavior |
|---|---|---|---|---|
| `CAMERA` | Yes | Yes | First "Scan Receipt" tap | Transaction still saveable without a receipt |
| `ACCESS_FINE_LOCATION` | Yes | Yes | First "Tag Location" toggle-on | Toggle auto-reverts off; transaction still saveable without location |
| `ACCESS_COARSE_LOCATION` | Yes | Yes (as fallback if fine is denied but coarse is granted) | Same as above | Same as above |
| `INTERNET` | Yes | No (normal permission, granted at install) | — | — |
| `ACCESS_NETWORK_STATE` | Yes (optional but recommended) | No | — | used only to pre-check connectivity before a Finnhub call and show a clearer "offline" message rather than waiting for a timeout |

**Explicitly NOT requested:** `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` — unnecessary under scoped storage (API 26+ target) since receipts are written to the app's own external-files directory, which requires no special permission. Requesting this anyway is a common student mistake worth calling out and avoiding.

---

## 9. Non-Functional Requirements

- **Performance:** receipt thumbnails must load without visible jank — always downsample before displaying (see §7.1). Watchlist/quote calls must never block the main thread — all network and Room I/O happens via coroutines on `Dispatchers.IO`.
- **Offline resilience:** every screen that depends on network data (Watchlist, Stock Detail) must degrade to cached data rather than an empty/broken screen when offline. Screens with no network dependency (Accounts, Transactions, Budgets) must work fully offline at all times — this is a local-first app, and the vast majority of its screens should never even notice a missing connection.
- **Data integrity:** account balances must never drift from the sum of their transaction history due to a partial write — wrap the "insert/edit/delete transaction + adjust account balance" sequence in a Room `@Transaction`-annotated repository method so both writes commit or neither does.
- **Security/privacy:** the Finnhub API key is never committed to version control (see Phase 0 in the companion build plan) and is not logged. Receipt images and location data stay entirely on-device — there is no upload path for either, which is worth stating positively in the README as a privacy characteristic of the app, not just an implementation detail.
- **Accessibility (nice-to-have, not graded but easy wins):** all interactive elements have `contentDescription`s; text sizes use `sp`, not hardcoded `dp`, so they respect system font-scaling settings.

---

## 10. Error Handling & Edge Cases — consolidated matrix

| Scenario | Expected behavior |
|---|---|
| Delete an account with existing transactions | Warn via `AlertDialog`; either block deletion or offer to reassign/delete the transactions too — pick one policy and apply it consistently (recommend: block deletion until the account has zero transactions, simplest to implement and explain) |
| Negative or zero transaction amount entered | Form-level validation rejects it before it ever reaches the Repository/Room layer |
| Budget limit ≤ 0 entered | Same — form-level validation |
| Two budgets created for the same category+month | Prevented by excluding already-budgeted categories from the picker (see Phase 9 in the build plan); if a race or edit path could still cause it, a `UNIQUE(categoryId, month)` index at the DB level is the backstop |
| Camera permission permanently denied ("Don't ask again") | Detect via `shouldShowRequestPermissionRationale` returning `false` after a prior denial; show a message directing the user to app settings rather than silently re-prompting on every tap |
| Location fix times out | Auto-cancel after ~10s, toggle off, inform the user, allow save without location |
| Geocoder returns no result | Store raw coordinates, leave address null, do not block save |
| Finnhub returns invalid-symbol zero-quote | Treated as "not found," shown as a search-result error, never silently added to the watchlist |
| App killed mid-form-entry (e.g. incoming call) | `onSaveInstanceState`/`ViewModel`-held state should restore the in-progress form on return, at minimum for the fields already entered before the interruption |
| Rotating the device on any screen | No crash, no data loss on in-progress forms — test this explicitly on every screen, not just the obvious ones |

---

## 11. Generating the APK

1. In Android Studio: **Build → Generate Signed Bundle / APK…** → select **APK** (not App Bundle, since the deliverable is a directly-installable file, not a Play Store upload).
2. **Create new…** keystore if one doesn't already exist. Record the keystore file path, key alias, store password, and key password somewhere safe (a password manager, or a `.gitignore`d local note) — losing these blocks re-signing future updates, though for a one-off academic submission this is a minor risk, not a blocker.
3. Choose the **release** build variant.
4. Check both **V1 (Jar Signature)** and **V2 (Full APK Signature)** for broad device compatibility.
5. Leave `minifyEnabled false` in the release `buildTypes` block unless there's a specific, tested reason to enable R8/ProGuard — obfuscation-related crashes that only appear in the release build are hard to debug under a deadline, and code size isn't a grading criterion here.
6. Build. The output APK lands at `app/release/app-release.apk` — this is the submission artifact.
7. **Sanity-test the generated APK specifically**, not just the dev build: sideload it onto a device or emulator image that was **not** used during development. This catches dev-only assumptions — e.g. a permission that was already granted from repeated testing during development, masking a bug in the first-run permission-request flow; or a hardcoded local API key behaving differently from the `BuildConfig`-sourced one.
8. Increment `versionCode`/`versionName` in `build.gradle` if this is a resubmission after fixes, so the artifact is distinguishable from a prior attempt.

---

## 12. Testing & Acceptance Checklist

- [ ] Add an account, add a transaction against it, confirm the account balance updates correctly for both an income and an expense category
- [ ] Enter a merchant name matching a seeded keyword → category auto-fills; manually override it → `isAutoCategorized` flips to false and the override sticks on re-open
- [ ] Add 2+ transactions with the same merchant+rounded-amount in consecutive months → confirm they're flagged recurring and surfaced on the Dashboard
- [ ] Set a budget for a category, add transactions until near/over the limit → progress bar and status (OK/AT_RISK/OVER) reflect it correctly, including correct color-coding
- [ ] Enable rollover on a budget, verify a prior month's positive remainder correctly increases the current month's effective limit
- [ ] Capture a receipt photo on a transaction → thumbnail appears in the list, full image opens in detail, and it survives an app restart (real file, not lost with the process)
- [ ] Deny camera permission → app doesn't crash, clear message shown, transaction still saves without a receipt
- [ ] Tag a transaction's location (via emulator mock location) → address resolves (or gracefully falls back to raw coordinates), "Open in Maps" launches Maps correctly with the right coordinates
- [ ] Deny location permission → app doesn't crash, transaction still saves without location
- [ ] Nearby Spending screen correctly sorts geotagged transactions by distance from a mock/injected current location — verify against at least 3 transactions at known, meaningfully different distances
- [ ] Add a stock to the watchlist, confirm a live price is fetched and cached; re-opening within 60 seconds serves the cache (verify via Logcat/network inspector, not just visually); pull-to-refresh bypasses the cache
- [ ] Airplane-mode test on Watchlist/Stock Detail: no crash, clear offline message, cached data still shown if available
- [ ] Add a portfolio holding for a watchlisted symbol, confirm gain/loss computes correctly against the cached current price
- [ ] Confirm the Dashboard's net worth figure exactly matches a hand-calculated expected value (per the worked example in §7.6) against real seeded data
- [ ] Share a report via `ACTION_SEND` and confirm the share sheet opens with sensible, accurate content
- [ ] Rotate the device on every screen at least once; confirm no crash and no silent loss of in-progress form data
- [ ] Generate the signed release APK and sideload it onto a device/emulator not used during development; confirm first-run permission prompts, camera capture, location tagging, and live watchlist pricing all behave identically to the dev build

---

## 13. README/Report Requirements (write last, after the build)

Mirroring the disciplined documentation approach used for prior projects — the final README should include:
- Install & run instructions (clone, open in Android Studio, add `FINNHUB_API_KEY=...` to `local.properties`, build & run)
- Architecture overview (MVVM-lite, Room, Retrofit) with the package-structure tree from §4.2
- The requirement-to-feature mapping table from §2, reproduced as direct evidence of coverage
- The net-worth worked example from §7.6, with real numbers from the actual seed data, so it's hand-verifiable
- An explanation of the auto-categorization rule engine and the recurring-detection logic (§7.3–7.4), since these are the two pieces of "smart" behavior most likely to draw questions in a viva
- Assumptions section: no bank sync, no brokerage integration, single currency, single local user, Finnhub free-tier caching strategy and why it matters, emulator-based location testing method
- Known Limitations section, stated honestly rather than omitted — e.g. the price-history chart's limited depth (§7.5), the simplification in recurring-amount matching (§7.4)
