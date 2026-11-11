#!/usr/bin/env bash
# Checks which permission frameworks Grant drags into a CONSUMER's iOS binary (Issue #91).
#
# Two probes, each a release static framework built by an unpublished module that plays an app:
#
#   camera  :linkage-probe       grant-core + grant-camera; uses CAMERA and CAMERA_HARDWARE only
#   core    :linkage-probe-core  grant-core alone; uses NOTIFICATION and LOCATION_GPS only
#
#   ./gradlew :linkage-probe:linkReleaseFrameworkIosArm64 :linkage-probe-core:linkReleaseFrameworkIosArm64
#   scripts/check-ios-linkage.sh camera linkage-probe/build/bin/iosArm64/releaseFramework/LinkageProbe.framework/LinkageProbe
#   scripts/check-ios-linkage.sh core   linkage-probe-core/build/bin/iosArm64/releaseFramework/LinkageProbeCore.framework/LinkageProbeCore
#
# Two groups of assertions per profile. Each is a hard failure:
#
#   CONTROL   Must be PRESENT: what the probe really uses. If it is missing, the probe got
#             stripped or the tool output changed, and every "absent" check would pass for no
#             reason.
#   ISOLATED  Must be ABSENT: every framework that lives in an opt-in module the probe does not
#             use. Until 3.0.0, CoreLocation and Photos (and, for the core probe, AVFoundation)
#             were linked by grant-core itself; this is what keeps them out.
#
# What is inspected (a static framework is an `ar` archive, so `otool -L` shows nothing useful):
#   class:<name>     `nm -u`: an undefined reference to _OBJC_CLASS_$_<name>
#   sel:<selector>   `strings`: an ObjC method-name string (what Apple's scanner reads)
#   fw:<Framework>   `otool -l`: an LC_LINKER_OPTION autolink "-framework <Framework>" record
set -euo pipefail

PROFILE="${1:?usage: $0 <camera|core> <path to probe framework binary>}"
BIN="${2:?usage: $0 <camera|core> <path to probe framework binary>}"
[[ -f "$BIN" ]] || { echo "error: $BIN not found (build the probe's linkReleaseFrameworkIosArm64 first)" >&2; exit 2; }

CAMERA=( "class:AVCaptureDevice" "sel:requestAccessForMediaType:completionHandler:" "fw:AVFoundation" )   # grant-camera
LOCATION=( "class:CLLocationManager" "sel:requestWhenInUseAuthorization" "fw:CoreLocation" )               # grant-location
GALLERY=( "class:PHPhotoLibrary" "fw:Photos" )                                                            # grant-gallery
OLDER_MODULES=(
  "class:CNContactStore"          "fw:Contacts"                 # grant-contacts
  "class:EKEventStore"            "fw:EventKit"                 # grant-calendar
  "class:CMMotionActivityManager" "fw:CoreMotion"               # grant-motion
  "class:CBCentralManager"        "fw:CoreBluetooth"            # grant-bluetooth
  "class:ATTrackingManager"       "fw:AppTrackingTransparency"  # grant-tracking
  "sel:requestAlwaysAuthorization"                              # grant-location-always
)

case "$PROFILE" in
  camera) CONTROL=( "${CAMERA[@]}" )
          ISOLATED=( "${LOCATION[@]}" "${GALLERY[@]}" "${OLDER_MODULES[@]}" ) ;;
  core)   CONTROL=( "class:UNUserNotificationCenter" "fw:UserNotifications" )
          ISOLATED=( "${CAMERA[@]}" "${LOCATION[@]}" "${GALLERY[@]}" "${OLDER_MODULES[@]}" ) ;;
  *)      echo "error: unknown profile '$PROFILE' (expected camera or core)" >&2; exit 2 ;;
esac

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

echo "iOS linkage check ($PROFILE): $BIN"
for i in "${CONTROL[@]}";  do check present CONTROL  "$i"; done
for i in "${ISOLATED[@]}"; do check absent  ISOLATED "$i"; done

if (( failures > 0 )); then
  cat >&2 <<'MSG'

FAILED. Read the group of each FAIL line:
  CONTROL   the probe no longer exercises what it claims to; the other results mean nothing.
  ISOLATED  a framework that should live only in an opt-in module is linked again. Look for a
            new direct reference from grant-core (even behind `by lazy`) to a handler that
            belongs in that module (Issue #91).
MSG
  exit 1
fi
echo "All linkage assertions hold."
