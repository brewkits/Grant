# Grant Library - Dependency Management

Guide for managing dependencies and avoiding version conflicts.

---

## Desktop and Web projects

The upcoming 3.0.0-alpha01 preview (which absorbs the planned 2.7.0) wires Desktop into publication and its BOM, but has not
been uploaded to Central. See the [preview guide](../grant-desktop/README.md) for staging
and independent consumer verification. The following matrix describes released **2.6.0**.

As of 2.6.0, module availability and native permission support are different:

| Module | Published targets | Desktop behavior |
| --- | --- | --- |
| `grant-core` | Android, iOS, JVM, JS, Wasm JS | Uses registered desktop handlers; otherwise `checkStatus()` and `request()` return `DENIED_ALWAYS`, and `openSettings()` only logs a notice. |
| `grant-testing` | Android, iOS, JVM, JS, Wasm JS | Test doubles, not native permission handlers. |
| `grant-compose`, `grant-core-koin`, optional permission modules | Android/iOS (see the installation guide for iOS architecture exceptions) | No JVM or browser variant. |
| `grant-desktop` | **Not published to Maven Central; not included in the BOM** | Experimental macOS Apple Silicon camera/microphone bridge in this repository. |

**3.0.0 (in development):** `grant-location`, `grant-gallery` and `grant-camera` are new and,
unlike the other optional permission modules, publish **every** target `grant-core` does
(Android, iOS, JVM, JS, Wasm JS). Their `initialize()` is a no-op outside iOS, so they can go
in `commonMain` next to `grant-core`. On iOS they are required for Location, Gallery and
Camera/Microphone; see the [Migration Guide](MIGRATION_GUIDE.md#upgrading-from-grant-2x-to-300).

The browser implementation in `grant-core` supports camera, microphone, location, and
notifications through browser APIs. This does not imply equivalent native Desktop support.

### Keep mobile-only dependencies in mobileMain

For a module using the Kotlin Multiplatform and `com.android.library` plugins, the following
configuration keeps the default iOS hierarchy and adds a source set shared only by Android
and iOS. Keep your existing Android namespace/SDK configuration and plugin versions.

```kotlin
// shared/build.gradle.kts
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")
    js { browser() } // Optional: omit if your app does not target the browser.

    applyDefaultHierarchyTemplate()

    sourceSets {
        val mobileMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("dev.brewkits:grant-core:2.6.0")
                implementation("dev.brewkits:grant-compose:2.6.0")
                // Add other Android/iOS-only Grant modules here as needed.
                // (On 3.0.0, grant-location/-gallery/-camera can stay in commonMain.)
            }
        }
        androidMain.get().dependsOn(mobileMain)
        iosMain.get().dependsOn(mobileMain)
    }
}
```

Put code that imports these Grant APIs in `src/mobileMain/kotlin`, `androidMain`, or
`iosMain`. `commonMain`, `desktopMain`, and browser source sets cannot access dependencies
declared only in `mobileMain`. Shared UI can call an application-owned interface, with
platform implementations supplied at startup. Model unsupported capabilities explicitly in
your application instead of treating them as granted.

If you need Grant's core API across all targets, move **only** `grant-core` to
`commonMain.dependencies`; keep `grant-compose`, `grant-core-koin`, and optional permission
modules in `mobileMain`. `grant-testing` may likewise be added to `commonTest` with an
explicit matching version. Core alone on Desktop does not provide native handlers.

The explicit hierarchy call follows Kotlin's
[guide to adding intermediate source sets](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html#creating-additional-source-sets).

### Experimental macOS development

There is currently no Maven Central dependency that enables Grant's native Desktop bridge.
For development on an Apple Silicon Mac, check out Grant at tag `v2.6.0` and use the
repository's `desktop-harness`, which depends on `grant-core` and `grant-desktop` as local
Gradle projects:

```shell
./gradlew :desktop-harness:createDistributable
```

Launch the generated `.app` through Finder or macOS `open`, rather than directly executing
its binary from a terminal. See
[the harness](../desktop-harness/build.gradle.kts) for packaging and usage-description keys.
Permission attribution must be verified against the packaged app's identity.

For a separate experimental consumer, on the same Mac you can run:

```shell
./gradlew :grant-core:publishToMavenLocal :grant-desktop:publishToMavenLocal
```

Then add `mavenLocal()` before `mavenCentral()` in the consumer's dependency repositories,
and add both `dev.brewkits:grant-core:2.6.0` and `dev.brewkits:grant-desktop:2.6.0` to its
`desktopMain` dependencies. This is a local development build, not a supported Central
release. Call `dev.brewkits.grant.desktop.GrantDesktop.initialize()` once before requesting
permissions. Package the app with camera/microphone usage descriptions and the entitlements
required for its distribution mode.

Current limits: camera and microphone only; the Settings action opens the Camera pane;
no bundled Intel macOS, Windows, or Linux bridge. Missing or unsupported bridges fall back
to `DENIED_ALWAYS`. See [the roadmap](../ROADMAP.md) for remaining publishing work.

---

## 📦 Dependencies Overview

Grant library has the following dependencies:

### Required Dependencies
- **Kotlin Coroutines** `1.10.2` - For async operations
- **AndroidX Activity Compose** `1.12.1` (Android only) - For permission requests

### Optional Dependencies
- **Koin** `4.1.1` - For dependency injection (OPTIONAL). Use `dev.brewkits:grant-core-koin:2.5.0`.

---

## 🔧 Koin Dependency (Optional)

### Current Version
Grant library uses **Koin 4.1.1** for optional dependency injection support.

### Important Notes

✅ **Koin is OPTIONAL** - You don't need Koin to use Grant library!

As of version **1.3.1**, Koin support has been moved to a separate module to ensure `grant-core` remains a pure, dependency-free artifact.

1. **Manual Creation (Recommended)** - Use `grant-core`
2. **Koin DI Modules** - Use `grant-core-koin`

---

## 🚀 Usage Without Koin (Recommended)

### Manual Instance Creation

Use `GrantFactory` and `ServiceFactory` for manual dependency injection:

#### Android Example
```kotlin
class MainActivity : ComponentActivity() {
    // Create GrantManager without Koin
    private val grantManager by lazy {
        GrantFactory.create(applicationContext)
    }

    // Create ServiceManager without Koin
    private val serviceManager by lazy {
        ServiceFactory.createServiceManager()
    }

    // Create combined checker
    private val checker by lazy {
        GrantAndServiceChecker(grantManager, serviceManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use grantManager, serviceManager, or checker
    }
}
```

#### iOS Example
```kotlin
class ViewController : UIViewController {
    private val grantManager = GrantFactory.create()
    private val serviceManager = ServiceFactory.createServiceManager()

    override fun viewDidLoad() {
        super.viewDidLoad()
        // Use grantManager or serviceManager
    }
}
```

#### Compose Multiplatform Example
```kotlin
@Composable
fun App() {
    // Create once and remember
    val grantManager = remember {
        GrantFactory.create(
            context = LocalContext.current // Android
            // or null for iOS
        )
    }

    val handler = remember {
        GrantHandler(
            grantManager = grantManager,
            grant = AppGrant.CAMERA,
            scope = rememberCoroutineScope()
        )
    }

    // Use handler
}
```

---

## 🔌 Usage With Koin (Optional)

If your project already uses Koin, you can use Grant's DI modules:

### Step 1: Add Koin Dependency

Make sure you have Koin in your project:

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation("io.insert-koin:koin-core:4.1.1") // Or your version
    implementation("io.insert-koin:koin-android:4.1.1") // Android
}
```

### Step 2: Setup Grant Modules

```kotlin
startKoin {
    modules(
        grantModule,          // Common Grant DI
        grantPlatformModule   // Platform-specific (Android/iOS)
    )
}
```

### Step 3: Inject Dependencies

```kotlin
class MyViewModel : ViewModel() {
    private val grantManager: GrantManager by inject()
    private val serviceManager: ServiceManager by inject()
    private val checker: GrantAndServiceChecker by inject()
}
```

---

## ⚠️ Handling Version Conflicts

### Problem: Koin Version Mismatch

Your project might use a different Koin version than Grant library:
- Grant library: **Koin 4.1.1**
- Your project: **Koin 3.x.x** or different version

### Solution 1: Exclude Koin from Grant (Recommended)

If you want to manage Koin version yourself:

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation(project(":grant-core")) {
        exclude(group = "io.insert-koin")
    }

    // Use your own Koin version
    implementation("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-android:3.5.0")
}
```

**Important**: After excluding Koin, you should:
- ✅ Use manual creation (`GrantFactory`, `ServiceFactory`)
- ❌ Don't use Grant's Koin DI modules (`grantModule`, `grantPlatformModule`)

### Solution 2: Use Grant's Koin Version

Let Gradle resolve to Grant's Koin version (4.1.1):

```kotlin
dependencies {
    implementation(project(":grant-core"))
    // Gradle will use Koin 4.1.1 from Grant
}
```

### Solution 3: Force Your Koin Version

Force your project's Koin version across all dependencies:

```kotlin
// In your project's build.gradle.kts (root)
allprojects {
    configurations.all {
        resolutionStrategy {
            force("io.insert-koin:koin-core:3.5.0")
            force("io.insert-koin:koin-android:3.5.0")
        }
    }
}
```

**Warning**: This may cause runtime errors if Grant's DI modules are incompatible with your Koin version.

---

## 📊 Dependency Conflict Resolution Matrix

| Your Koin Version | Grant's Koin | Recommendation |
|-------------------|--------------|----------------|
| None (no Koin) | 4.1.1 | ✅ **Exclude Koin** from Grant, use manual creation |
| 4.x.x | 4.1.1 | ✅ **Keep both** - Gradle will resolve to latest 4.x |
| 3.x.x | 4.1.1 | ⚠️ **Exclude Koin** from Grant, use manual creation |
| 2.x.x | 4.1.1 | ❌ **Exclude Koin** from Grant, use manual creation |

---

## 🎯 Best Practices

### 1. Prefer Manual Creation
**Recommended approach** for most projects:
- ✅ No dependency on Koin
- ✅ Full control over instances
- ✅ No version conflicts
- ✅ Works with any DI framework (Hilt, Dagger, etc.)

```kotlin
// Simple and clean
val grantManager = GrantFactory.create(context)
```

### 2. Use Koin Only If Needed
Only use Grant's Koin modules if:
- ✅ Your project already heavily uses Koin
- ✅ You want consistent DI across entire app
- ✅ You can manage Koin version conflicts

### 3. Document Your Choice
In your project, document which approach you use:
```kotlin
// Manual creation approach
// We don't use Koin to avoid version conflicts
private val grantManager = GrantFactory.create(context)
```

### 4. Exclude Transitive Dependencies
If using other DI framework, exclude Koin:
```kotlin
dependencies {
    // Hilt for DI
    implementation("com.google.dagger:hilt-android:2.48")

    // Grant library - exclude Koin
    implementation(project(":grant-core")) {
        exclude(group = "io.insert-koin")
    }
}
```

---

## 🧪 Testing Without Koin

Grant library's tests don't depend on Koin - they use fake implementations:

```kotlin
class FakeGrantManager : GrantManager {
    override suspend fun checkStatus(grant: AppGrant) = GrantStatus.GRANTED
    override suspend fun request(grant: AppGrant) = GrantStatus.GRANTED
    override fun openSettings() { }
}

@Test
fun testGrantFlow() {
    val fakeManager = FakeGrantManager()
    val handler = GrantHandler(fakeManager, AppGrant.CAMERA, testScope)
    // Test without Koin
}
```

---

## 📝 Migration Guide

### From Koin to Manual Creation

If you want to remove Koin dependency:

**Before (with Koin):**
```kotlin
class MyViewModel : ViewModel() {
    private val grantManager: GrantManager by inject()
}
```

**After (manual creation):**
```kotlin
class MyViewModel(
    private val grantManager: GrantManager
) : ViewModel()

// In your composition root
val grantManager = GrantFactory.create(context)
val viewModel = MyViewModel(grantManager)
```

### From Other DI to Grant

If migrating from another DI framework:

**With Hilt:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object GrantModule {
    @Provides
    @Singleton
    fun provideGrantManager(
        @ApplicationContext context: Context
    ): GrantManager = GrantFactory.create(context)
}

@HiltViewModel
class MyViewModel @Inject constructor(
    private val grantManager: GrantManager
) : ViewModel()
```

**With Dagger:**
```kotlin
@Module
class GrantModule {
    @Provides
    @Singleton
    fun provideGrantManager(context: Context): GrantManager {
        return GrantFactory.create(context)
    }
}
```

---

## ❓ FAQ

### Q: Do I need Koin to use Grant library?
**A:** No! Koin is completely optional. Use `GrantFactory` and `ServiceFactory` for manual creation.

### Q: What if my project uses Koin 3.x?
**A:** Exclude Koin from Grant and use manual creation, or upgrade to Koin 4.x.

### Q: Can I use Grant with Hilt/Dagger?
**A:** Yes! Use `GrantFactory.create()` and provide it through your DI framework.

### Q: Will excluding Koin break Grant library?
**A:** No, if you use manual creation. Don't use `grantModule` after excluding Koin.

### Q: What's the recommended approach?
**A:** Manual creation with `GrantFactory` - it's simple, flexible, and has no dependency conflicts.

---

## 🔗 Related Documentation

- [Quick Start Guide](grant-core/QUICK_START.md)
- [Testing Guide](TESTING.md)

---

*Last Updated: 2026-01-27*
