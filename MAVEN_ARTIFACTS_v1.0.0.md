# KRelay v1.0.0 - Maven Central Artifacts Summary

**Version**: 1.0.0
**Group ID**: dev.brewkits
**Artifact ID**: krelay
**Date**: 2026-01-23

This document lists all generated Maven artifacts ready for manual upload to Maven Central (Sonatype OSSRH).

---

## Artifacts Location

All artifacts are located in:
```
krelay/build/maven-central-staging/dev/brewkits/
```

---

## Artifact Structure

### 1. Android Library (krelay-android)

**Path**: `krelay-android/1.0.0/`

Main artifacts:
- `krelay-android-1.0.0.aar` - Android library (35,920 bytes)
- `krelay-android-1.0.0-sources.jar` - Android sources (8,291 bytes)
- `krelay-android-1.0.0.pom` - Maven POM file
- `krelay-android-1.0.0.module` - Gradle module metadata

Checksums (for each artifact):
- `.md5` - MD5 hash
- `.sha1` - SHA1 hash
- `.sha256` - SHA256 hash
- `.sha512` - SHA512 hash

Metadata:
- `maven-metadata.xml` - Version metadata (with checksums)

**Total Android files**: 24 files

---

### 2. Multiplatform Library (krelay)

**Path**: `krelay/1.0.0/`

Main artifacts:
- `krelay-1.0.0.jar` - Multiplatform JAR (57,824 bytes)
- `krelay-1.0.0-metadata.jar` - Metadata JAR (261 bytes)
- `krelay-1.0.0-sources.jar` - Common sources (12,117 bytes)
- `krelay-1.0.0.klib` - iOS native library (Kotlin/Native)
- `krelay-1.0.0.pom` - Maven POM file
- `krelay-1.0.0.module` - Gradle module metadata
- `krelay-1.0.0-kotlin-tooling-metadata.json` - Kotlin tooling metadata

Checksums (for each artifact):
- `.md5` - MD5 hash
- `.sha1` - SHA1 hash
- `.sha256` - SHA256 hash
- `.sha512` - SHA512 hash

Metadata:
- `maven-metadata.xml` - Version metadata (with checksums)

**Total Multiplatform files**: 40 files

---

## File Size Summary

```
Android Library (krelay-android-1.0.0.aar):     35,920 bytes
Android Sources (krelay-android-1.0.0-sources.jar): 8,291 bytes

Multiplatform JAR (krelay-1.0.0.jar):           57,824 bytes
Multiplatform Sources (krelay-1.0.0-sources.jar): 12,117 bytes
iOS Native Library (krelay-1.0.0.klib):         [size varies by platform]
```

**Total artifacts**: 64 files (24 Android + 40 Multiplatform)

---

## Checksum Verification

All artifacts have been signed with the following checksums:
- **MD5**: For legacy compatibility
- **SHA1**: Maven Central requirement
- **SHA256**: Modern security standard
- **SHA512**: Highest security standard

Example checksum files for `krelay-1.0.0.jar`:
```
krelay-1.0.0.jar.md5
krelay-1.0.0.jar.sha1
krelay-1.0.0.jar.sha256
krelay-1.0.0.jar.sha512
```

---

## Manual Upload to Maven Central

### Prerequisites

1. **Sonatype Account**: https://issues.sonatype.org/
2. **Group ID Approved**: `dev.brewkits` must be verified
3. **GPG Signing**: All artifacts must be GPG signed before upload

### Important: GPG Signing Required

**Maven Central requires GPG signatures (`.asc` files) for all artifacts.**

Currently, the artifacts have checksums but are **NOT GPG signed yet**.

#### Add GPG Signatures

Before uploading, you must sign all artifacts:

