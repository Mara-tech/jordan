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

`jordan-client` depends on `jordan-core` via a project reference (`project(':jordan-core')`). There is no published artifact yet — both modules must be built together from source.

## Relation to the Android app

The Android app (`app/android/`) currently embeds copies of the DTOs and utilities that live in `jordan-core`. Migrating the Android app to depend on `jordan-core` as a library module is the intended next step.
