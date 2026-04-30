# Grant Library - Dependency Management

Guide for managing dependencies and avoiding version conflicts.

---

## 📦 Dependencies Overview

Grant library has the following dependencies:

### Required Dependencies
- **Kotlin Coroutines** `1.10.2` - For async operations
- **AndroidX Activity Compose** `1.12.1` (Android only) - For permission requests

### Optional Dependencies
- **Koin** `4.1.1` - For dependency injection (OPTIONAL). Use `dev.brewkits:grant-core-koin:1.3.0`.

---

## 🔧 Koin Dependency (Optional)

### Current Version
Grant library uses **Koin 4.1.1** for optional dependency injection support.

### Important Notes

✅ **Koin is OPTIONAL** - You don't need Koin to use Grant library!

As of version **1.3.0**, Koin support has been moved to a separate module to ensure `grant-core` remains a pure, dependency-free artifact.

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
