# Maven Central Bundle - Quick Checklist

**Version:** 2.2.0
**Last Updated:** 2026-01-29
**Status:** ✅ Tested and working

---

## Quick Start (3 Commands)

```bash
# 1. Update version in kmpworker/build.gradle.kts (2 places: line 16 and line 93)

# 2. Build
./gradlew clean
./gradlew :kmpworker:publishAllPublicationsToMavenCentralLocalRepository

# 3. Create bundle
chmod +x create-maven-bundle.sh
./create-maven-bundle.sh
```

---

## Pre-Upload Checklist

Trước khi upload lên Maven Central, kiểm tra:

### Bundle File
- [ ] File location: `maven-central-artifacts/v2.2.0/kmpworkmanager-v2.2.0-bundle.jar`
- [ ] Size: ~1.3 MB
- [ ] Total files: 156

### Critical Files
- [ ] **kotlin-tooling-metadata.json.asc** ← Quan trọng nhất!
- [ ] 24 GPG signatures (.asc) total
- [ ] 5 platforms (core, android, iosarm64, iosx64, iossimulatorarm64)

### No Errors
- [ ] Không có thư mục META-INF
- [ ] Tất cả signatures verified

---

## Upload Process

1. **Login:** https://central.sonatype.com/
2. **Navigate:** Publishing → Upload via Publisher Portal
3. **Upload:** kmpworkmanager-v2.2.0-bundle.jar
4. **Wait:** 2-10 minutes validation
5. **Verify:** https://central.sonatype.com/artifact/dev.brewkits/kmpworkmanager/2.2.0

---

## Common Issues & Fixes

### ❌ "Missing signature for file: kotlin-tooling-metadata.json"

**Fix:** Script đã include file .json trong find command. Nếu vẫn thiếu:

```bash
cd kmpworker/build/maven-central-staging/dev/brewkits/kmpworkmanager/2.2.0
gpg --batch --yes --pinentry-mode loopback --armor --detach-sign \
    kmpworkmanager-2.2.0-kotlin-tooling-metadata.json
```

### ❌ "Bundle contains META-INF"

**Fix:** Script đã dùng `zip` thay vì `jar` command. Verify:

```bash
unzip -l maven-central-artifacts/v2.2.0/kmpworkmanager-v2.2.0-bundle.jar | grep -i "meta-inf"
# Should return nothing
```

### ❌ Validation failed

**Check:**
```bash
# Count signatures (should be 24)
unzip -l maven-central-artifacts/v2.2.0/kmpworkmanager-v2.2.0-bundle.jar | grep "\.asc$" | wc -l

# Verify kotlin-tooling-metadata.json.asc
unzip -l maven-central-artifacts/v2.2.0/kmpworkmanager-v2.2.0-bundle.jar | grep "kotlin-tooling-metadata.json.asc"
```

---

## Files Summary

### Generated Files
```
maven-central-artifacts/
└── v2.2.0/
    ├── kmpworkmanager-v2.2.0-bundle.jar  ← Upload file này
    └── MAVEN_ARTIFACTS_v2.2.0.md         ← Documentation
```

### Documentation
- **Full Guide:** `HUONG_DAN_TAO_MAVEN_BUNDLE_V2.md`
- **Script:** `create-maven-bundle.sh`
- **This Checklist:** `MAVEN_BUNDLE_CHECKLIST.md`

---

## Verification Commands

### Quick Verify
```bash
VERSION="2.2.0"
BUNDLE="maven-central-artifacts/v${VERSION}/kmpworkmanager-v${VERSION}-bundle.jar"

# All-in-one check
echo "Checking bundle..."
echo -n "META-INF: " && (unzip -l "$BUNDLE" | grep -i "meta-inf" > /dev/null && echo "❌ FOUND" || echo "✅ NONE")
echo -n "Files: " && unzip -l "$BUNDLE" | tail -1 | awk '{print $2}'
echo -n "Signatures: " && unzip -l "$BUNDLE" | grep "\.asc$" | wc -l | xargs
echo -n "kotlin-tooling-metadata.json.asc: " && (unzip -l "$BUNDLE" | grep -q "kotlin-tooling-metadata.json.asc" && echo "✅ FOUND" || echo "❌ MISSING")
echo -n "Size: " && du -h "$BUNDLE" | awk '{print $1}'
```

### Expected Output
```
Checking bundle...
META-INF: ✅ NONE
Files: 156
Signatures: 24
kotlin-tooling-metadata.json.asc: ✅ FOUND
Size: 1.3M
```

---

## For Next Version (v2.3.0)

1. Update version:
   - `kmpworker/build.gradle.kts` line 16: `version = "2.3.0"`
   - `kmpworker/build.gradle.kts` line 93: `version = "2.3.0"`
   - `create-maven-bundle.sh` line 15: `VERSION="2.3.0"`

2. Run:
   ```bash
   ./gradlew clean
   ./gradlew :kmpworker:publishAllPublicationsToMavenCentralLocalRepository
   ./create-maven-bundle.sh
   ```

3. Upload: `maven-central-artifacts/v2.3.0/kmpworkmanager-v2.3.0-bundle.jar`

---

## Success Criteria

✅ **Bundle ready when:**
- Script completes without errors
- Bundle has 156 files
- Bundle has 24 signatures
- kotlin-tooling-metadata.json.asc exists
- No META-INF directory
- Bundle size ~1.3MB

✅ **Upload successful when:**
- Validation passes (2-10 minutes)
- Artifact appears on https://central.sonatype.com/
- Can download from Maven Central (15-30 minutes)

---

**Last successful bundle:** v2.2.0 (2026-01-29)
