#!/bin/bash

# 🚀 iOS App Launcher for Android Studio
# This script is called by Android Studio run configuration

set -e

SIMULATOR="${1:-iPhone 16}"

echo "════════════════════════════════════════════════════"
echo "🎯 Running iOS App on: $SIMULATOR"
echo "════════════════════════════════════════════════════"
echo ""

# Step 1: Build Kotlin framework
echo "📦 [1/3] Building Kotlin framework..."
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64 --quiet --console=plain || {
    echo "❌ Failed to build Kotlin framework"
    exit 1
}
echo "✅ Kotlin framework built successfully"
echo ""

# Step 2: Build iOS app with Xcode
echo "🔨 [2/3] Building iOS app with Xcode..."
cd demo/iosApp/GrantDemo
xcodebuild \
  -project GrantDemo.xcodeproj \
  -scheme PermissionDemo \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,name=$SIMULATOR" \
  build \
  CODE_SIGNING_ALLOWED=NO 2>&1 | grep -E "BUILD|error|warning" || true

if [ ${PIPESTATUS[0]} -ne 0 ]; then
    echo "❌ Failed to build iOS app"
    exit 1
fi
echo "✅ iOS app built successfully"
echo ""

# Step 3: Launch on simulator
echo "🚀 [3/3] Launching app on $SIMULATOR..."

# Boot simulator if not running
xcrun simctl boot "$SIMULATOR" 2>/dev/null || true
sleep 2

# Open Simulator app
open -a Simulator 2>/dev/null || true

# Find app path
APP_PATH="$(find ~/Library/Developer/Xcode/DerivedData/GrantDemo-*/Build/Products/Debug-iphonesimulator/PermissionDemo.app -type d 2>/dev/null | head -1)"

if [ -z "$APP_PATH" ]; then
    echo "❌ Could not find built app"
    exit 1
fi

# Install and launch
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted dev.brewkits.grant.demo

echo ""
echo "════════════════════════════════════════════════════"
echo "✅ SUCCESS! App is running on $SIMULATOR"
echo "════════════════════════════════════════════════════"
