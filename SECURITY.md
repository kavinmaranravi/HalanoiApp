# Security & Privacy Policy 🛡️

Halanoi Sovereign is built on the principle of **zero trust** and **offline-first** operation. Because the application requests high-level OS privileges (Device Owner and Accessibility Service), we believe in absolute transparency.

## 🔒 Privacy Guarantees

*   **100% On-Device AI**: All screen analysis, text classification, and keyword matching performed by the TFLite model run locally on your phone. No screen content, text, or keystrokes are ever sent to any remote servers.
*   **Local VPN Routing**: The Network Shield creates a local loopback VPN connection. It does not route your internet traffic through external proxy servers. Your web data remains entirely on your device.
*   **No Analytics or Trackers**: The application does not contain any third-party analytics (like Google Analytics, Mixpanel, or Facebook SDK) or crash-reporting libraries that send usage statistics.
*   **Self-Hosted Firebase (Optional)**: If configured, database storage is only used to sync your own personal study sessions. You can choose to compile the app without Firebase if you prefer.

## 🛠️ Security Audits

Because the source code is public, we invite developers, security researchers, and privacy advocates to audit our codebase:
*   Verify the absence of outgoing HTTP trackers or network logs.
*   Audit the [HalanoiAccessibilityService](app/src/main/java/com/halanoi/app/HalanoiAccessibilityService.kt) to confirm text classification is processed locally.

## ✉️ Reporting Vulnerabilities

If you discover a security vulnerability within this project, please open a confidential issue or contact the repository maintainers directly.
