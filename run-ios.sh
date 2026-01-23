#!/bin/bash

# 🚀 iOS App Runner Script
# Run this script to build and launch iOS app on simulator

set -e

echo "🔨 Building Kotlin framework for iOS..."
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64

echo ""
echo "📱 Building iOS app with Xcode..."
cd demo/iosApp/GrantDemo

xcodebuild \
  -project GrantDemo.xcodeproj \
  -scheme PermissionDemo \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
  build

echo ""
echo "🚀 Installing app on simulator..."
APP_PATH="$(pwd)/build/Build/Products/Debug-iphonesimulator/PermissionDemo.app"

# Boot simulator if not running
xcrun simctl boot "iPhone 16" 2>/dev/null || true

# Install and launch app
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted dev.brewkits.grant.demo

echo ""
echo "✅ iOS app is now running on iPhone 16 simulator!"
echo "📱 You can also open Simulator.app to see the app"
