# Halanoi Sovereign — Codebase Breakdown 🛠️

This document provides a technical overview of the source files in the **Halanoi Sovereign** codebase. It helps open-source developers understand the architecture and onboarding process for modifying or contributing to the project.

---

## 📐 Architecture Overview

Halanoi Sovereign operates as a multi-layered security system:
1.  **Device Policy Manager (DPC)**: Locks down system settings, prevents uninstallation, and provides app-hiding capabilities.
2.  **Accessibility Service**: Scrapes screen content and URLs, monitors app stores, and intercepts browser launches.
3.  **Local VPN**: Routes and filters network DNS queries at the system level.
4.  **Local AI Engine**: Runs offline inference on scraped text using TensorFlow Lite.

---

## 📂 Core Component & File Breakdown

### 1. Device Administration (System Lockdown)
*   **[`HalanoiDeviceAdminReceiver.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/HalanoiDeviceAdminReceiver.kt)**:
    Acts as the main Device Admin entry point. It receives administrative intents from the Android OS and implements policy controls:
    *   Locks Accessibility settings so they cannot be disabled.
    *   Greys out the system "Factory Reset" option.
    *   Prevents uninstallation and data clearing.

### 2. Screen Scraper & Package Interceptor
*   **[`HalanoiAccessibilityService.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/HalanoiAccessibilityService.kt)**:
    The background engine that monitors screen activity. It performs:
    *   **Text & URL Extraction**: Scrapes text nodes on the active screen and reads browser address bars.
    *   **Active App Store Scanning**: Monitors Play Store/Vivo App Store activities and triggers background packages sweeps.
    *   **Hydra Auto-Hide**: Detects unauthorized browser launches and calls the DPC to instantly hide and terminate them.
*   **[`BrowserHelper.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/BrowserHelper.kt)**:
    A helper utility that scans package names to identify web browsers:
    *   Runs targeted intent-queries to bypass package manager indexing latency.
    *   Holds the database of alternative browsers (Opera, Brave, Firefox, etc.) blocked in Standard Mode.

### 3. Local VPN & DNS filtering
*   **[`HalanoiVpnService.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/HalanoiVpnService.kt)**:
    Initializes the local Android VPN interface. It handles background service lifecycle management and routes network traffic.
*   **[`HalanoiVpnThread.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/HalanoiVpnThread.kt)**:
    A dedicated background thread that parses raw IP packets:
    *   Intercepts DNS queries (Port 53).
    *   Redirects queries to **Cloudflare Family DNS** (`1.1.1.3` / `1.0.0.3`) to filter out adult content at the network level.

### 4. Edge AI Classifier
*   **[`TFLiteClassifier.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/TFLiteClassifier.kt)**:
    Initializes the TensorFlow Lite interpreter.
    *   Loads the 64MB model (`halanoi_transformer.tflite`) from assets.
    *   Preprocesses text (tokenization, padding) and runs local sentence classification.
*   **[`TFLiteViewModel.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/TFLiteViewModel.kt)**:
    Provides reactive states to the UI, exposing prediction results and model status.

### 5. UI Dashboard & Boot Services
*   **[`MainActivity.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/MainActivity.kt)**:
    The Jetpack Compose dashboard where users configure Vault settings, manage blocked keywords, start the VPN service, and access the GitHub Star Call to Action.
*   **[`HalanoiBootReceiver.kt`](file:///d:/Halanoi_Fresh/HalanoiApp/app/src/main/java/com/halanoi/app/HalanoiBootReceiver.kt)**:
    A broadcast receiver that listens for `BOOT_COMPLETED`. It automatically restarts the local VPN and sweeps for unauthorized package installations as soon as the phone boots up.
