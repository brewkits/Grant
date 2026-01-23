#!/bin/bash

# 🚀 Quick iOS Runner - One command to rule them all!
# Usage: ./ios-quick-run.sh [simulator-name]

set -e

SIMULATOR="${1:-iPhone 16}"

echo "🎯 Target simulator: $SIMULATOR"
echo ""

# Step 1: Build framework
echo "📦 Step 1/3: Building Kotlin framework..."
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64 --quiet

# Step 2: Build iOS app
echo "🔨 Step 2/3: Building iOS app..."
cd demo/iosApp/GrantDemo
xcodebuild \
  -project GrantDemo.xcodeproj \
  -scheme PermissionDemo \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,name=$SIMULATOR" \
  build \
  CODE_SIGNING_ALLOWED=NO \
  > /dev/null 2>&1

# Step 3: Run on simulator
echo "🚀 Step 3/3: Launching on simulator..."

# Boot simulator
xcrun simctl boot "$SIMULATOR" 2>/dev/null || true
sleep 2

# Open Simulator app
open -a Simulator

# Install and launch
APP_PATH="$(find ~/Library/Developer/Xcode/DerivedData/GrantDemo-*/Build/Products/Debug-iphonesimulator/PermissionDemo.app -type d 2>/dev/null | head -1)"

if [ -n "$APP_PATH" ]; then
    xcrun simctl install booted "$APP_PATH"
    xcrun simctl launch booted dev.brewkits.grant.demo
    echo ""
    echo "✅ SUCCESS! App is running on $SIMULATOR"
else
    echo "❌ ERROR: Could not find built app"
    exit 1
fi
