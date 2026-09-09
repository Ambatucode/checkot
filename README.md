# 🧼 Checkot — AI-Powered Car Wash MIS & Booking Platform

![Version](https://img.shields.io/badge/version-5.6-00E6C3?style=for-the-badge&logo=android)
![Build](https://img.shields.io/badge/build-47-555555?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.06-4285F4?style=for-the-badge&logo=jetpackcompose)
![Firebase](https://img.shields.io/badge/Firebase-Cloud%20Functions%20v2%20%2B%20Firestore-FFCA28?style=for-the-badge&logo=firebase)
![Gemini AI](https://img.shields.io/badge/Gemini%20AI-3.1%20Flash%20Lite-8E75B5?style=for-the-badge&logo=googlegemini)
![Min SDK](https://img.shields.io/badge/minSdk-26-green?style=for-the-badge&logo=android)
![Target SDK](https://img.shields.io/badge/targetSdk-36-green?style=for-the-badge&logo=android)

**Checkot** is a full-stack Management Information System (MIS) and automated booking platform for car wash businesses, built natively for Android. It connects vehicle owners with car wash shops through real-time queue management, AI-driven vehicle cleanliness diagnosis, transactional bay scheduling, and comprehensive revenue analytics.

---

## 🌟 Key Features

### 🚘 For Customers
- **Shop Discovery**: Browse nearby approved car wash shops on an interactive Google Map. Shops display their logo, address, and a "From ₱X" minimum price badge.
- **Service Booking**: Select wash services (Exterior Wash, Underwash, Wax, Interior Vacuum, Tire Shine, Engine Wash, or custom owner-created services), pick a vehicle size (S/M/L/XL/XXL), choose a time slot, and confirm — all in a single flow.
- **Real-Time Queue & Live Wash Timer**: Live queue position tracking with dynamic wait-time estimation across multiple bays. An animated live HH:MM:SS timer counts down while a booking is In Progress.
- **🤖 AI Vehicle Cleanliness Check**: Upload or capture a photo of your car; the `checkCar` Cloud Function relays it to **Gemini 3.1 Flash Lite** and returns a verdict (`Clean`, `Lightly dirty`, `Needs a wash`, `Not a car`, `Photo unclear`, `Out of scope`), a friendly one-sentence reason, and a `dirtyArea` (`Exterior`, `Interior`, `Both`, `None`) that drives contextual shop recommendations.
- **AI Usage Tracking**: Daily limit of 10 checks per user, enforced server-side. A quota indicator shows remaining checks before and after each analysis.
- **Vehicle Manager**: Save up to 3 vehicles per account (plate number, brand/model, color, size). Set a default car for 1-tap booking.
- **Booking History**: Paginated booking history (cursor-based, 15 per page) with a booking details screen showing service summary, price, add-ons, assigned staff, and payment status.
- **Paid Add-Ons**: Customers can add extra services to a `CONFIRMED` or `IN_PROGRESS` booking; these bump the total price but do not change the reserved bay window.
- **Post-Service Reviews**: Leave a 1–5 star rating and comment on any completed booking (one review per booking, stored at `reviews/{bookingId}`).
- **Passwordless Authentication & Contact Management**:
  - 1-Click Google Sign-In (via Credential Manager API 1.3.0)
  - Frictionless Profile Phone Management (Direct phone update without SMS OTP delays or Play Integrity gates)
  - Active Queue Lock Guardrail (Phone number updates are locked while a user has a pending or in-progress booking)
  - Biometric Authentication (Fingerprint / Face Unlock) via `BiometricPrompt` for sensitive actions

### 🏪 For Shop Owners
- **Owner Dashboard**: Tabbed interface covering Bookings, Revenue, Services, Customers, and Settings.
- **Live Queue Control**: Real-time booking list with status filter chips (Pending, Confirmed, In Progress, Completed, Cancelled). Advance bookings through the lifecycle (`PENDING → CONFIRMED → IN_PROGRESS → COMPLETED`), assign a staff member when starting, mark payment received, and cancel.
- **Add-On Management**: Append paid add-ons to active bookings (price-only; no bay re-reservation needed).
- **Master Shop Availability Toggle**: Instantly flip the shop between **OPEN** and **CLOSED** from the top bar. Closing is enforced server-side — `createBooking` rejects new bookings while `isClosed` is true.
- **Revenue Analytics**: Daily, weekly, and monthly income totals and customer metrics calculated from completed bookings. Calendar-based boundaries (since midnight / start of week / 1st of month).
- **Service Configurator**: Add, edit, or remove services. For each service configure: custom display name, per-size pricing (S/M/L/XL/XXL), per-size durations (20 min–10 hr), a customer-facing description, and per-date unavailability blackouts.
- **Operating Hours**: Set global open/close times (minutes-since-midnight) and one-off per-date overrides (e.g. closing early for a holiday).
- **Closed Date Blackouts**: Mark specific dates fully closed so clients cannot book them.
- **Shop Profile**: Upload a logo and a wide banner image (Firebase Storage); both are displayed on the customer booking screen.
- **Bay Count**: Configure 1–10 simultaneous wash bays. The scheduler uses this to compute parallel slot availability.
- **Staff List**: Maintain a list of staff names for assignment at service start (display-only; does not gate capacity).
- **Shop Location**: Set and update the shop's pin on an interactive Google Map.
- **Floating Island Navigation UI**: Edge-to-edge floating navigation pills and a conditional slide-up action dock on setup tabs.

### 🛡️ For Super Administrators
- **Admin Dashboard**: Tabbed view of Pending, Active, and Rejected shops.
- **Shop Approval Workflow**: Review shop details (name, address, map location, logo, owner email/phone), then approve or reject. Approve/reject actions require **Biometric confirmation**.
- **Review Moderation**: View all customer reviews and delete inappropriate ones.
- **Push Notifications**: Admins receive FCM pushes when a new shop registers. Owners receive FCM pushes when their shop is approved or rejected.

---

## 🏗️ Architecture & Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.0 (JVM target 17) |
| **UI** | Jetpack Compose (BOM 2026.06.01), Material 3, `material-icons-extended` |
| **Architecture** | MVVM (Model–View–ViewModel) + `StateFlow` / `collectAsState` |
| **Navigation** | Navigation Compose 2.9.8, `sealed class Screen` routes |
| **Database** | Firebase Cloud Firestore (real-time listeners + cursor-based pagination) |
| **Backend** | Firebase Cloud Functions v2 (Node.js Callable Functions, region `asia-southeast1`) |
| **Authentication** | Firebase Auth — Google Sign-In (Credential Manager 1.3.0), Phone SMS OTP, Biometric (`BiometricPrompt` 1.1.0) |
| **AI Integration** | Google Gemini AI (`gemini-3.1-flash-lite`) via `checkCar` Callable Cloud Function; API key stored in GCP Secret Manager |
| **Push Notifications** | Firebase Cloud Messaging (FCM) + Android Notification Channels (`checkot_bookings`) |
| **Maps & Location** | Google Maps SDK for Android 19.0.0, `maps-compose` 6.4.1, Fused Location Provider 21.3.0 |
| **Image Loading** | Coil 2.7.0 (`AsyncImage` for shop logos/banners) |
| **Async** | Kotlin Coroutines & Flow, `kotlinx-coroutines-play-services` (`.await()` on Firebase Tasks) |
| **Splash Screen** | `androidx.core:core-splashscreen` |
| **Min / Target SDK** | API 26 (Android 8.0) / API 36 |

### Cloud Functions (`functions/index.js`)

| Function | Trigger | Purpose |
| :--- | :--- | :--- |
| `checkCar` | Callable | Relay a car photo (base64) to Gemini AI; enforce daily quota (10/user); return `{ verdict, reason, dirtyArea, remaining, dailyLimit }` |
| `getCarCheckUsage` | Callable | Return remaining daily AI checks for the caller without spending a Gemini call |
| `createBooking` | Callable | Transactionally validate slot availability, compute price & duration from shop config, reserve a free bay in `day_slots`, write the booking document, and notify the owner via FCM |
| `sendPushNotification` | Callable | Send an FCM message to any device token (used by owner/admin notification flows) |
| `onBookingStatusUpdated` | Firestore trigger on `bookings/{bookingId}` | On `CANCELLED` or `COMPLETED`, clean up stale entries from the `day_slots` ledger |
| `syncLedger` | Callable | Manually trigger ledger cleanup for a given `shopId` + `bookingDate` |

### Firestore Collections

| Collection | Purpose |
| :--- | :--- |
| `users/{uid}` | User profiles — role (`customer`/`owner`/`admin`), saved cars, FCM token, phone verification state |
| `shop_services/{shopId}` | Shop configuration — services, per-size pricing, hours, bay count, logo/banner URLs, `isClosed` toggle, approval `status` |
| `bookings/{bookingId}` | Booking records — full lifecycle, services, price, add-ons, timestamps, payment status, assigned staff |
| `day_slots/{shopId}_{date}` | Per-shop-per-day bay reservation ledger — used inside Firestore transactions for atomic check-and-reserve |
| `reviews/{bookingId}` | Shop reviews — one per completed booking, 1–5 stars + comment |
| `ai_usage/{uid}_{day}` | Daily Gemini AI usage counter — written and read only by Cloud Functions; denied to all clients |

### ViewModels

| ViewModel | Responsibility |
| :--- | :--- |
| `AuthViewModel` | Firebase Auth (Google Sign-In, Phone SMS OTP), role loading & RBAC gate, phone verification state machine, FCM token upload, demo mode auto-sign-in |
| `BookingViewModel` | Real-time user booking listener, time slot availability, booking creation via `createBooking` CF, cancellation, cursor-based pagination |
| `OwnerDashboardViewModel` | Live booking stream for the owner's shop, status transitions, add-on management, staff assignment, payment confirmation, shop analytics |
| `CarViewModel` | CRUD for a user's saved vehicles (up to 3 per account) |
| `ProfileViewModel` | Profile read/update, shop configuration updates (logo, banner, hours, closed dates, bay count, staff, location) |
| `SuperAdminViewModel` | Pending/active/rejected shop lists, approve/reject actions, review management |

---

## 🔒 Security Architecture

1. **Secret Isolation**: The Gemini API key lives only in **GCP Secret Manager** (`GEMINI_KEY`). It is read at runtime by the Cloud Function — never embedded in the APK or source code.
2. **Server-Side Booking**: Price calculation, bay reservation, shop-closed validation, and duplicate-car prevention all run inside a **Firestore transaction in a Cloud Function**. The client cannot bypass or forge any of this logic.
3. **Server-Side AI Quota**: The 10-check daily limit is enforced in the Cloud Function. Timed-out or failed Gemini calls do not consume the user's quota. The `ai_usage` collection is denied to all clients by Firestore rules.
4. **Role Immutability**: `users/{uid}` Firestore rules block changes to `role`, `userId`, and `ownedShopId` — customers cannot escalate privileges to owner or admin.
5. **Shop Isolation**: Owners can only write to `shop_services/{shopId}` for their own `ownedShopId`, and cannot change `status` (only admins can approve/reject).
6. **Booking Integrity**: Customers can only create bookings as themselves (`PENDING`). Cancellations are restricted to `PENDING`/`CONFIRMED` and may only touch `status` + `cancelledAt`. Add-ons may only increase `price` and `addOns`. Hard deletes are disabled for all clients.
7. **Biometric Gate on Admin Actions**: Approving or rejecting a shop requires a successful `BiometricPrompt` challenge in the Admin Dashboard.
8. **FCM Token Isolation**: All push notifications are sent via the `sendPushNotification` Cloud Function; token routing is never exposed to the client.

---

## 📦 Project Structure

```
checkot/
├── app/
│   ├── build.gradle.kts          # Dependencies, build config, secret injection
│   └── src/main/java/com/app/checkot/
│       ├── model/                # Data classes & enums (CarWashUser, Booking, ServiceType,
│       │                         #   CarWashShop, ShopCustomization, Review, DaySlotLedger, …)
│       ├── navigation/           # NavHost, sealed Screen routes
│       ├── service/              # BookingLedgerService, MyFirebaseMessagingService,
│       │                         #   NotificationHelper
│       ├── ui/
│       │   ├── components/       # Reusable Composables (FloatingNavBars, LiveWashTimerCard,
│       │   │                     #   ShopLogo, SkeletonBox shimmer, TypewriterText,
│       │   │                     #   MapComposables, AppButton, …)
│       │   ├── screens/          # One file per screen/tab (HomeScreen, BookServiceScreen,
│       │   │                     #   CheckCarScreen, OwnerDashboard + 5 tabs, AdminDashboard,
│       │   │                     #   BookingDetailsScreen, MyCarsScreen, ProfileScreen, …)
│       │   └── theme/            # Color, Typography, Theme
│       ├── utils/                # BiometricAuth, BookingUtils, ConnectivityObserver, DateUtils
│       └── viewmodel/            # AuthViewModel, BookingViewModel, CarViewModel,
│                                 #   OwnerDashboardViewModel, ProfileViewModel,
│                                 #   SuperAdminViewModel
├── functions/
│   └── index.js                  # All Cloud Functions (checkCar, createBooking, syncLedger, …)
├── firestore.rules               # Firestore security rules
├── storage.rules                 # Firebase Storage security rules
└── firebase.json                 # Firebase project config
```

---

## 🚀 Quick Start & Developer Setup

### Prerequisites
- **Android Studio** Ladybug 2024.2+ (or newer)
- **JDK 17** or higher
- Android SDK 36
- A Firebase project configured for this app

### Setup Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Ambatucode/checkot.git
   cd checkot
   ```

2. **Add Firebase credentials**:
   Obtain `google-services.json` from the project administrator and place it inside the `app/` folder:
   ```
   checkot/app/google-services.json
   ```

3. **Configure Maps API Key** *(optional — the app compiles and runs without it; maps render blank tiles)*:
   Add to `local.properties` in the project root:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key
   ```

4. **Demo mode** *(optional — auto-signs-in and skips the login/signup UI)*:
   Add credentials to `local.properties`. Owner credentials win if both blocks are present:
   ```properties
   # Demo customer
   DEMO_EMAIL=customer@example.com
   DEMO_PASSWORD=secret

   # Demo owner (uncomment and comment out the customer block above)
   # DEMO_OWNER_EMAIL=owner@example.com
   # DEMO_OWNER_PASSWORD=secret
   ```

5. **Sync & Run**:
   - Open the `checkot` folder in Android Studio.
   - Let Gradle sync (first run downloads dependencies).
   - Click **Run (▶)** (`Shift + F10`) on an emulator or physical device (Android 8.0+).

### Deploying Cloud Functions
```bash
cd functions
npm install
firebase deploy --only functions
```

### Deploying Firestore/Storage Rules
```bash
firebase deploy --only firestore:rules,storage
```

---

## 📱 Version History

| Version | Build | Highlights |
| :--- | :--- | :--- |
| **v5.1** | **42** | *Current* — Cursor-based booking history pagination, `QueuePositionLoadingCard` shimmer, "View All" button on Home, `day_slots` write permission fix, automatic ledger cleanup trigger on booking status change, multi-bay queue position fix |
| **v5.0** | 41 | Master Shop Availability Toggle (`isClosed`), Edge-to-Edge system insets, standardised Floating Island UI, Conditional Floating Action Dock, production build clean-up |
| **v4.9** | 40 | Floating Dock Action Bar for Owner setup tabs, revenue analytics optimisations |
| **v4.8** | 39 | Per-car booking validation, server-side transaction locks, thread-safe date utilities, Gemini AI vehicle diagnostic integration |

---

## 📄 License

This repository is maintained for the **Checkot Car Wash Management Information System (MIS)** project. All rights reserved.
