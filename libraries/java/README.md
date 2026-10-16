# Jordan Java libraries

This directory contains two Gradle modules built as a multi-module project.

| Module | Description |
|---|---|
| [`jordan-core`](jordan-core/README.md) | Shared DTOs, constants, and utilities (Java 8+) |
| [`jordan-client`](jordan-client/README.md) | Passive-client library — register, send status, read messages (Java 11+) |

## Building

A Gradle wrapper is not committed. Generate it once:

```bash
cd libraries/java
gradle wrapper --gradle-version 8.5
```

Then for all subsequent builds:

```bash
./gradlew build          # build both modules
./gradlew test           # run all tests
./gradlew :jordan-core:test     # jordan-core tests only
./gradlew :jordan-client:test   # jordan-client tests only
```

## Module dependency

```
jordan-client  →  jordan-core
```

`jordan-client` depends on `jordan-core` via a project reference (`api project(':jordan-core')`). In
the published POM it becomes a `compile` dependency on `com.mara.jordan:jordan-core` of the same version:
`api` rather than `implementation`, because client code uses `jordan-core` types (`JordanConstants`).

## Publishing

Both modules publish to GitHub Packages, `https://maven.pkg.github.com/Mara-tech/jordan`:

| Coordinates | |
|---|---|
| `com.mara.jordan:jordan-core:<version>` | jar, sources jar, POM, Gradle module metadata |
| `com.mara.jordan:jordan-client:<version>` | same |

The two are versioned together: `version` in [gradle.properties](gradle.properties), overridden for one
build with `-Pversion=<x.y.z>`. The configuration shared by both modules is
[publishing.gradle](publishing.gradle), which each `build.gradle` applies after the `maven-publish`
plugin.

```bash
# from libraries/java/ — a token with the write:packages scope
GITHUB_ACTOR=<github login> GITHUB_TOKEN=<token> bash gradlew publish
```

The credentials can also be the Gradle properties `gpr.user` and `gpr.key` (in
`~/.gradle/gradle.properties`, never in the repository). They are only read by the publish tasks, so a
build or a test run needs none. A version already published cannot be published again: bump it first.

To check what would be published without credentials, publish to the local Maven repository:

```bash
bash gradlew publishToMavenLocal    # ~/.m2/repository/com/mara/jordan/
```

A consumer declares the repository — GitHub Packages asks for a token with `read:packages` even to read a
public package — then the dependency:

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/Mara-tech/jordan')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}
dependencies {
    implementation 'com.mara.jordan:jordan-client:1.0.0'   // brings jordan-core
}
```

A `java/v*` tag publishes nothing yet: the release workflow is JRD-5.

## Relation to the Android app

The Android app (`app/android/`) currently embeds copies of the DTOs and utilities that live in `jordan-core`. Migrating the Android app to depend on `jordan-core` as a library module is the intended next step.
