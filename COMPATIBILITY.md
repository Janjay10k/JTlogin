# JTLogin compatibility matrix

**Read this first.** Every test-status cell below says *not executed*. The environment this port was prepared in had
no access to Gradle, Maven Central, PaperMC or Mojang, so JTLogin has **never been compiled as a whole and never been
started on any server or proxy**. What exists is *source-level evidence* (the platforms' own sources declare support
for a protocol version, and the APIs JTLogin calls exist there) plus a small set of unit tests. Nothing here is a
promise that Minecraft 1.21.11 to 26.3 works. It is a map of what is expected to work and what still has to be proven.

## Levels used

| Level | Meaning |
|---|---|
| Source evidence | The platform's public source declares the protocol version, and the library/API members JTLogin uses exist in it. Says nothing about runtime behaviour. |
| Compiled | `./gradlew :Plugin:shadowJar` succeeds against the pinned dependencies. |
| Startup | The plugin loads and enables on that platform/version without errors (`scripts/smoke_test.py`). |
| Auth | A real client can register/log in through it (password, premium auto-login, TOTP, Floodgate where applicable). |
| E2E | Routing (limbo/lobby), sessions, database backends, migrations and integrations all behave end to end. |

## Release inventory (Minecraft Java Edition, 1.21.11 to 26.3)

Stable releases in the requested range: **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3**. Compiled from several
independent sites that agree with each other. Mojang's own version manifest could not be fetched from the authoring
environment, so this list is *unverified against the primary source*; re-check it with
`https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`.

| Release | Protocol | Server Java | Min. PacketEvents (Paper port) | Captcha map packet id |
|---|---|---|---|---|
| 1.21.11 | 774 | 21 | 2.11.0 | 0x31 |
| 26.1 / 26.1.1 / 26.1.2 | 775 | 25 | 2.12.0 | 0x33 |
| 26.2 | 776 | 25 | 2.13.0 | 0x33 |
| 26.3 | 777 | 25 | 2.14.0 | 0x34 |

## Artifact

One artifact covers the whole range: **`Plugin/build/libs/JTLogin.jar`**, Java 21 bytecode (`--release 21`).
Java 21 is the lowest runtime in the range (Minecraft 1.21.11, Velocity 3.x), and Java 21 bytecode runs unchanged on
the Java 25 runtimes that Minecraft 26.x and Velocity 4.x require. No multi-release JAR and no per-version artifact
is needed *for that reason*. Whether that holds for Velocity 4.x (Adventure 5) is exactly what "Open risks" below is
about. If it fails in testing, the fallback is a second artifact for Velocity 4.x.

## Matrix

"Platform Java" is what the platform itself requires. All four test columns are *not executed*; a CI run
(`.github/workflows/ci.yml`) fills in *Compiled* and *Startup*. *Auth* and *E2E* are not automated at all.

