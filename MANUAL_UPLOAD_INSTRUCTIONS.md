# Manual Upload to Maven Central - Instructions

**Bundle File**: `krelay-1.0.0-bundle.zip` (840KB)
**Date**: 2026-01-23
**Version**: 1.0.0

---

## ✅ Preparation Complete

All artifacts have been prepared for manual upload:

- ✅ **13 GPG signatures (.asc)** generated for all main artifacts
- ✅ **64 checksum files** (MD5, SHA1, SHA256, SHA512)
- ✅ **ZIP bundle** created: `krelay-1.0.0-bundle.zip`

Bundle contains:
- Android library (AAR) + sources + metadata
- Multiplatform library (JAR) + sources + metadata
- iOS native library (KLIB)
- POM files with complete metadata
- Gradle module files
- All checksums and GPG signatures

---

## Step-by-Step Upload Process

### Step 1: Login to Sonatype OSSRH

1. Open browser and go to: **https://s01.oss.sonatype.org/**
2. Click **"Log In"** (top right)
3. Enter your Sonatype credentials:
   - Username: (your OSSRH username)
   - Password: (your OSSRH password)

**Note**: This is the NEW Sonatype instance (s01.oss.sonatype.org), not the old oss.sonatype.org

---

### Step 2: Upload Bundle

1. In the left sidebar, click **"Staging Upload"**
2. Select **"Upload Mode: Artifact Bundle"**
3. Click **"Select Bundle to Upload"**
4. Choose the file: `krelay-1.0.0-bundle.zip`
5. Click **"Upload Bundle"**
6. Wait for upload to complete (should take 10-30 seconds for 840KB)

**Expected result**: You should see "Upload successful" message

---

### Step 3: Find Your Staging Repository

1. In the left sidebar, click **"Staging Repositories"**
2. In the search box (top right), type: **"brewkits"**
3. Look for a repository named something like:
   - `devbrewkits-1001`
   - `devbrewkits-1002`
   - etc. (the number increments each time)
4. Click on your repository to select it

**Expected content**:
- Should show `dev/brewkits/krelay/1.0.0/` structure
- Should show `dev/brewkits/krelay-android/1.0.0/` structure

---

### Step 4: Close the Staging Repository

**What "Close" means**: Sonatype will validate your artifacts against Maven Central requirements

1. With your repository selected, click the **"Close"** button (top toolbar)
2. A dialog will appear asking for a description
   - Enter: `KRelay v1.0.0 - Initial release`
3. Click **"Confirm"**
4. Wait for validation to complete (2-5 minutes)

**Validation checks**:
- ✓ POM file is valid (name, description, URL, licenses, developers, SCM)
- ✓ All artifacts have GPG signatures (.asc files)
- ✓ All artifacts have checksums (MD5, SHA1)
- ✓ Group ID `dev.brewkits` is authorized for your account
- ✓ All required files are present

**During validation**:
- The "Activity" tab (bottom panel) will show progress
- You'll see rules being executed (Checksum Validation, Signature Validation, etc.)
- Status will change from "Open" → "Closing" → "Closed"

---

### Step 5: Check Validation Results

**If validation SUCCEEDS**:
- ✅ Status changes to **"Closed"**
- ✅ The "Release" button becomes enabled
- ✅ Continue to Step 6

**If validation FAILS**:
- ❌ Status changes to **"Failed"** or returns to "Open"
- ❌ Click the "Activity" tab to see error details
- ❌ Common errors and fixes:

#### Common Validation Errors

**Error: "No signature files"**
- **Cause**: Missing .asc files
- **Fix**: Already fixed - bundle includes 13 .asc files

**Error: "Invalid signature"**
- **Cause**: GPG key not on keyserver
- **Fix**: Upload your GPG public key
  ```bash
  gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
  ```

**Error: "Invalid POM"**
- **Cause**: Missing required POM fields
- **Check**: krelay/build.gradle.kts has all required metadata
- **Status**: Already configured correctly

**Error: "Unauthorized for group ID"**
- **Cause**: Group ID `dev.brewkits` not approved
- **Fix**: Create Sonatype JIRA ticket to request group ID

**Error: "Checksum validation failed"**
- **Cause**: File corruption during upload
- **Fix**: Re-upload the bundle

**To retry after fixing**:
1. Click **"Drop"** button to delete the failed repository
2. Go back to Step 2 and upload again

---

### Step 6: Release to Maven Central

**Important**: This step is IRREVERSIBLE. Once released, you cannot delete or modify the artifacts.

1. With your **"Closed"** repository selected, click the **"Release"** button (top toolbar)
2. A dialog will appear asking for confirmation
   - Description: `Release KRelay v1.0.0 to Maven Central`
3. Check the option: **"Automatically Drop"** (recommended)
   - This will delete the staging repository after successful release
4. Click **"Confirm"**

**What happens next**:
- Artifacts are moved to Maven Central sync queue
- Staging repository is dropped (deleted)
- You'll see "Release in progress" in Activity tab

---

### Step 7: Wait for Maven Central Sync

Maven Central sync is NOT instant:

**Timeline**:
- ⏱️ **2-4 hours**: Artifacts appear on Maven Central
- ⏱️ **4-24 hours**: Fully indexed and searchable
- ⏱️ **24-48 hours**: Available in all mirrors worldwide

