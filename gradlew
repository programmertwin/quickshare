name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Show workspace (debug)
        run: |
          pwd
          ls -la
          echo "---- settings.gradle ----"
          sed -n '1,200p' settings.gradle || true

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      # نصب Android SDK + cmdline-tools (sdkmanager) به‌صورت استاندارد
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      # نصب پکیج‌های لازم برای compileSdk 34
      - name: Install required SDK packages
        run: |
          sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
          yes | sdkmanager --licenses

      # اطمینان از LF و مجوز اجرا برای gradlew
      - name: Normalize EOL for gradlew (fix CRLF)
        run: sed -i 's/\r$//' gradlew

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew --stacktrace --info assembleDebug

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: QuickShare-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk

      - name: Build Release APK
        run: ./gradlew --stacktrace --info assembleRelease

      - name: Upload Release APK
        uses: actions/upload-artifact@v4
        with:
          name: QuickShare-release-apk
          path: app/build/outputs/apk/release/app-release.apk