| Minecraft | Protocol | Entry point | Platform line | Platform Java | Artifact | Source evidence | Compiled | Startup | Auth | E2E |
|---|---|---|---|---|---|---|---|---|---|---|
| 1.21.11 | 774 | Paper (standalone) | Paper 1.21.11 | 21 | `JTLogin.jar` | branch `ver/1.21.11` exists; PacketEvents >= 2.11.0 knows this version (pinned 2.14.0) | not executed | not executed | not executed | not executed |
| 1.21.11 | 774 | Velocity 3.x | 3.5.x line | 21 | `JTLogin.jar` | protocol present in Velocity 3.x `ProtocolVersion` (`dev/3.0.0` head); a released 3.5.x may support fewer, check its notes | not executed | not executed | not executed | not executed |
| 1.21.11 | 774 | Velocity 4.x | 4.x line | 25 | `JTLogin.jar` | protocol present in Velocity 4.x `ProtocolVersion` (`dev/4.0.0` head) | not executed | not executed | not executed | not executed |
| 1.21.11 | 774 | BungeeCord | current builds | >= 17 (platform), >= 21 (JTLogin.jar) | `JTLogin.jar` | protocol present in BungeeCord `ProtocolConstants` (master, 2026-10-10) | not executed | not executed | not executed | not executed |
| 26.1 | 775 | Paper (standalone) | Paper 26.1 | 25 | `JTLogin.jar` | only branch `ver/26.1.2` exists; builds for 26.1 itself not verified; PacketEvents >= 2.12.0 knows this version (pinned 2.14.0) | not executed | not executed | not executed | not executed |
| 26.1 | 775 | Velocity 3.x | 3.5.x line | 21 | `JTLogin.jar` | protocol present in Velocity 3.x `ProtocolVersion` (`dev/3.0.0` head); a released 3.5.x may support fewer, check its notes | not executed | not executed | not executed | not executed |
| 26.1 | 775 | Velocity 4.x | 4.x line | 25 | `JTLogin.jar` | protocol present in Velocity 4.x `ProtocolVersion` (`dev/4.0.0` head) | not executed | not executed | not executed | not executed |
| 26.1 | 775 | BungeeCord | current builds | >= 17 (platform), >= 21 (JTLogin.jar) | `JTLogin.jar` | protocol present in BungeeCord `ProtocolConstants` (master, 2026-10-10) | not executed | not executed | not executed | not executed |
| 26.1.1 | 775 | Paper (standalone) | Paper 26.1.1 | 25 | `JTLogin.jar` | only branch `ver/26.1.2` exists; builds for 26.1.1 itself not verified; PacketEvents >= 2.12.0 knows this version (pinned 2.14.0) | not executed | not executed | not executed | not executed |
| 26.1.1 | 775 | Velocity 3.x | 3.5.x line | 21 | `JTLogin.jar` | protocol present in Velocity 3.x `ProtocolVersion` (`dev/3.0.0` head); a released 3.5.x may support fewer, check its notes | not executed | not executed | not executed | not executed |
| 26.1.1 | 775 | Velocity 4.x | 4.x line | 25 | `JTLogin.jar` | protocol present in Velocity 4.x `ProtocolVersion` (`dev/4.0.0` head) | not executed | not executed | not executed | not executed |
| 26.1.1 | 775 | BungeeCord | current builds | >= 17 (platform), >= 21 (JTLogin.jar) | `JTLogin.jar` | protocol present in BungeeCord `ProtocolConstants` (master, 2026-10-10) | not executed | not executed | not executed | not executed |
| 26.1.2 | 775 | Paper (standalone) | Paper 26.1.2 | 25 | `JTLogin.jar` | branch `ver/26.1.2` exists; PacketEvents >= 2.12.0 knows this version (pinned 2.14.0) | not executed | not executed | not executed | not executed |
| 26.1.2 | 775 | Velocity 3.x | 3.5.x line | 21 | `JTLogin.jar` | protocol present in Velocity 3.x `ProtocolVersion` (`dev/3.0.0` head); a released 3.5.x may support fewer, check its notes | not executed | not executed | not executed | not executed |
| 26.1.2 | 775 | Velocity 4.x | 4.x line | 25 | `JTLogin.jar` | protocol present in Velocity 4.x `ProtocolVersion` (`dev/4.0.0` head) | not executed | not executed | not executed | not executed |
| 26.1.2 | 775 | BungeeCord | current builds | >= 17 (platform), >= 21 (JTLogin.jar) | `JTLogin.jar` | protocol present in BungeeCord `ProtocolConstants` (master, 2026-10-10) | not executed | not executed | not executed | not executed |
| 26.2 | 776 | Paper (standalone) | Paper 26.2 | 25 | `JTLogin.jar` | branch `ver/26.2` exists; PacketEvents >= 2.13.0 knows this version (pinned 2.14.0) | not executed | not executed | not executed | not executed |
| 26.2 | 776 | Velocity 3.x | 3.5.x line | 21 | `JTLogin.jar` | protocol present in Velocity 3.x `ProtocolVersion` (`dev/3.0.0` head); a released 3.5.x may support fewer, check its notes | not executed | not executed | not executed | not executed |
| 26.2 | 776 | Velocity 4.x | 4.x line | 25 | `JTLogin.jar` | protocol present in Velocity 4.x `ProtocolVersion` (`dev/4.0.0` head) | not executed | not executed | not executed | not executed |
| 26.2 | 776 | BungeeCord | current builds | >= 17 (platform), >= 21 (JTLogin.jar) | `JTLogin.jar` | protocol present in BungeeCord `ProtocolConstants` (master, 2026-10-10) | not executed | not executed | not executed | not executed |
| 26.3 | 777 | Paper (standalone) | Paper 26.3 | 25 | `JTLogin.jar` | `main` is 26.3; PacketEvents >= 2.14.0 knows this version (pinned 2.14.0) | not executed | not executed | not executed | not executed |
| 26.3 | 777 | Velocity 3.x | - | - | - | **not supported**: protocol 777 is absent from Velocity 3.x (`dev/3.0.0` head) | n/a | n/a | n/a | n/a |
| 26.3 | 777 | Velocity 4.x | 4.x line | 25 | `JTLogin.jar` | protocol present in Velocity 4.x `ProtocolVersion` (`dev/4.0.0` head) | not executed | not executed | not executed | not executed |
| 26.3 | 777 | BungeeCord | current builds | >= 17 (platform), >= 21 (JTLogin.jar) | `JTLogin.jar` | protocol present in BungeeCord `ProtocolConstants` (master, 2026-10-10) | not executed | not executed | not executed | not executed |

