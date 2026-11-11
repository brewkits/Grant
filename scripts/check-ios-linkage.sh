#!/usr/bin/env bash
# Checks which permission frameworks grant-core drags into a CONSUMER's iOS binary.
#
# Input: the release static framework built by :linkage-probe, which plays a camera-only app
# (Issue #91's reporter). The probe uses only AppGrant.CAMERA and ServiceType.CAMERA_HARDWARE.
#
#   ./gradlew :linkage-probe:linkReleaseFrameworkIosArm64
#   scripts/check-ios-linkage.sh linkage-probe/build/bin/iosArm64/releaseFramework/LinkageProbe.framework/LinkageProbe
#
# Three groups of assertions. Each is a hard failure:
#
#   CONTROL     Must be PRESENT. The probe really uses the camera, so AVFoundation must show up.
#               If it does not, the probe got stripped or the tool output changed, and every
#               "absent" check below would pass for no reason.
#   ISOLATED    Must be ABSENT. These live in opt-in modules (#25/#38/#45) that the probe does not
#               add. Before this script, that isolation was only ever verified by hand.
#   KNOWN_GAP   Must be PRESENT, for now. grant-core still links these (Issue #91). Pinned so the
#               detector is shown to find a real leak on every run. When a 3.0.0 PR isolates one,
#               this script fails until its entry moves from KNOWN_GAP to ISOLATED. That is
#               deliberate: the fix has to be recorded, not just happen.
#
# What is inspected (a static framework is an `ar` archive, so `otool -L` shows nothing useful):
#   class:<name>     `nm -u`: an undefined reference to _OBJC_CLASS_$_<name>
#   sel:<selector>   `strings`: an ObjC method-name string (what Apple's scanner reads)
#   fw:<Framework>   `otool -l`: an LC_LINKER_OPTION autolink "-framework <Framework>" record
set -euo pipefail

BIN="${1:?usage: $0 <path to LinkageProbe.framework/LinkageProbe>}"
[[ -f "$BIN" ]] || { echo "error: $BIN not found (build :linkage-probe:linkReleaseFrameworkIosArm64 first)" >&2; exit 2; }

CONTROL=(
  "class:AVCaptureDevice"
  "sel:requestAccessForMediaType:completionHandler:"
  "fw:AVFoundation"
)

ISOLATED=(
  "class:CNContactStore"        "fw:Contacts"                 # grant-contacts
  "class:EKEventStore"          "fw:EventKit"                 # grant-calendar
  "class:CMMotionActivityManager" "fw:CoreMotion"             # grant-motion
  "class:CBCentralManager"      "fw:CoreBluetooth"            # grant-bluetooth
  "class:ATTrackingManager"     "fw:AppTrackingTransparency"  # grant-tracking
  "sel:requestAlwaysAuthorization"                            # grant-location-always
)

# Issue #91. Move an entry to ISOLATED in the PR that removes it from grant-core.
KNOWN_GAP=(
  "class:CLLocationManager" "sel:requestWhenInUseAuthorization" "fw:CoreLocation"
  "class:PHPhotoLibrary"    "fw:Photos"
)

UNDEF_CLASSES="$(nm -u "$BIN" 2>/dev/null | sed -n 's/.*_OBJC_CLASS_\$_//p' | sort -u)"
STRINGS="$(strings -a "$BIN" | sort -u)"
FRAMEWORKS="$(otool -l "$BIN" | awk '/cmd LC_LINKER_OPTION/{lo=1;next} /cmd /{lo=0} lo && /string #2/{print $3}' | sort -u)"

present() {
  local kind="${1%%:*}" name="${1#*:}"
  case "$kind" in
    class) grep -qxF -- "$name" <<<"$UNDEF_CLASSES" ;;
    sel)   grep -qxF -- "$name" <<<"$STRINGS" ;;
    fw)    grep -qxF -- "$name" <<<"$FRAMEWORKS" ;;
    *)     echo "error: unknown check kind '$kind'" >&2; exit 2 ;;
  esac
}

failures=0
check() {  # check <expect present|absent> <group> <item>
  local expect="$1" group="$2" item="$3" got=absent
  present "$item" && got=present
  if [[ "$got" == "$expect" ]]; then
    printf '  ok    %-10s %-50s %s\n' "$group" "$item" "$got"
  else
    printf '  FAIL  %-10s %-50s expected %s, got %s\n' "$group" "$item" "$expect" "$got"
    failures=$((failures + 1))
  fi
}

echo "iOS linkage check: $BIN"
for i in "${CONTROL[@]}";   do check present CONTROL   "$i"; done
for i in "${ISOLATED[@]}";  do check absent  ISOLATED  "$i"; done
for i in "${KNOWN_GAP[@]}"; do check present KNOWN_GAP "$i"; done

if (( failures > 0 )); then
  cat >&2 <<'MSG'

FAILED. Read the group of each FAIL line:
  CONTROL    the probe no longer exercises the camera path; the other results mean nothing.
  ISOLATED   a framework that should live only in an opt-in module leaked back into grant-core.
  KNOWN_GAP  a framework is no longer linked. If you isolated it on purpose (Issue #91), move
             its entries from KNOWN_GAP to ISOLATED in this script.
MSG
  exit 1
fi
echo "All linkage assertions hold. (KNOWN_GAP entries are still linked; see Issue #91.)"