```bash
# Navigate to staging directory
cd krelay/build/maven-central-staging/

# Sign all JAR files
find . -name "*.jar" -exec gpg --detach-sign --armor {} \;

# Sign all AAR files
find . -name "*.aar" -exec gpg --detach-sign --armor {} \;

# Sign all POM files
find . -name "*.pom" -exec gpg --detach-sign --armor {} \;

# Sign all MODULE files
find . -name "*.module" -exec gpg --detach-sign --armor {} \;

# Sign all KLIB files
find . -name "*.klib" -exec gpg --detach-sign --armor {} \;

# Sign all metadata.xml files
find . -name "maven-metadata.xml" -exec gpg --detach-sign --armor {} \;
```

This will create `.asc` signature files for each artifact.

**Verify signatures created**:
```bash
find . -name "*.asc" | wc -l
# Should show 64+ signature files
```

---

### Upload Methods

#### Method 1: Automated Upload (Recommended)

Use Gradle to publish with GPG signing:

```bash
# Make sure ~/.gradle/gradle.properties has:
# - ossrhUsername
# - ossrhPassword
# - signing.key or signing.keyId
# - signing.password

# Publish to OSSRH
./gradlew :krelay:publishAllPublicationsToOSSRHRepository
```

This will automatically sign and upload all artifacts.

#### Method 2: Manual Upload via Sonatype Web UI

1. **Login**: https://s01.oss.sonatype.org/
2. **Create Bundle**:
   - Click "Staging Upload"
   - Select "Artifact Bundle"
3. **Create ZIP Bundle**:
   ```bash
   cd krelay/build/maven-central-staging/
   # After GPG signing, create bundle
   zip -r krelay-1.0.0-bundle.zip dev/
   ```
4. **Upload Bundle**: Upload `krelay-1.0.0-bundle.zip`
5. **Close & Release**: Follow Sonatype release process

#### Method 3: Maven Deploy Plugin

```bash
# Deploy each artifact manually
mvn deploy:deploy-file \
  -DgroupId=dev.brewkits \
  -DartifactId=krelay \
  -Dversion=1.0.0 \
  -Dpackaging=jar \
  -Dfile=krelay/build/maven-central-staging/dev/brewkits/krelay/1.0.0/krelay-1.0.0.jar \
  -Durl=https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/ \
  -DrepositoryId=ossrh
```

---

## Verification After Upload

### 1. Check Staging Repository

Login to https://s01.oss.sonatype.org/ and verify:
- All artifacts are present
- All artifacts have `.asc` signature files
- POM files are valid
- Checksums match

### 2. Close Staging Repository

In Sonatype UI:
1. Select staging repository (e.g., `devbrewkits-1001`)
2. Click "Close"
3. Wait for validation (2-5 minutes)
4. Check for errors in "Activity" tab

### 3. Release to Maven Central

After successful validation:
1. Click "Release" button
2. Confirm release
3. Wait for sync (2-4 hours)

### 4. Verify on Maven Central

Check availability:
```
https://repo1.maven.org/maven2/dev/brewkits/krelay/1.0.0/
```

---

## Troubleshooting

### Missing .asc Files

**Error**: "No signature files"

**Solution**: Run GPG signing commands above

### Invalid Signature

**Error**: "Signature verification failed"

**Solution**:
- Verify GPG key is published to keyserver
- Check signing key matches uploaded key
- Re-sign artifacts with correct key

### Invalid POM

**Error**: "POM is invalid"

**Solution**:
- Check `krelay/build.gradle.kts` POM configuration
- Verify all required fields are present:
  - name, description, url
  - licenses, developers, scm

### Unauthorized

**Error**: "401 Unauthorized"

**Solution**:
- Verify Sonatype credentials in `~/.gradle/gradle.properties`
- Ensure `dev.brewkits` group ID is approved

---

## Quick Reference: Required Files for Maven Central

For each artifact, Maven Central requires:
1. ✅ The artifact file (`.jar`, `.aar`, `.pom`, etc.)
2. ✅ Checksums (`.md5`, `.sha1`) - **Already generated**
3. ⚠️ GPG signature (`.asc`) - **MUST BE ADDED**

**Current status**:
- ✅ All artifacts generated
- ✅ All checksums generated (MD5, SHA1, SHA256, SHA512)
- ⚠️ GPG signatures NOT generated yet

