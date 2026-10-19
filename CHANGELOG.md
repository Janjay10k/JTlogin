# JTLogin changelog

## 1.0.0-SNAPSHOT (unreleased, never built)

First JTLogin source, a rebrand and compatibility port of LibreLogin 0.24.0 (by kyngs, MPL-2.0).

- Rebrand to JTLogin: package `jtlogin`, classes, plugin name/id, `/jtlogin`, permissions, messages, Gradle artifacts.
- Existing LibreLogin installations are supported: the data folder is copied (never moved or overwritten, verified,
  rolled back on failure, startup aborted rather than creating an empty database); the table `librepremium_data`,
  `database.type` ids `librelogin-*`, old permission nodes, `/librelogin` and the old LuckPerms context keys are kept.
- Target range Minecraft 1.21.11 to 26.3, source-level only, see COMPATIBILITY.md:
  - captcha map packet ids for protocol 768 to 777 (previously only up to 1.21.1)
  - Paper login encryption no longer depends on Spigot-mapped names (needed for Paper 26.1+, which has no remapper)
  - `Title.Times.of` replaced by `Title.Times.times` (removed in Adventure 5: Velocity 4.x, Paper 26.x)
  - PacketEvents 2.7.0 to 2.14.0 (first release with 26.3)
  - explicit `--release 21`, Paper manifest `paperweight-mappings-namespace: mojang`
- Removed: update checker, bStats reporting, remote forbidden-passwords download (the full list is now bundled),
  the original author's publishing configuration and CI.
- Added: unit tests (JUnit 5), `scripts/smoke_test.py`, GitHub Actions workflow, COMPATIBILITY.md, MIGRATION_REPORT.md, NOTICE.md.
- Breaking for API consumers: `xyz.kyngs.librelogin.api` is now `jtlogin.api`.

## Upstream LibreLogin history (as shipped in the uploaded source)

0.24.0 - 1.21.1 - 1.21.4 Support

- Add support for 1.21.4
- Add support for Java 23
- Fix "logged in from another location" issue on Paper (see GH #296)