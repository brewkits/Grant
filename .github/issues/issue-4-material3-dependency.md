# [ISSUE] Material3 dependency could cause version conflicts

**Labels:** `enhancement`, `dependency`, `v1.0.1`
**Priority:** ⭐⭐⭐ MEDIUM

## 💡 Issue Description

grant-compose uses `implementation(compose.material3)` which becomes a transitive dependency for apps. This can cause version conflicts for apps using Material 2 or different Compose versions.

## 📍 Location

**File:** `grant-compose/build.gradle.kts:50`

## 🔍 Current Behavior

```kotlin
sourceSets {
    commonMain.dependencies {
        implementation(project(":grant-core"))
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)  // ⚠️ Transitive dependency
        implementation(compose.ui)
    }
}
```

## ⚠️ Problems

1. **Version Conflicts:**
   - App uses Compose 1.6.0
   - Grant pulls Compose 1.7.1 transitively
   - Gradle resolution errors

2. **Material 2 Apps:**
   - App uses Material 2
   - Grant forces Material 3 as transitive dependency
   - Unnecessary APK size increase

3. **Dependency Hell:**
   - Apps can't control Compose version
   - Hard to debug version conflicts

## ✅ Proposed Solution

**Change to `api` dependency:**

```kotlin
sourceSets {
    commonMain.dependencies {
        implementation(project(":grant-core"))
        implementation(compose.runtime)
        implementation(compose.foundation)
        api(compose.material3)  // ✅ Let apps control version
        implementation(compose.ui)
    }
}
```

## 🎯 Benefits

1. **App Controls Version:**
   - App's Compose version takes precedence
   - No forced upgrades

2. **Better Dependency Resolution:**
   - Gradle can resolve conflicts properly
   - Apps see Material3 as explicit dependency

3. **Clear Requirements:**
   - Apps know they need Material3
   - No hidden transitive dependencies

## ⚠️ Breaking Changes

- [ ] No - This is backward compatible

**Why Not Breaking:**
- Apps already get Material3 transitively
- Changing to `api` just makes it explicit
- Same runtime behavior

## 📊 Impact Analysis

**Before (implementation):**
```
app/
└─ grant-compose/
   └─ compose.material3:1.7.1 (transitive, hidden)
```

**After (api):**
```
app/
├─ compose.material3:1.6.0 (app's version wins!)
└─ grant-compose/
   └─ compose.material3 (api, app controls version)
```

## 🧪 Testing Strategy

1. **Test with Different Compose Versions:**
   ```kotlin
   // Test that app's version wins
   dependencies {
       implementation("org.jetbrains.compose.material3:material3:1.6.0")
       implementation("dev.brewkits:grant-compose:1.0.1")
   }
   ```

2. **Test Compilation:**
   - Ensure grant-compose still compiles
   - No API breakage

3. **Runtime Testing:**
   - Dialogs still render correctly
   - No ClassNotFoundException

## 📚 Documentation Updates

**Update docs/DEPENDENCY_MANAGEMENT.md:**
```markdown
## Compose Material3 Dependency

grant-compose requires Compose Multiplatform with Material3.

### Version Control

We use `api` dependency for Material3, which means:
- ✅ Your app controls the Material3 version
- ✅ No forced version upgrades
- ✅ Clear dependency tree

### Minimum Version

- **Minimum:** Compose 1.6.0
- **Recommended:** Compose 1.7.1+

### Usage

```kotlin
dependencies {
    // Your Compose version
    implementation("org.jetbrains.compose.material3:material3:1.7.1")

    // Grant will use your version
    implementation("dev.brewkits:grant-compose:1.0.1")
}
```

### Material 2 Apps

If you're using Material 2, you have two options:

1. **Use grant-core only** (recommended):
   ```kotlin
   implementation("dev.brewkits:grant-core:1.0.1")
   // Build your own dialogs with Material 2
   ```

2. **Upgrade to Material 3** (or use both):
   ```kotlin
   implementation("androidx.compose.material:material:1.6.0")  // Material 2
   implementation("androidx.compose.material3:material3:1.7.1") // Material 3
   implementation("dev.brewkits:grant-compose:1.0.1")
   ```
```

**Update README.md:**
```markdown
## 📦 Installation

### For Apps Using Material 3

```kotlin
dependencies {
    implementation("dev.brewkits:grant-core:1.0.1")
    implementation("dev.brewkits:grant-compose:1.0.1")
}
```

### For Apps Using Material 2

Use grant-core only and build custom dialogs:

```kotlin
dependencies {
    implementation("dev.brewkits:grant-core:1.0.1")
    // Don't include grant-compose if you're on Material 2
}
```
```

## ✅ Definition of Done

- [ ] Change Material3 dependency to `api` in build.gradle.kts
- [ ] Test compilation with different Compose versions
- [ ] Update DEPENDENCY_MANAGEMENT.md
- [ ] Update README.md installation section
- [ ] Update CHANGELOG.md
- [ ] Verify demo app still works

---

**Review Finding Reference:** CODE_REVIEW_FINDINGS.md - Issue #4
**Estimated Effort:** 30 minutes
