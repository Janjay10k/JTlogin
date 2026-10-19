# Vendored libby Gradle plugin

This folder is a copy of **libby-gradle-plugin 1.2.1** by [kyngs](https://github.com/kyngs), licensed under the
Mozilla Public License 2.0 (see [LICENSE](LICENSE)). Upstream: https://github.com/kyngs/libby-gradle-plugin
(archived by its owner on 2026-08-19, which says it was merged into https://github.com/kyngs/libby).

## Why it is here

JTLogin's build applies the Gradle plugin `xyz.kyngs.libby.plugin` 1.2.1. That version could no longer be downloaded from
`repo.kyngs.xyz/gradle-plugins` (the plugin marker exists, the plugin jar it points to is gone), so every build failed with
`Could not find libby-gradle-plugin:plugin:1.2.1`. The plugin is only a build tool: it writes the `libby.json` file into the
JTLogin jar, which `configureFromJSON()` reads at startup.

## What was changed

- `src/main/java/xyz/kyngs/libby/plugin/*.java`: **not changed**, copied as they are in the upstream archive.
- `build.gradle.kts`, `settings.gradle.kts`: new, a reduced version of the upstream `plugin/build.gradle.kts`
  (no publishing to the author's repository, no license-header plugin, no javadoc/sources jars, single project).

## How it is used

The root `settings.gradle` includes this folder with `pluginManagement { includeBuild("build-plugins/libby-gradle-plugin") }`,
and `Plugin/build.gradle.kts` applies `id("xyz.kyngs.libby.plugin")` (no version, Gradle takes it from this folder).

## Status

Not built or tested in the authoring environment (no Gradle or network access there). The first GitHub Actions run is the
first real check.
