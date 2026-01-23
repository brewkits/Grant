#!/bin/bash

# 📱 List all available iOS simulators

echo "📱 Available iOS Simulators:"
echo ""
xcrun simctl list devices available | grep -E "iPhone|iPad" | grep -v "unavailable"
echo ""
echo "💡 Usage: ./ios-quick-run.sh \"iPhone 16 Pro\""