**Progress tracking**:
- After 2 hours, check: https://repo1.maven.org/maven2/dev/brewkits/krelay/1.0.0/
- After 4 hours, search: https://search.maven.org/search?q=g:dev.brewkits
- After 24 hours, should appear in IDE auto-completion

---

## Verification Steps

### Immediate Verification (After Release)

Check that release was successful in Sonatype:
1. Go to **"Staging Repositories"**
2. Your repository should be gone (if "Automatically Drop" was checked)
3. No error messages in Activity tab

### 2-4 Hours After Release

Check Maven Central repository:
```
https://repo1.maven.org/maven2/dev/brewkits/krelay/1.0.0/
```

Expected files:
- `krelay-1.0.0.jar`
- `krelay-1.0.0.pom`
- `krelay-1.0.0.module`
- `krelay-1.0.0-sources.jar`
- etc. (all artifacts from bundle)

### 4-24 Hours After Release

Check Maven Central search:
```
https://search.maven.org/search?q=g:dev.brewkits%20AND%20a:krelay
```

Should show: **KRelay 1.0.0** with download statistics

### Test in a Project

Create a new Gradle project and add dependency:

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.brewkits:krelay:1.0.0")
}
```

Run: `./gradlew dependencies`

Expected: KRelay 1.0.0 should download successfully

---

## Troubleshooting

### Upload Failed

**Symptoms**: Error message during bundle upload

**Solutions**:
- Check internet connection
- Verify file is not corrupted: `unzip -t krelay-1.0.0-bundle.zip`
- Try uploading again
- Try different browser

### Close/Validation Stuck

**Symptoms**: Status stays "Closing" for more than 10 minutes

**Solutions**:
- Refresh the page
- Click "Refresh" button in toolbar
- Check Sonatype status: https://status.maven.org/
- Wait and try again later

### Can't Find Staging Repository

**Symptoms**: No repository appears after upload

**Solutions**:
- Click "Refresh" button
- Make sure you're searching for "brewkits" (not "krelay")
- Check you're logged in with correct account
- Try uploading bundle again

### Release Button Disabled

**Symptoms**: Can't click "Release" button

**Solutions**:
- Repository must be "Closed" first
- Validation must pass completely
- Refresh the page
- Select the repository again

### Artifacts Not Appearing on Maven Central

**Symptoms**: 24 hours passed, still not on Maven Central

**Solutions**:
- Check https://repo1.maven.org/maven2/dev/brewkits/krelay/ (should show 1.0.0 folder)
- If folder exists but search doesn't find it, wait more (indexing delay)
- If folder doesn't exist, check Sonatype Activity log for errors
- Contact Sonatype support: https://issues.sonatype.org/

---

## After Successful Publication

### 1. Update README.md

Add badge showing Maven Central availability:
```markdown
[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits/krelay.svg)](https://search.maven.org/artifact/dev.brewkits/krelay)
```

Update installation instructions to use Maven Central:
```kotlin
dependencies {
    implementation("dev.brewkits:krelay:1.0.0")
}
```

### 2. Update ROADMAP.md

Mark Phase 1 tasks complete:
- [x] Publish v1.0.0 to Maven Central
- [x] Available on Maven Central within 24 hours

### 3. Announce Release

Post announcements on:
- [ ] Reddit r/Kotlin
- [ ] Kotlin Slack #multiplatform
- [ ] Twitter/X (with #KotlinMultiplatform hashtag)
- [ ] LinkedIn
- [ ] Dev.to or Medium blog post

Example announcement:
```
🎉 KRelay v1.0.0 is now available on Maven Central!

The Glue Code Standard for Kotlin Multiplatform - Safe, leak-free bridge between shared code and platform-specific APIs.

implementation("dev.brewkits:krelay:1.0.0")

Features:
✅ Automatic WeakReference (zero leaks)
✅ Sticky Queue (survive lifecycle changes)
✅ Thread Safety (runs on main thread)
✅ Simple API (register, dispatch, done!)

GitHub: https://github.com/brewkits/KRelay
Docs: https://github.com/brewkits/KRelay#readme

#KotlinMultiplatform #KMP #AndroidDev #iOSDev
```

### 4. Monitor Adoption

Track metrics:
- GitHub stars
- Maven Central download count
- GitHub issues/discussions
- Social media mentions

---

## Support

If you encounter issues:

1. **Sonatype Support**: https://issues.sonatype.org/
2. **GitHub Issues**: https://github.com/brewkits/KRelay/issues
3. **Email**: dev@brewkits.dev

---

## Quick Reference

**Bundle file**: `krelay-1.0.0-bundle.zip` (840KB)
**Upload URL**: https://s01.oss.sonatype.org/
**Maven Central**: https://repo1.maven.org/maven2/dev/brewkits/krelay/
**Search**: https://search.maven.org/search?q=g:dev.brewkits

**Credentials needed**:
- Sonatype username/password
- GPG key already used for signing (already done)

**Estimated time**:
- Upload: 1 minute
- Validation: 2-5 minutes
- Release: 1 minute
- Sync to Maven Central: 2-24 hours

---

**Good luck with your release! 🚀**
