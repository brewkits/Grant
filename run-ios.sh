#!/bin/bash

# 🚀 iOS App Runner Script
# Run this script to build and launch iOS app on simulator

set -e

echo "🔨 Building Kotlin framework for iOS..."
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64

echo ""
echo "📱 Building iOS app with Xcode..."
cd demo/iosApp/GrantDemo

SIM_ID=$(xcrun simctl list devices available | grep "iPhone" | head -n 1 | grep -oE "[A-F0-9\-]{36}")

if [ -z "$SIM_ID" ]; then
    echo "❌ No iOS simulator found. Please install one via Xcode."
    exit 1
fi

echo "✅ Found iOS Simulator with ID: $SIM_ID"

xcodebuild \
  -project GrantDemo.xcodeproj \
  -scheme GrantDemo \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=$SIM_ID" \
  -derivedDataPath build \
  build

echo ""
echo "🚀 Installing app on simulator..."
APP_PATH="$(pwd)/build/Build/Products/Debug-iphonesimulator/GrantDemo.app"

# Boot simulator if not running
xcrun simctl boot "$SIM_ID" 2>/dev/null || true

# Install and launch app
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted dev.brewkits.GrantDemo

echo ""
echo "✅ iOS app is now running on simulator ($SIM_ID)!"
echo "📱 You can also open Simulator.app to see the app"