## What was actually run (authoring environment, JDK 21)

38 unit tests, all passing, covering only code that has no third-party dependencies:

| Test class | Tests | Covers |
|---|---|---|
| `LegacyPermissionsTest` | 9 | legacy/new permission nodes, AND semantics, no cross-granting |
| `LegacyDataMigratorTest` | 10 | copy, never overwrite, idempotence, conflicts, chain order, rollback on failure |
| `MapPacketIdsTest` | 4 | map packet ids for protocol 768-777 are complete and non-overlapping |
| `ConnectionEncryptionTest` | 8 | AES/CFB8 against an OpenSSL known answer; setter found by parameter types under three mapping shapes |
| `SourceGuardsTest` | 7 | no removed Adventure calls, no upstream endpoints, DB table name unchanged, legacy ids/contexts kept |

The guards were mutation-checked (re-introducing a removed call, renaming the table, adding an unmapped permission
each makes a test fail). In addition, a dependency-less `javac` run over the whole tree has the same error set as
the original LibreLogin sources after normalising names, so the rebrand broke no internal reference.

**Not run:** compilation against the real dependencies, the Gradle build, every platform class, every database
provider, every authentication path, Floodgate, TOTP, premium login, session handling, routing, RedisBungee.

## Source evidence behind the matrix

Read from the projects' public repositories in October 2026:

- **Velocity** (`PaperMC/Velocity`): `ProtocolVersion` enum on `dev/3.0.0` (3.6.0-SNAPSHOT) ends at 26.2 (protocol 776);
  on `dev/4.0.0` (4.2.1-SNAPSHOT) it also has 26.3 (777). Java 21 vs Java 25 and Adventure 4.26.1 (3.5.x) vs 5.2.0
  (3.6.0-SNAPSHOT, 4.x) taken from the same repository's build files and history. Velocity's plugin class loader is
  parent-first in both lines.
- **BungeeCord** (`SpigotMC/BungeeCord`, 2026-10-10): `ProtocolConstants` lists 774 to 777; compiled for Java 17.
- **Paper** (`PaperMC/Paper`, `main` at a90108f, 2026-10-17): `mcVersion=26.3`; version branches `ver/1.21.11`,
  `ver/26.1.2`, `ver/26.2`. PaperMC's documentation states that from 26.1 the plugin remapper is gone and reflection
  sees Mojang names, which is why the one server-internals access was rewritten (see below).
- **PacketEvents** (`retrooper/packetevents`): tags `v2.11.0` (1.21.11), `v2.12.0` (26.1), `v2.13.0` (26.2) and
  `v2.14.0` (26.3) are the first to contain those client versions. Every PacketEvents class, constructor and accessor
  JTLogin uses exists at master with the same signature.
