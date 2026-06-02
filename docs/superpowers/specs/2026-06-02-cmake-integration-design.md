# CMake Integration for prefab-plugin

## Summary

Integrate CMake build capability into the prefab-plugin, enabling a complete pipeline:
**CMake build → symbol strip → artifact collection → Prefab directory generation → AAR packaging**.

A single `cmake { }` DSL block drives the full flow. When `cmake { }` is not configured, the plugin behaves exactly as it does today — fully backward compatible.

## Architecture

### New files

```
src/main/java/io/github/byhook/prefab/
├── extension/
│   └── PrefabCmakeExtension.kt      ← CMake configuration DSL
└── task/
    └── CmakeBuildTask.kt            ← CMake configure + build + strip
```

### Modified files

| File | Change |
|---|---|
| `PrefabRootExtension.kt` | Add `cmake { }` nested extension |
| `PrefabGeneratePlugin.kt` | Register `CmakeBuildTask`, adjust dependency chain |

### Task dependency chain

```
With cmake configured:
  CmakeBuildTask ──> GenerateModulesTask ──> GeneratePrefabTask

Without cmake (existing behavior unchanged):
  user's dependsOnTask ──> GenerateModulesTask ──> GeneratePrefabTask
```

### Data flow

```
cmake { sourceDir, ndkVersion, platform, stl, ... }
       │
       ▼
CmakeBuildTask
  ├── Resolve NDK path
  ├── For each ABI: cmake configure + build
  ├── Optional: llvm-strip
  └── Output: {buildDir}/libs/{abi}/*.so, {buildDir}/include/**
       │
       ▼  (auto-assign sourceLibsDir, sourceIncsDir)
       │
GenerateModulesTask (unchanged)
       │
GeneratePrefabTask (unchanged)
```

## DSL Design

```kotlin
generatePrefab {
    cmake {
        // CMakeLists.txt directory (required when cmake block present)
        sourceDir = layout.projectDirectory.dir("src/main/cpp")

        // NDK version, defaults to $ANDROID_NDK_HOME
        ndkVersion = "27.0.12077973"

        // Build type, default "Release"
        buildType = "Release"

        // ANDROID_PLATFORM, default 21
        platform = 21

        // ANDROID_STL, default "c++_shared"
        stl = "c++_shared"

        // Strip symbols after build, default true
        stripSymbols = true

        // Extra CMake arguments passed through (-D flags)
        arguments = mapOf("SOME_FLAG" to "value")
    }

    // Existing config — abiList applies to both CMake and Prefab
    abiList = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    prefabName = "lame"
    prefabVersion = "3.100.0"
    manifestFile = ...

    module("lame.so", false) {
        libraryName = "libmp3lame"
    }
}
```

### Key rules

- `cmake { }` is entirely optional. Without it, plugin behavior is unchanged.
- When `cmake { }` is configured, `sourceLibsDir` and `sourceIncsDir` are auto-populated from CMake output. User-supplied values are overridden with a warning.
- `abiList` drives both CMake `ANDROID_ABI` and Prefab module generation — single source of truth.
- NDK resolution priority: `cmake.ndkVersion` > `$ANDROID_NDK_HOME` env > `local.properties` `ndk.dir`.
- Header file collection follows a convention-over-configuration approach: plugin collects from `sourceDir/include/` by default, with manual override via `cmake.includeSubDirs`.

## CmakeBuildTask Implementation

### Step 1: Resolve NDK

```
ndkDir = resolveNdkPath(cmakeConfig)
cmakeBinary = ndkDir/cmake/<version>/bin/cmake (or system cmake)
toolchainFile = ndkDir/build/cmake/android.toolchain.cmake
```

### Step 2: Build per ABI

For each abi in abiList:

```
cmake -S {sourceDir} -B {outputBuildDir}/{abi} \
      -DCMAKE_TOOLCHAIN_FILE={toolchainFile} \
      -DANDROID_ABI={abi} \
      -DANDROID_PLATFORM={platform} \
      -DANDROID_STL={stl} \
      -DCMAKE_BUILD_TYPE={buildType}
```

Then:

```
cmake --build {outputBuildDir}/{abi}
```

The output directory defaults to `{project.buildDir}/cmake-output` and is used to construct the intermediate prefab build layout:

```
{buildDir}/cmake-output/
├── libs/
│   ├── arm64-v8a/libxxx.so
│   └── armeabi-v7a/libxxx.so
└── include/
    └── xxx/
        └── xxx.h
```

### Step 2.5: Strip symbols (optional)

When `stripSymbols = true`:

```
{ndkDir}/toolchains/llvm/prebuilt/{host}/bin/llvm-strip --strip-all {soFile}
```

If `llvm-strip` is not found, log a warning and skip — the AGP will strip again during APK packaging anyway.

### Step 3: Wire to existing pipeline

```
sourceLibsDir  = project.layout.buildDirectory.dir("cmake-output/libs")
sourceIncsDir  = project.layout.buildDirectory.dir("cmake-output/include")
```

These feed directly into `GenerateModulesTask` unchanged.

## PrefabCmakeExtension

```kotlin
open class PrefabCmakeExtension {
    var sourceDir: Directory
    var ndkVersion: String? = null     // null = auto-detect
    var buildType: String = "Release"
    var platform: Int = 21
    var stl: String = "c++_shared"
    var stripSymbols: Boolean = true
    var arguments: Map<String, String> = emptyMap()
    var includeSubDirs: List<String> = emptyList()  // manual override for header dirs
}
```

## Error Handling

| Scenario | Behavior |
|---|---|
| NDK not found | Fail before build with clear message: "NDK not found. Set cmake.ndkVersion or ANDROID_NDK_HOME" |
| cmake command unavailable | Fail early with "cmake not found on PATH. Install CMake or set NDK path" |
| CMakeLists.txt missing at sourceDir | Fail early with path in error message |
| CMake build failure for any ABI | Gradle task fails (non-zero exit), subsequent tasks skipped |
| No output artifacts after build | Warn: "No library files found in {path}" |
| llvm-strip not available | Warn + skip strip, build continues |
| cmake block + manual sourceLibsDir | cmake wins, warn that manual value was overridden |

## Backward Compatibility

- No `cmake { }` → all existing behavior preserved, nothing changes
- `sourceLibsDir` / `sourceIncsDir` become nullable internally; validation only enforces them as required when `cmake { }` is absent
- `dependsOn` mechanism still works alongside cmake: user can add extra pre-cmake dependencies if needed

## Test Strategy

| Layer | What | How |
|---|---|---|
| Unit | `PrefabCmakeExtension` defaults and validation | JUnit |
| Unit | NDK path resolution (multi-source priority) | Mock env vars + `local.properties` |
| Integration | Full pipeline: cmake → modules → aar | Gradle TestKit with minimal CMake project (`libhello`) |
| Regression | Existing behavior without cmake block | Existing tests pass unchanged |
