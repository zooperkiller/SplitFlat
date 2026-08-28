# 📱 SplitFlat — Technical Design Document

## Table of Contents

- [1. Introduction](#1-introduction)
- [2. Goals & Scope](#2-goals-scope)
- [3. Technology Stack](#3-tech-stack)
- [4. System Architecture](#4-architecture)
- [5. Project Structure](#5-project-structure)
- [6. Data Design](#6-data-design)
- [6.1 Firestore Collections](#6-1-collections)
- [6.2 Data Models](#6-2-models)
- [6.3 Access Patterns](#6-3-access-patterns)
- [7. Module Design](#7-module-design)
- [7.1 Authentication](#7-1-auth)
- [7.2 Navigation](#7-2-navigation)
- [7.3 Home Screen](#7-3-home)
- [7.4 Create Group](#7-4-create-group)
- [7.5 Group Detail](#7-5-group-detail)
- [7.6 Add / Edit Expense](#7-6-add-expense)
- [7.7 QR Join & Share](#7-7-qr)
- [7.8 Theming](#7-8-theme)
- [8. Core Algorithms](#8-algorithms)
- [8.1 Balance Computation](#8-1-balance)
- [8.2 Debt Simplification](#8-2-simplifier)
- [8.3 Settlement Logic](#8-3-settle)
- [9. UI / UX Design](#9-ui-ux)
- [10. Security & Privacy](#10-security)
- [11. Error Handling & Resilience](#11-error-handling)
- [12. Testing Strategy](#12-testing)
- [13. Build & Deployment](#13-build-deploy)
- [14. Limitations & Future Roadmap](#14-limitations)
- [15. Appendix](#15-appendix)

---

## 1. Introduction

### 1.1 Purpose of this Document

This Technical Design Document (TDD) describes the complete technical architecture, data design, module breakdown, algorithms, and engineering decisions behind **SplitFlat**. It is intended for developers maintaining or extending the codebase, technical reviewers, and contributors onboarding to the project.

### 1.2 Product Summary

SplitFlat is a minimalist, modern Android application for splitting expenses and managing group finances. Users authenticate with email/password via Firebase, create expense groups (or 1-on-1 "friend ledgers"), record expenses with flexible split modes, track balances in real time, and settle up debts — optionally minimized through a *Smart Debt Simplification* algorithm. Group membership can be shared instantly via QR codes.

### 1.3 Definitions & Acronyms

| Term | Definition |
|---|---|
| **TDD** | Technical Design Document (this document) |
| **UID** | Firebase Authentication user identifier |
| **Group** | A shared expense container with 1..N members |
| **Friend Ledger** | A special 1-on-1 group (`isOneOnOne = true`) representing a direct friendship balance |
| **Balance** | Net position of a member: positive = is owed money, negative = owes money |
| **Settlement** | A recorded payment that offsets an existing debt (stored as an Expense) |
| **Simplification** | Greedy netting algorithm that reduces the number of transactions required to settle all debts |

---

## 2. Goals & Scope

### 2.1 Product Goals

- Provide a fast, clean, ad-free experience for tracking shared expenses.
- Support both **group expenses** (trips, roommates, events) and **1-on-1 friend ledgers**.
- Offer flexible splitting: *Equal*, *Exact amounts*, and *Percentages*.
- Minimize the number of repayments via Smart Debt Simplification.
- Enable instant group joining through QR code scanning.
- Maintain real-time consistency across all members' devices using Firestore listeners.

### 2.2 In Scope (v1.0)

| Feature | Status |
|---|---|
| Email/password authentication + password reset | Implemented |
| Group creation with email invitations | Implemented |
| 1-on-1 friend ledgers | Implemented |
| Expense CRUD (create / edit / delete) | Implemented |
| Equal / Exact / Percentage splits | Implemented |
| Category tagging + donut chart analytics | Implemented |
| Smart Debt Simplification | Implemented |
| Settle Up (record payments) | Implemented |
| QR code generation & scanning to join groups | Implemented |
| Light / dark theming | Implemented |
| Push notifications, offline queueing, multi-currency | Out of Scope v1 |

### 2.3 Non-Goals

- No web or iOS client in v1.0.
- No server-side business logic — all computation happens client-side against Firestore.
- No payment processing integration (settlements are recorded, not executed).

---

## 3. Technology Stack

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| **Language** | Kotlin | 2.2.10 | Official Android language; concise, null-safe, coroutine-friendly. |
| **UI Toolkit** | Jetpack Compose + Material 3 | BOM 2026.02.01 | Declarative UI, less boilerplate, modern Material You design system. |
| **Build System** | Gradle (Kotlin DSL) + AGP | AGP 9.2.1 | Type-safe build scripts; version catalog for dependency management. |
| **Authentication** | Firebase Authentication | BOM 33.1.2 | Managed email/password auth, password reset, session persistence. |
| **Database** | Cloud Firestore | BOM 33.1.2 | Real-time listeners, offline cache, flexible document model. |
| **Navigation** | Navigation Compose | 2.7.7 | Type-safe route arguments, back-stack management. |
| **QR Generation** | ZXing (core) | 3.5.3 | Lightweight, dependency-free QR encoding to Bitmap. |
| **QR Scanning** | Google Play Services Code Scanner (ML Kit) | 16.1.0 | No camera permission required; Google-hosted scanning UI. |
| **AndroidX** | Core KTX, Lifecycle, Activity Compose | 1.10.1 / 2.6.1 / 1.8.0 | Core Kotlin extensions and lifecycle-aware components. |
| **Testing** | JUnit 4, Espresso, Compose UI Test | 4.13.2 / 3.5.1 / BOM | Unit + instrumented + declarative UI testing. |

SDK Configuration: minSdk = 24 (Android 7.0, ~99% device coverage), targetSdk/compileSdk = 36, Java 11 compatibility, single release build type with minification disabled in v1.0.

---

## 4. System Architecture

### 4.1 Architectural Style

SplitFlat follows a **client-centric, single-module Compose architecture**. There is no custom backend — Firebase acts as the entire server layer. The app is organized into a lightweight layered structure inside one Gradle module:

🎨 UI Layer (Compose Screens)
      ⇄
      🧭 Navigation Layer
      ⇄
      💾 Firebase Services

LoginScreen
      SignUpScreen
      HomeScreen
      CreateGroupScreen
      GroupDetailScreen
      AddExpenseScreen

utils / DebtSimplifier
      utils / QrCodeGenerator
      model / User · Group · Expense

🔥 Firebase Auth &nbsp;·&nbsp; Cloud Firestore &nbsp;·&nbsp; ML Kit Scanner

### 4.2 Layer Responsibilities

| Layer | Package | Responsibility |
|---|---|---|
| **UI Layer** | `ui.auth`, `ui.home`, `ui.group`, `ui.theme` | Stateful Composables that own screen state via `remember`/`mutableStateOf`, render Material 3 UI, and invoke Firebase SDK directly through listener callbacks. |
| **Navigation** | `navigation.AppNavigation` | Defines the route graph (`Screen` sealed class), resolves auth state for the start destination, and wires navigation callbacks between screens. |
| **Domain / Utils** | `utils.DebtSimplifier`, `utils.QrCodeGenerator` | Pure, side-effect-free business logic: debt netting algorithm and QR bitmap generation. |
| **Model** | `model.Models.kt` | Serializable data classes mirroring Firestore documents. |
| **Backend (BaaS)** | Firebase | Identity (Auth), persistence + realtime sync (Firestore), barcode scanning (ML Kit via Play Services). |

### 4.3 Key Architectural Decisions (ADR Summary)

| # | Decision | Alternatives Considered | Consequences |
|---|---|---|---|
| ADR-1 | Direct Firebase calls from Composables (no ViewModel/Repository layer) | MVVM with Repositories, Clean Architecture | ✅ Minimal boilerplate, fast MVP delivery. ⚠️ Logic coupled to UI; harder to unit test; refactor to ViewModels recommended as the app grows. |
| ADR-2 | Settlements stored as `Expense` documents | Separate `settlements` collection | ✅ Single balance computation path; settlements automatically reflected in balances. ⚠️ Settlement entries appear in the expense list. |
| ADR-3 | Friend ledgers modeled as Groups with `isOneOnOne` flag | Dedicated friendship entity | ✅ Reuses all group machinery (expenses, balances, QR). ⚠️ Slight semantic overload of the Group model. |
| ADR-4 | Play Services Code Scanner instead of CameraX + ZXing decode | CameraX + ML Kit bundled, ZXing scanning | ✅ Zero camera permission, tiny APK impact, Google-managed UI. ⚠️ Requires Google Play Services on device. |
| ADR-5 | Greedy max-debtor/max-creditor netting | Linear programming (optimal min-transactions), graph algorithms | ✅ Simple, O(n²) worst case, produces ≤ n−1 transactions in practice. ⚠️ Not guaranteed globally minimal transaction count. |
| ADR-6 | Dynamic color disabled by default | Material You dynamic theming on Android 12+ | ✅ Consistent minimalist brand palette (mint green / charcoal). ⚠️ Less OS-level personalization. |

### 4.4 Runtime Flow — Cold Start

**Flow:** App Launch → MainActivity.onCreate → enableEdgeToEdge + setContent → SplitFlatTheme → AppNavigation → Auth check: currentUser? → Login OR Home

---

## 5. Project Structure

SplitFlat/
├── build.gradle.kts            // Root build config
├── settings.gradle.kts          // Module registration
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml       // Version catalog (single source of truth)
│   └── wrapper/
└── app/
    ├── build.gradle.kts          // App module: SDK levels, dependencies
    ├── google-services.json      // Firebase project config
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml // Launcher activity + ML Kit meta-data
        │   ├── java/com/example/splitflat/
        │   │   ├── MainActivity.kt          // Single-activity entry point
        │   │   ├── model/
        │   │   │   └── Models.kt            // User, Group, Expense
        │   │   ├── navigation/
        │   │   │   └── AppNavigation.kt     // Route graph + auth gate
        │   │   ├── ui/
        │   │   │   ├── auth/               // LoginScreen, SignUpScreen
        │   │   │   ├── home/               // HomeScreen, CreateGroupScreen
        │   │   │   ├── group/              // GroupDetailScreen, AddExpenseScreen
        │   │   │   └── theme/              // Color, Theme, Type
        │   │   └── utils/
        │   │       ├── DebtSimplifier.kt     // Greedy netting algorithm
        │   │       └── QrCodeGenerator.kt    // ZXing QR → Bitmap
        │   └── res/                    // Icons, strings, themes, backup rules
        ├── test/                       // JVM unit tests
        └── androidTest/                // Instrumented tests

Single-Activity Pattern: The entire app runs inside MainActivity; every screen is a Composable destination managed by Navigation Compose. No fragments, no XML layouts.

---

## 6. Data Design

### 6.1 Firestore Collections

Firestore uses a flat, three-collection design. Document IDs are app-generated UUIDs (except `users`, which uses the Auth UID).

| Collection | Document ID | Purpose | Key Queries |
|---|---|---|---|
| `users` | Firebase Auth UID | Public user profile (name, email) | `whereEqualTo("email", x)` for invites/friend-add; `whereIn("uid", [...])` for member hydration |
| `groups` | UUID (client-generated) | Expense groups & 1-on-1 friend ledgers | `whereArrayContains("members", uid)` for home list; direct `document(id).get()` for QR join |
| `expenses` | UUID (client-generated) | Expenses *and* settlement records | `whereEqualTo("groupId", id)` with snapshot listener for real-time balance updates |

#### Entity-Relationship Overview

User (uid)
    ─── belongs to ───▶
    Group (id) &nbsp;[members: uid[]]
    ─── has many ───▶
    Expense (id) &nbsp;[groupId, paidBy, splits{uid: ₹}]
    
      1 User : N Groups (via members array)
      1 Group : N Expenses (via groupId)
      1 Expense : N Split entries (via splits map)

### 6.2 Data Models

#### User

```
data class User(
    val uid: String = "",      // Firebase Auth UID (document ID)
    val name: String = "",     // Display name
    val email: String = ""     // Login email (used for lookups)
)
```

#### Group

```
data class Group(
    val id: String = "",                       // UUID, matches document ID
    val name: String = "",                     // Group name ("Friendship" for ledgers)
    val createdBy: String = "",                // Creator's UID
    val members: List = emptyList(),   // Member UIDs (array-contains queryable)
    val simplifyDebts: Boolean = false,        // Enables Smart Debt Simplification
    val isOneOnOne: Boolean = false,           // true ⇒ 1-on-1 friend ledger
    val timestamp: Long = 0L                   // Creation epoch millis
)
```

#### Expense

```
data class Expense(
    val id: String = "",
    val groupId: String = "",                  // Parent group reference
    val title: String = "",                    // e.g. "Dinner", "Settle Up: A -> B"
    val amount: Double = 0.0,                  // Total amount (₹)
    val paidBy: String = "",                   // UID of payer
    val splitType: String = "EQUAL",           // "EQUAL" | "EXACT" | "PERCENTAGE"
    val category: String = "Other",            // "🍔 Food", "🚗 Transport", ...
    val splitAmong: List = emptyList(),// Legacy: participants of EQUAL splits
    val splits: Map = emptyMap(), // UID → amount owed (computed at save time)
    val timestamp: Long = 0L
)
```

Design note — denormalized splits: Split amounts are computed client-side at save time and stored as a materialized map (splits). This makes balance computation a pure fold over the expense list with no runtime division, and keeps reads cheap. The splitAmong list is retained only for legacy EQUAL-split compatibility.

### 6.3 Data Access Patterns

| Operation | Screen | Mechanism | Real-time? |
|---|---|---|---|
| Load my groups | Home | `groups.whereArrayContains("members", uid)` | ✅ Snapshot listener |
| Hydrate member names | Home, GroupDetail, AddExpense | `users.whereIn("uid", [...])` | ❌ One-shot get |
| Find user by email | Invite / Add Friend | `users.whereEqualTo("email", email)` | ❌ One-shot get |
| Load group | GroupDetail, AddExpense | `groups.document(id)` | ✅ Snapshot listener (GroupDetail) |
| Load expenses | GroupDetail | `expenses.whereEqualTo("groupId", id)` | ✅ Snapshot listener |
| Join via QR | Home (scanner) | `groups.document(scannedId).get()` then `update("members", +uid)` | ❌ One-shot |
| Toggle simplification | GroupDetail | `groups.document(id).update("simplifyDebts", bool)` | ✅ Via listener |

Known constraint: Firestore whereIn is limited to 10 values per query. Groups with more than 10 members will fail member hydration (users.whereIn("uid", members)). See §14 for the chunking fix on the roadmap.

---

## 7. Module Design

### 7.1 Authentication Module — `ui.auth`

#### LoginScreen.kt

| Aspect | Detail |
|---|---|
| State | `email`, `password`, `isLoading`, `errorMessage`, plus forgot-password dialog state (`resetEmail`, `isSendingReset`, `resetStatusMessage`, `isResetSuccess`) |
| Actions | `auth.signInWithEmailAndPassword(email, password)`; validation requires non-empty fields |
| Forgot Password | Dialog pre-filled with entered email → `auth.sendPasswordResetEmail()` with success/error status inline |
| Callbacks | `onLoginSuccess` → navigate Home (pop Login); `onNavigateToSignUp` |
| UX details | 16dp rounded inputs, 56dp CTA button, inline `CircularProgressIndicator` while loading, error text in `colorScheme.error` |

#### SignUpScreen.kt

| Aspect | Detail |
|---|---|
| State | `name`, `email`, `password`, `isLoading`, `errorMessage` |
| Flow | 1) `createUserWithEmailAndPassword` → 2) write profile doc `users/{uid}` with `uid`, `name`, `email` → 3) `onSignUpSuccess` |
| Failure modes | Auth failure → inline message; profile write failure → "Failed to save user data"; missing UID → proceed without profile (edge case) |

**Flow:** Validate fields → createUserWithEmailAndPassword → Write users/{uid} → Navigate Home

### 7.2 Navigation Module — `navigation.AppNavigation`

#### Route Graph

| Route | Screen | Arguments | Notes |
|---|---|---|---|
| `login` | LoginScreen | — | Start destination when unauthenticated |
| `signup` | SignUpScreen | — | Reached from Login |
| `home` | HomeScreen | — | Start destination when authenticated |
| `create_group` | CreateGroupScreen | — | — |
| `group_detail/{groupId}` | GroupDetailScreen | `groupId: String` | Helper `createRoute(groupId)` |
| `add_expense/{groupId}` | AddExpenseScreen | `groupId: String` | Create mode (`expenseId = null`) |
| `edit_expense/{groupId}/{expenseId}` | AddExpenseScreen | `groupId`, `expenseId` | Edit mode (same composable reused) |

#### Auth Gating & Back-Stack Behavior

- Start destination decided once at composition: `auth.currentUser != null ? Home : Login`.
- Login → Home uses `popUpTo(Login) { inclusive = true }` so Back does not return to auth.
- SignUp → Home pops the entire Login stack; SignUp → Login pops SignUp.
- Sign-out navigates to Login popping Home (inclusive).
- Create-group and add/edit-expense use `popBackStack()` to return.

### 7.3 Home Module — `ui.home.HomeScreen`

#### Responsibilities

- **Tabs:** "Groups" (regular groups) and "Friends" (1-on-1 ledgers), filtered client-side from a single listener via `isOneOnOne`.
- **Real-time data:** `LaunchedEffect` registers a snapshot listener on `groups.whereArrayContains("members", uid)`; member names hydrated with a secondary `users.whereIn` query.
- **Pull-to-refresh:** Material 3 `PullToRefreshBox` triggers `loadData()` with a 500 ms minimum spinner duration.
- **QR scanner entry point:** launches `GmsBarcodeScanning` client; on success, fetches the group, appends the current UID to `members` if absent, then navigates to Group Detail.
- **Add Friend dialog:** email lookup → duplicate/self checks → creates a `Group` with `isOneOnOne = true`, `name = "Friendship"`, members = [self, friend].
- **Overflow menu:** About dialog (developer, stack, version 1.0.0) and Sign Out.
- **Empty states:** contextual guidance per tab ("Tap the + button to create one!").

#### QR Join Flow

**Flow:** Tap QR icon → GmsBarcodeScanning.startScan() → Read rawValue (groupId) → Fetch group doc → Member? No → update members[] → Navigate GroupDetail

#### GroupCard composable

Renders group name (or friend display name for ledgers), member count / "1-on-1 Ledger" subtitle, 16dp rounded card with 2dp elevation, whole-card clickable.

### 7.4 Create Group Module — `ui.home.CreateGroupScreen`

| Aspect | Detail |
|---|---|
| Inputs | Group name (required), invite-by-email chips (local list, deduped), Smart Debt Simplification checkbox |
| Flow | Generate UUID → resolve invited emails to UIDs via `users.whereIn("email", list)` → write `Group` doc with creator as first member → `onGroupCreated` (popBackStack) |
| Validation | Blank group name rejected; duplicate emails ignored in UI list |
| Edge case | Empty invite list → group created with creator only; unregistered emails silently skipped (MVP behavior) |

### 7.5 Group Detail Module — `ui.group.GroupDetailScreen`

The functional core of the app. Composes:

| Section | Behavior |
|---|---|
| **Header** | Dynamic title (friend name for ledgers, group name otherwise); QR display button; invite button (hidden for 1-on-1 ledgers) |
| **Analytics** | `DonutChart` — Canvas-drawn ring of category totals with center total and legend (slices > 5% shown) |
| **Simplification toggle** | Switch bound to `groups/{id}.simplifyDebts`; listener re-renders suggestions instantly |
| **Balances panel** | Mode A (simplify ON): suggested transactions "X pays Y ₹Z". Mode B (OFF): raw per-member net balances (+green / −red) |
| **Expense list** | LazyColumn of `ExpenseCard` (title, payer, amount, overflow menu with Edit/Delete); delete removes Firestore doc directly |
| **Settle Up dialog** | Lists simplified debts; select one → enter amount → records a settlement Expense (see §8.3) |
| **Invite dialog** | Email lookup → membership checks → `update("members", +uid)` |
| **QR dialog** | Generates 512×512 QR of groupId via ZXing; shows group ID caption |
| **Data** | Snapshot listeners on group doc + expenses collection; balances recomputed on every emission (see §8.1) |

### 7.6 Add / Edit Expense Module — `ui.group.AddExpenseScreen`

| Aspect | Detail |
|---|---|
| Dual mode | Single composable; `expenseId == null` ⇒ create, else edit (pre-loads expense, reuses same document ID on save) |
| Inputs | Title, amount (₹), category chips (🍔 Food, 🚗 Transport, 🏠 Housing, 🛒 Groceries, 🎉 Fun, 📝 Other), split type tabs |
| Split modes | **EQUAL**: amount ÷ members. **EXACT**: per-member ₹ inputs, must sum to total (±0.01). **PERCENTAGE**: per-member % inputs, must sum to 100% (±0.01) |
| Validation | Title non-blank; amount > 0; group/members loaded; split-sum checks with animated error text |
| Persistence | Writes computed `splits` map + `splitAmong` (legacy) to `expenses/{docId}` |
| Edit pre-fill | EXACT values restored verbatim; PERCENTAGE back-computed as `(amount/total)×100` |

### 7.7 QR Utilities — `utils.QrCodeGenerator` + ML Kit Scanner

- **Generation:** ZXing `QRCodeWriter.encode(text, QR_CODE, 512, 512)` → per-pixel `Bitmap` (RGB_565, black/white); returns `null` on failure (rendered as error text).
- **Payload:** raw group UUID — scannable by any SplitFlat client.
- **Scanning:** `GmsBarcodeScanning.getClient(context).startScan()`; success callback receives `Barcode`, uses `rawValue` as group ID. No CAMERA permission needed (Play-Services-hosted UI). Manifest declares ML Kit dependency `barcode_ui`.

### 7.8 Theming — `ui.theme`

| Token | Light | Dark |
|---|---|---|
| primary / secondary | `#2A9D8F` MintGreen | `#2A9D8F` MintGreenDark |
| tertiary (accent) | `#E63946` ExpenseRed | `#E63946` ExpenseRed |
| background | `#F8F9FA` SoftBackground | `#121212` DarkBackground |
| surface | `#FFFFFF` SurfaceWhite | `#1E1E1E` DarkSurface |
| onBackground / onSurface | `#1D3557` TextPrimary | `#F1FAEE` TextPrimaryDark |

- `SplitFlatTheme` auto-switches on `isSystemInDarkTheme()`; dynamic color supported but **disabled by default** to preserve brand palette.
- Custom `Typography` from `Type.kt`; consistent 16dp corner radius language across inputs/buttons/cards.

---

## 8. Core Algorithms

### 8.1 Balance Computation

Executed on every expense snapshot emission (GroupDetailScreen). For each expense:

`balance[paidBy] += expense.amount            // payer is credited the full amount

if (expense.splits.isNotEmpty()) {
    expense.splits.forEach { (uid, owed) ->
        balance[uid] -= owed                  // each participant debited their share
    }
} else if (expense.splitAmong.isNotEmpty()) { // legacy EQUAL fallback
    val perHead = expense.amount / expense.splitAmong.size
    expense.splitAmong.forEach { uid -> balance[uid] -= perHead }
}`
  **Invariant:** `Σ balances == 0` (conservation of money) — every rupee credited to a payer is debited across participants.

### 8.2 Smart Debt Simplification — `utils.DebtSimplifier`

Greedy max-matching netting. Given net balances, produce the minimal set of direct transfers:

`fun simplifyDebts(balances: Map): List {
    // 1. Drop balances within ±0.01 (epsilon tolerance)
    val valid = balances.filterValues { abs(it) > 0.01 }

    // 2. Partition: debtors (negative) and creditors (positive), as absolute values
    val debtors   = valid.filterValues { it  0 }

    // 3. Greedy loop: pair the LARGEST debtor with the LARGEST creditor
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        val maxDebtor   = debtors.maxByOrNull { it.value }
        val maxCreditor = creditors.maxByOrNull { it.value }
        val amount = min(maxDebtor.value, maxCreditor.value)

        emit Transaction(from = maxDebtor, to = maxCreditor, amount)

        // 4. Reduce or remove both sides (residual 

  Worked Example
  
    StepDebtors (owe)Creditors (owed)Transaction emitted
    Input balancesA: −₹400, B: −₹100C: +₹300, D: +₹200—
    1A: ₹400C: ₹300**A → C ₹300**
    2A: ₹100, B: ₹100D: ₹200**A → D ₹100**
    3B: ₹100D: ₹100**B → D ₹100**
    Result3 transactions instead of up to 4 raw pairwise debts; all balances zero
  

  Properties
  
    PropertyValue
    ComplexityO(n²) worst case (n = participants); typically far fewer iterations
    CorrectnessAlways fully settles (Σ = 0 input ⇒ empty end state); amounts conserved
    Transaction bound≤ n − 1 transactions; greedy largest-first heuristic keeps counts low in practice
    Epsilon handling±₹0.01 treated as settled — absorbs floating-point residue
    PuritySide-effect-free object; trivially unit-testable
  

  8.3 Settlement Recording
  "Settle Up" converts a suggested transaction into a persisted record. The debtor pays the creditor; this is stored as a synthetic Expense engineered to net out both balances:

`Expense(
    title    = "Settle Up: {fromName} -> {toName}",
    amount   = enteredAmount,
    paidBy   = tx.fromUid,              // debtor "pays"...
    splitType= "EXACT",
    category = "📝 Other",
    splits   = mapOf(tx.toUid to amt)   // ...and creditor is debited the same amount
)`
  **Net effect:** payer +amt, recipient −amt ⇒ both original balances move toward zero by exactly `amt`. Partial settlements are supported (amount field is editable, defaults to full debt). The real-time expense listener recomputes balances and suggestions automatically.

---

## 9. UI / UX Design

### 9.1 Design Language

- **Minimalist fintech aesthetic:** mint-green primary, charcoal text, generous whitespace, soft surfaces.
- **Shape system:** 16dp rounded corners on all interactive containers; 12dp on list cards.
- **Iconography:** Material Icons Extended (QR scanner, QR code, person-add, more-vert, arrow-back).
- **Emoji category chips:** scannable at a glance without custom icon assets.
- **Currency:** INR (₹) formatted via `String.format(Locale.US, "%.2f", …)`.

### 9.2 Screen Inventory & States

| Screen | Loading | Empty | Error | Primary Action |
|---|---|---|---|---|
| Login | Button spinner | — | Inline red text | Log In |
| SignUp | Button spinner | — | Inline red text | Sign Up |
| Home | Center spinner + pull-refresh | Per-tab guidance copy | Listener silently stops loading | FAB (+ group / + friend) |
| CreateGroup | Button spinner | — | Inline red text | Create Group |
| GroupDetail | Full-screen spinner | "No expenses yet." | Listener fallback | FAB Add Expense |
| AddExpense | Button spinner | — | Animated error banner | Save Expense |

### 9.3 Interaction Patterns

- **Real-time-first:** all lists update live via Firestore listeners; pull-to-refresh is a supplementary manual sync.
- **Dialogs over routes:** invites, QR display, settle-up, about, and add-friend are Material 3 `AlertDialog`s — keeping the nav graph shallow.
- **Optimistic-free writes:** buttons disable + show spinners during async writes; no rollback needed.
- **Animated feedback:** `AnimatedVisibility` for split-mode panels and error banners; `fadeIn/expandVertically` transitions.
- **Edge-to-edge:** `enableEdgeToEdge()` with Scaffold padding handling insets.

---

## 10. Security & Privacy

### 10.1 Current Posture

| Control | Implementation | Status |
|---|---|---|
| Identity | Firebase Auth email/password; sessions persisted by Auth SDK | Active |
| Password reset | Out-of-band reset email flow | Active |
| Transport | All Firestore/Auth traffic over TLS | Active |
| Client-side guards | Self-friend check, duplicate member checks, duplicate group membership check on QR join | Active |
| Firestore Security Rules | Not present in repo — **must be configured in Firebase console** | Required |
| Backup rules | `backup_rules.xml` + `data_extraction_rules.xml` declared | Active |

Critical recommendation: Because balance logic runs client-side, Firestore Security Rules are the only server-side enforcement. Recommended baseline:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /users/{uid} {
      allow read: if request.auth != null;          // needed for email lookups
      allow write: if request.auth.uid == uid;      // self-profile only
    }
    match /groups/{gid} {
      allow read, update: if request.auth != null
        && request.auth.uid in resource.data.members;
      allow create: if request.auth != null
        && request.auth.uid == request.resource.data.createdBy;
    }
    match /expenses/{eid} {
      allow read, write: if request.auth != null;   // tighten to group membership
    }
  }
}
```

### 10.2 Privacy Considerations

- Stored PII: name + email (visible to any authenticated user by design, for email-based invites).
- No analytics, ads, or third-party trackers in v1.0.
- `google-services.json` is committed (standard for Android Firebase projects; contains project IDs, not secrets).

---

## 11. Error Handling & Resilience

| Category | Strategy | Example |
|---|---|---|
| Auth errors | Surface Firebase exception message inline | "Login failed" fallback if message null |
| Validation errors | Pre-flight checks before any write; animated inline banners | "Exact amounts must sum up to the total (₹X)" |
| Lookup failures | Explicit user-facing copy | "User not found!", "You are already friends!" |
| Write failures | Failure listeners reset loading state + show message | "Failed to create group", "Failed to record payment" |
| Listener errors | Snapshot error callbacks stop the spinner; last-good data retained | GroupDetail expense listener |
| QR failures | Null-safe bitmap handling; scanner failures silently ignored | "Failed to generate QR code" |
| Offline | Firestore local cache serves reads; writes queue automatically (SDK behavior) | Implicit |

Float hygiene: all monetary comparisons use a ±0.01 epsilon (split validation, simplifier, settlement residuals) to neutralize IEEE-754 rounding noise.

---

## 12. Testing Strategy

### 12.1 Current State

| Suite | Location | Runner | Coverage |
|---|---|---|---|
| Unit tests | `src/test/` | JUnit 4 | Placeholder (`ExampleUnitTest`) |
| Instrumented | `src/androidTest/` | AndroidJUnitRunner + Espresso | Placeholder (`ExampleInstrumentedTest`) |
| Compose UI tests | `androidTestImplementation(ui-test-junit4)` | ComposeTestRule | Dependencies wired, no tests yet |

### 12.2 Recommended Test Plan

#### Unit — `DebtSimplifierTest` (highest ROI, pure function)

```
// 1. Empty / all-zero balances ⇒ no transactions
// 2. Single debtor, single creditor ⇒ exactly one transaction
// 3. Worked example (A:-400, B:-100, C:+300, D:+200) ⇒ 3 txns, all zero after
// 4. Epsilon: balances of ±0.01 are ignored
// 5. Conservation: sum of transaction amounts == sum of positive balances
// 6. Idempotence of partitioning: no debtor ever appears as creditor
// 7. Large random fuzz: assert Σ(txns) settles Σ(balances) within 0.01×n
```

#### Unit — Balance computation

```
// Extract the fold from GroupDetailScreen into a testable function, then:
// - EQUAL expense across N members ⇒ each debited amount/N
// - EXACT splits honored verbatim
// - Legacy splitAmong fallback path
// - Settlement expense nets both parties to zero
```

#### Instrumented / Compose UI

- Login: empty-field validation, error rendering, success navigation (with Auth emulator).
- AddExpense: EXACT sum mismatch shows error; PERCENTAGE ≠ 100% blocked; save writes correct splits (Firestore emulator).
- GroupDetail: simplification toggle re-renders suggestions; settle-up dialog records payment.
- Use **Firebase Emulator Suite** (`auth` + `firestore`) for hermetic tests.

### 12.3 Manual QA Checklist (critical paths)

- Sign-up → profile doc created → lands on Home.
- Create group with 0 and with multiple invitees; verify member list.
- Add EQUAL / EXACT / PERCENTAGE expenses; verify balances on second device.
- Enable simplification; verify suggested transactions update live.
- Partial settle-up; verify balances shrink by the paid amount.
- QR: generate on device A → scan on device B → B becomes member.
- Add friend by email; ledger appears under Friends tab with friend's name.
- Sign out → relaunch → lands on Login; sign in → lands on Home.

---

## 13. Build & Deployment

### 13.1 Build Configuration

| Item | Value |
|---|---|
| Application ID | `com.example.splitflat` |
| Version | `versionCode 1`, `versionName "1.0"` (About dialog reports 1.0.0) |
| Build types | `release` only (debug implicit); `isMinifyEnabled = false` |
| Java compat | VERSION_11 source & target |
| Compose | `buildFeatures.compose = true` + Kotlin Compose compiler plugin |
| Dependency management | Gradle version catalog (`gradle/libs.versions.toml`) |
| Firebase wiring | `google-services` plugin 4.4.2 + `app/google-services.json` |

### 13.2 Commands

`./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (unsigned unless signing configured)
./gradlew test                 # JVM unit tests
./gradlew connectedAndroidTest # Instrumented tests`

  13.3 Release Checklist
  
    Configure release signing (`signingConfigs`) — currently absent.
    Enable R8/ProGuard (`isMinifyEnabled = true`) + rules for Firestore/ZXing.
    Verify Firestore Security Rules deployed (see §10.1).
    Bump `versionCode`/`versionName`; keep About dialog in sync.
    Run full manual QA checklist (§12.3) on minSdk 24 and targetSdk 36 devices.
  

  13.4 Source Control Workflow
  Feature Branch Workflow (Gitflow-like) per project README:

- `main` — production-ready only; no direct commits.
- `develop` — integration branch for completed features.
- `feature/*`, `bugfix/*` from `develop`; `hotfix/*` from `main` (merged back to both).
- Conventional commit prefixes: `feat:`, `fix:`, `refactor:`; imperative mood.

---

## 14. Limitations & Future Roadmap

### 14.1 Known Limitations (v1.0)

| # | Limitation | Impact | Proposed Fix |
|---|---|---|---|
| L-1 | Firestore `whereIn` 10-item cap on member hydration | Groups with > 10 members show "Unknown" names | Chunk queries into batches of 10 and merge results |
| L-2 | No ViewModel/Repository layer | Business logic coupled to Composables; poor testability | Introduce MVVM with `ViewModel` + `Repository` per screen |
| L-3 | Settlements appear in the expense list | Mixed UX of real expenses and payment records | Add `isSettlement` flag; filter or visually distinguish in UI |
| L-4 | Unregistered emails silently skipped on group creation | Inviter believes invite succeeded | Report unresolved emails before creating the group |
| L-5 | No Firestore Security Rules in repo | Data integrity depends on client behavior | Deploy rules from §10.1; add `firestore.rules` to VCS |
| L-6 | Single currency (INR), hardcoded ₹ | Not usable in other locales | Currency field on Group + locale-aware formatting |
| L-7 | Expense edit resets timestamp | Edited expenses jump to top of list | Preserve original `timestamp` in edit mode |
| L-8 | No member removal / group deletion | Groups are permanent once created | Leave-group flow + soft-delete with confirmation |
| L-9 | QR payload is a bare group UUID | Any QR containing a valid group ID grants membership | Signed/invite-token QR payloads with expiry |
| L-10 | Minification disabled, no signing config | Larger APK; unsigned release builds | Enable R8 + configure `signingConfigs` |

### 14.2 Future Roadmap

| Phase | Feature | Notes |
|---|---|---|
| v1.1 | Chunked member hydration (L-1) | Quick win, high impact |
| v1.1 | Settlement flag + filtered views (L-3) | Data migration: backfill flag on "Settle Up:" titles |
| v1.2 | MVVM refactor (L-2) | Unlock unit testing of screens |
| v1.2 | Leave group / delete group | Requires balance-zero guard for leavers |
| v1.3 | Multi-currency support | Per-group currency + conversion display |
| v1.3 | Push notifications (FCM) | New expense / invite / settlement events |
| v2.0 | Recurring expenses | Rent, subscriptions templates |
| v2.0 | Export (CSV / PDF) | Group summary reports |
| v2.0 | Google Sign-In | Reduce signup friction |

---

## 15. Appendix

### 15.1 Glossary of Firestore Field Names

| Field | Collection | Type | Meaning |
|---|---|---|---|
| `uid` | users | String | Auth user ID (mirrors document ID) |
| `name` | users | String | Display name |
| `email` | users | String | Login email; lookup key for invites |
| `members` | groups | Array | Member UIDs; queried with array-contains |
| `createdBy` | groups | String | Creator UID |
| `simplifyDebts` | groups | Boolean | Smart simplification toggle |
| `isOneOnOne` | groups | Boolean | Friend ledger marker |
| `groupId` | expenses | String | Parent group reference |
| `paidBy` | expenses | String | Payer UID |
| `splitType` | expenses | String | "EQUAL" | "EXACT" | "PERCENTAGE" |
| `splits` | expenses | Map | UID → amount owed (materialized) |
| `splitAmong` | expenses | Array | Legacy EQUAL-split participants |

### 15.2 Traceability Matrix — Feature → Code

| Feature | Primary File(s) | Key Functions/Composables |
|---|---|---|
| Email login + reset | `ui/auth/LoginScreen.kt` | `LoginScreen` |
| Registration | `ui/auth/SignUpScreen.kt` | `SignUpScreen` |
| Route graph + auth gate | `navigation/AppNavigation.kt` | `AppNavigation`, `Screen` |
| Groups/Friends tabs, QR scan, add friend | `ui/home/HomeScreen.kt` | `HomeScreen`, `GroupCard` |
| Group creation | `ui/home/CreateGroupScreen.kt` | `CreateGroupScreen` |
| Balances, settle-up, invite, analytics | `ui/group/GroupDetailScreen.kt` | `GroupDetailScreen`, `ExpenseCard`, `DonutChart` |
| Expense create/edit | `ui/group/AddExpenseScreen.kt` | `AddExpenseScreen` |
| Debt simplification | `utils/DebtSimplifier.kt` | `simplifyDebts`, `Transaction` |
| QR generation | `utils/QrCodeGenerator.kt` | `generateQrCode` |
| Theming | `ui/theme/*` | `SplitFlatTheme` |

### 15.3 References

- Firebase Auth — Email/Password (Android)
- Cloud Firestore Documentation
- Firestore `whereIn` limits
- ML Kit Barcode Scanning (Play Services)
- ZXing QR Code Library
- Navigation Compose
- Jetpack Compose

### 15.4 Document History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2026-08-28 | Anish Thakurta | Initial TDD covering full v1.0.0 codebase |

---

*SplitFlat — Technical Design Document · Version 1.0 · Generated 2026-08-28 · Developer: Anish Thakurta*