**Next step**: Add GPG signatures using commands in "Add GPG Signatures" section above.

---

## Complete File List

### Android Artifacts (krelay-android/1.0.0/)

```
krelay-android-1.0.0.aar
krelay-android-1.0.0.aar.md5
krelay-android-1.0.0.aar.sha1
krelay-android-1.0.0.aar.sha256
krelay-android-1.0.0.aar.sha512

krelay-android-1.0.0-sources.jar
krelay-android-1.0.0-sources.jar.md5
krelay-android-1.0.0-sources.jar.sha1
krelay-android-1.0.0-sources.jar.sha256
krelay-android-1.0.0-sources.jar.sha512

krelay-android-1.0.0.pom
krelay-android-1.0.0.pom.md5
krelay-android-1.0.0.pom.sha1
krelay-android-1.0.0.pom.sha256
krelay-android-1.0.0.pom.sha512

krelay-android-1.0.0.module
krelay-android-1.0.0.module.md5
krelay-android-1.0.0.module.sha1
krelay-android-1.0.0.module.sha256
krelay-android-1.0.0.module.sha512

maven-metadata.xml
maven-metadata.xml.md5
maven-metadata.xml.sha1
maven-metadata.xml.sha256
maven-metadata.xml.sha512
```

### Multiplatform Artifacts (krelay/1.0.0/)

```
krelay-1.0.0.jar
krelay-1.0.0.jar.md5
krelay-1.0.0.jar.sha1
krelay-1.0.0.jar.sha256
krelay-1.0.0.jar.sha512

krelay-1.0.0-metadata.jar
krelay-1.0.0-metadata.jar.md5
krelay-1.0.0-metadata.jar.sha1
krelay-1.0.0-metadata.jar.sha256
krelay-1.0.0-metadata.jar.sha512

krelay-1.0.0-sources.jar
krelay-1.0.0-sources.jar.md5
krelay-1.0.0-sources.jar.sha1
krelay-1.0.0-sources.jar.sha256
krelay-1.0.0-sources.jar.sha512

krelay-1.0.0.klib
krelay-1.0.0.klib.md5
krelay-1.0.0.klib.sha1
krelay-1.0.0.klib.sha256
krelay-1.0.0.klib.sha512

krelay-1.0.0.pom
krelay-1.0.0.pom.md5
krelay-1.0.0.pom.sha1
krelay-1.0.0.pom.sha256
krelay-1.0.0.pom.sha512

krelay-1.0.0.module
krelay-1.0.0.module.md5
krelay-1.0.0.module.sha1
krelay-1.0.0.module.sha256
krelay-1.0.0.module.sha512

krelay-1.0.0-kotlin-tooling-metadata.json
krelay-1.0.0-kotlin-tooling-metadata.json.md5
krelay-1.0.0-kotlin-tooling-metadata.json.sha1
krelay-1.0.0-kotlin-tooling-metadata.json.sha256
krelay-1.0.0-kotlin-tooling-metadata.json.sha512

maven-metadata.xml
maven-metadata.xml.md5
maven-metadata.xml.sha1
maven-metadata.xml.sha256
maven-metadata.xml.sha512
```

---

## Summary

**Status**: ✅ All Maven artifacts successfully generated

**Generated**:
- 64 total files (artifacts + checksums + metadata)
- 2 artifact variants (Android + Multiplatform)
- 4 checksum types for each file (MD5, SHA1, SHA256, SHA512)

**Required Before Upload**:
- ⚠️ Add GPG signatures (`.asc` files) to all artifacts
- ✅ Sonatype account with `dev.brewkits` group ID approved
- ✅ POM files with complete metadata

**Recommended Upload Method**:
Use automated Gradle publishing with GPG signing:
```bash
./gradlew :krelay:publishAllPublicationsToOSSRHRepository
```

---

**For detailed publishing instructions, see**: [docs/MAVEN_CENTRAL_SETUP.md](docs/MAVEN_CENTRAL_SETUP.md)

**GitHub Release**: https://github.com/brewkits/KRelay/releases/tag/v1.0.0