- **Adventure** (`KyoriPowered/adventure`): `Title.Times.of` is deprecated for removal in 4.14.0/4.26.1 and absent in
  5.0.0/5.2.0; `Title.Times.times` exists since 4.10.0. This was the only call to a removed Adventure API found in
  the types JTLogin uses (checked against APIs that 4.14.0 marks as scheduled for removal).
- **ACF fork** (`kyngs/commands` at the pinned commit): all permission checks end in the public, non-final
  `CommandManager#hasPermission(CommandIssuer, String)`, and a comma in `@CommandPermission` means AND.

## Version-specific code, isolated

| Concern | Where | What changed | Evidence level |
|---|---|---|---|
| Captcha map packet ids (proxies, via Protocolize) | `MapPacketIds`, used by `MapDataPacket` | ids added for protocol 768-777 (previously only up to 767) | derived from PacketEvents tables; the same method reproduces all 11 ids LibreLogin already had; **not confirmed with a real client** |
| Login encryption on Paper (server internals) | `ConnectionEncryption` | no mapped names any more: setter found by parameter types, ciphers built with the JDK | known-answer test + fake classes; **not run on a real server** |
| Adventure 5 | `AuthenticAuthorizationProvider` | `Title.Times.of` to `Title.Times.times` | checked against Adventure sources; **not run** |
| Paper mappings namespace | `Plugin/build.gradle.kts` manifest | `paperweight-mappings-namespace: mojang` so Paper 1.21.11 does not remap the jar | documented Paper mechanism; **not run** |

## Open risks (things that can still break the claim)

1. **Velocity 4.x and Adventure 5.** On Velocity, JTLogin runs against Velocity's own Adventure (the plugin class
   loader is parent-first), and JTLogin is compiled against the Adventure 4 API. Only one removed call was found, but a
   source audit is not a binary-compatibility test.
2. **ACF fork on Velocity 4.x.** Command handling uses a pinned fork of ACF compiled against the Velocity 3 API.
   Nothing here shows it works on Velocity 4.x.
3. **Dependency resolution.** The versions in the build (notably PacketEvents 2.14.0, the Velocity API
   `3.4.0-SNAPSHOT`, Protocolize 2.4.2, Floodgate API) have not been resolved. A wrong coordinate fails the first build.
4. **Protocolize and Floodgate** must support the newer protocols themselves. JTLogin cannot fix that.
5. **Libraries loaded at runtime** (libby) are pinned to Adventure 4.14 for BungeeCord. Paper and Velocity put their
   own Adventure first.
6. **`paper-plugin.yml` / `plugin.yml`** keep their old `api-version`. Paper 26.x behaviour for those values is untested.
7. **Release list** not checked against Mojang's manifest, and Paper builds for 26.1 and 26.1.1 may not exist.
8. **PacketEvents 2.14.0 and the forced Adventure 4.14.** The build forces Adventure 4.14.0 onto PacketEvents' transitive
   dependencies because BungeeCord needs it. On Paper the server's own Adventure is loaded first, but whether PacketEvents
   2.14.0 runs correctly in that setup is untested. If it does not, the Paper port needs its own dependency configuration.
9. **Velocity internals.** The optional NanoLimbo integration compiles against Velocity proxy internals
   (`velocity-proxy:3.2.0-SNAPSHOT-277`). The members used exist at the heads of both Velocity lines, but internals are not a stable API.

## How to fill in the matrix

```bash
./gradlew :API:build :Plugin:test :Plugin:shadowJar            # Compiled (+ the unit tests)
python3 scripts/smoke_test.py --platform paper --version 26.2 --jar Plugin/build/libs/JTLogin.jar --java java
python3 scripts/smoke_test.py --platform velocity --version-prefix 4. --jar Plugin/build/libs/JTLogin.jar
python3 scripts/smoke_test.py --platform bungeecord --jar Plugin/build/libs/JTLogin.jar
```

`scripts/smoke_test.py` needs `SMOKE_USER_AGENT` set to a string with a contact URL or e-mail (a PaperMC downloads API
requirement). It tests *startup only*. Exit code 2 means "no server build existed, nothing was tested". Record
Auth and E2E by hand: register, log in, log out and back in within the session window, TOTP, premium auto-login,
Floodgate, limbo to lobby routing, then restart and check the account is still there.
