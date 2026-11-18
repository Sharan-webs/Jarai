# Kitagawh!

Jarvis-style Android assistant. Kotlin + Jetpack Compose. Package: com.kitagawh.app

## Build (GitHub Actions)
1. Create a GitHub repo and push this folder (the workflow is in .github/workflows/build.yml).
2. Open the Actions tab, run "Build Kitagawh APK" (or just push).
3. Download the artifact Kitagawh-debug-apk and install the APK.

## First run
1. Grant every permission on the Permissions page. Accessibility on Android 13+: App info, three dots menu, Allow restricted settings, then enable Kitagawh!.
2. Settings (gear, top left of Home) > API > paste your Groq key (console.groq.com/keys).
3. Start AI > Power on Jarvis. Say "Jarvis".

## Keep Accessibility locked on (optional)
Either enable Master access (Shizuku) on the Permissions page, or run once from a PC:
    adb shell pm grant com.kitagawh.app android.permission.WRITE_SECURE_SETTINGS
After that the app re-enables its own Accessibility service the moment it is switched off.

## Models (change in Groq.kt if Groq renames them)
Brain: llama-3.3-70b-versatile
Screen search (vision): meta-llama/llama-4-scout-17b-16e-instruct
