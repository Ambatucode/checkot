# 🧼 Checkot — AI-Powered Car Wash MIS & Booking Platform

![Version](https://img.shields.io/badge/version-5.0-00E6C3?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7-4285F4?style=for-the-badge&logo=jetpackcompose)
![Firebase](https://img.shields.io/badge/Firebase-Cloud%20Functions%20%2B%20Firestore-FFCA28?style=for-the-badge&logo=firebase)
![Gemini AI](https://img.shields.io/badge/Gemini%20AI-3.1%20Flash%20Lite-8E75B5?style=for-the-badge&logo=googlegemini)

**Checkot** is a modern, full-stack Management Information System (MIS) and automated booking mobile platform for car wash businesses. Built natively for Android using Jetpack Compose, Kotlin, and Firebase, Checkot connects vehicle owners with car wash shops through real-time queue management, AI-driven cleanliness diagnosis, dynamic wait-time estimation, and comprehensive revenue analytics.

---

## 🌟 Key Features

### 🚘 For Customers
- **Automated Service Booking**: Browse nearby approved car wash shops on an interactive Google Map, select vehicle size (S / M / L / XL / XXL), and pick customized wash packages.
- **Real-Time Queue & Wait Time**: Live queue position tracking with dynamic wait-time estimation based on active parallel wash bays.
- **🤖 AI Vehicle Cleanliness Check**: Instant vehicle inspection powered by **Google Gemini AI** to assess dirt level, identify dirty zones (Exterior, Interior, Both), and provide clean/dirty verdicts.
- **Vehicle Manager**: Save multiple personal vehicles for 1-tap bookings.
- **Biometric & SMS Auth**: Secure login via Biometric authentication (Fingerprint / Face Unlock) and Phone OTP verification.

### 🏪 For Shop Owners
- **Owner Dashboard**: Unified tabbed interface for complete business operations.
- **Live Queue Control**: Track, accept, progress (`Pending` $\rightarrow$ `Confirmed` $\rightarrow$ `In Progress` $\rightarrow$ `Completed`), or cancel bookings in real time.
- **Master Shop Availability Toggle**: Instantly switch shop state between **OPEN** and **CLOSED** to pause incoming bookings with real-time UI & server-side transaction locks.
- **Revenue Analytics**: Track daily, weekly, and monthly income stats alongside customer metrics.
- **Service & Pricing Customizer**: Configure custom wash packages, pricing by vehicle size, service durations, operating hours, and closed dates.
- **Floating Island Navigation UI**: Modern edge-to-edge floating navigation pills and conditional slide-up action docks.

### 🛡️ For Super Administrators
- **Admin Dashboard**: Review new shop registration applications, verify shop requirements (phone, map location, profile), and approve or reject shop accounts.
- **Platform Analytics**: System-wide revenue, active booking counts, and user management.

---

## 🏗️ Architecture & Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Mobile Client** | Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coroutines & Flow |
| **Architecture** | MVVM (Model-View-ViewModel) + StateFlow |
| **Database** | Firebase Cloud Firestore (Real-time synchronization) |
| **Backend & Cloud** | Firebase Cloud Functions (v2 Node.js Callable Functions) |
| **Authentication** | Firebase Auth (Phone MFA + Email/Password) + Biometric Prompt |
| **AI Integration** | Google Gemini AI (`gemini-3.1-flash-lite`) via GCP Secret Manager |
| **Notifications** | Firebase Cloud Messaging (FCM) + Android Channels |
| **Maps & Location** | Google Maps SDK for Android |

---

## 🔒 Security Architecture

Checkot enforces enterprise-grade security standards to protect users and backend resources:

1. **Secret Isolation**: Sensitive API keys (e.g. Gemini AI Key) live strictly inside **GCP Secret Manager** and are executed exclusively on server-side Cloud Functions. No keys are embedded in the Android APK.
2. **Server-Side Quotas & Rate Limits**: AI vehicle inspections are capped at **10 checks per user per day** in Cloud Functions to eliminate budget abuse.
3. **Privilege Escalation Protection**: `firestore.rules` enforces immutable user roles (`customer`, `owner`, `admin`). Customers cannot elevate privileges or modify shop IDs.
4. **Data Isolation**: Shop owners are restricted to reading/writing data strictly belonging to their assigned shop (`ownsShop(shopId)`).

---

## 🚀 Quick Start & Developer Setup

Setting up the project on a new developer machine takes **less than 5 minutes**:

### Prerequisites
- **Android Studio** (Ladybug 2024.2+ recommended)
- **JDK 17** or higher
- Android SDK 36

### Setup Steps

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/Ambatucode/checkot.git
   cd checkot
   ```

2. **Add Firebase Credentials**:
   Obtain `google-services.json` from the project administrator and place it inside the `app/` folder:
   ```text
   checkot/app/google-services.json
   ```

3. **Optional — Configure Maps API Key**:
   Create a `local.properties` file in the project root to enable Google Maps tile rendering:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key
   ```
   *(Note: The app compiles and runs cleanly even without this key; maps will simply render blank tiles).*

4. **Sync & Run**:
   - Open the `checkot` directory in **Android Studio**.
   - Let Gradle perform the initial sync.
   - Click **Run (▶)** (`Shift + F10`) on an emulator or physical Android device!

---

## 📱 Version History

- **v5.0 (Build 41)** — *Current Release*: Master Shop Availability Toggle, Edge-to-Edge System Insets, Standardized Floating Island UI, Conditional Floating Action Dock, Owner Dashboard banner alignment fix, and clean production repository build.
- **v4.9 (Build 40)** — Floating Dock Action Bar for Owner setup tabs & revenue analytics optimizations.
- **v4.8 (Build 39)** — Per-car booking validation, server-side transaction locks, thread-safe date utilities, and Gemini AI vehicle diagnostic integration.

---

## 📄 License

This repository is maintained for the **Checkot Car Wash Management Information System (MIS)** project. All rights reserved.
