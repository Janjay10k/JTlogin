# JTLogin migration report

What changed from LibreLogin 0.24.0 (the uploaded `LibreLogin-master.zip`), what was deliberately *not* changed, what
was verified, and how to move an existing installation over safely.

**Honest summary of the state of this work:** the source was rebranded and adapted, unit tests for the new
dependency-free code pass, and a dependency-less compiler run shows the rebrand did not break any internal reference.
The project has **not** been built with Gradle, and no server or proxy has been started with it, because the authoring
environment could not reach Gradle, Maven Central, PaperMC or Mojang. See [COMPATIBILITY.md](COMPATIBILITY.md).

---

## 1. What was architecturally decided

| Question | Finding | Decision |
|---|---|---|
| Proxy plugin, server plugin or both? | Multi-platform: Velocity, BungeeCord and Paper in one shaded JAR, sharing the code under `jtlogin.common`. | Keep it. |
| Which Minecraft versions does the proxy handle? | The proxy platform (not the plugin) provides protocol support. Proxy-side, the plugin is version independent except for the captcha map packet. | Keep one code path. |
| Where is server-side code needed? | Only on Paper standalone: login encryption through server internals, and PacketEvents. | Isolated in `ConnectionEncryption`, PacketEvents bumped. |
| One JAR or several? | Java 21 bytecode runs on Java 21 and Java 25 runtimes. One JAR is simplest. | One JAR. Fallback if Velocity 4.x fails in testing: a second artifact for Velocity 4.x. |
| Java target | The original build already produced Java 21 bytecode (toolchain 21, no `--release`); its README claim of "Java 17+" was stale. | `--release 21` made explicit. BungeeCord on Java 17 cannot run the JAR, as before. |

## 2. Rebrand: what was renamed

| Area | LibreLogin | JTLogin |
|---|---|---|
| Java package (plugin and API) | `xyz.kyngs.librelogin` | `jtlogin` |
| Classes | `*LibreLogin*` (e.g. `LibreLoginPlugin`, `AuthenticLibreLogin`) | `*JTLogin*` (e.g. `JTLoginPlugin`, `AuthenticJTLogin`) |
| Plugin name / id | `LibreLogin` / `librelogin` | `JTLogin` / `jtlogin` |
| Data folder | `plugins/LibreLogin` (Velocity: `plugins/librelogin`) | `plugins/JTLogin` (Velocity: `plugins/jtlogin`) |
| Staff command | `/librelogin` | `/jtlogin` (old name kept as an alias) |
| Permissions | `librepremium.*` (staff), `librelogin.*` (4 nodes) | `jtlogin.*` (old nodes still accepted, see below) |
| Database type ids (new installs) | `librelogin-sqlite` / `-mysql` / `-postgresql` | `jtlogin-sqlite` / `-mysql` / `-postgresql` |
| LuckPerms contexts | `librelogin-authorized`, `librelogin-awaiting2fa` | `jtlogin-authorized`, `jtlogin-awaiting2fa` |
| Paper limbo world generator | `librelogin:void` | `jtlogin:void` |
| Gradle | project `LibreLogin`, jar `LibreLogin.jar` | project `JTLogin`, jar `JTLogin.jar`, version `1.0.0-SNAPSHOT` |
| Relocation prefix | `xyz.kyngs.librelogin.lib` | `jtlogin.lib` |
| Messages, config headers, docs | LibreLogin | JTLogin, with attribution to LibreLogin and kyngs |

The rename was produced by a script (`scripts/rebrand.py`, kept as an audit record; it is not needed to build and
must not be re-run on this tree). It protects the identifiers in section 3, then a dependency-less `javac` run over all 189 source
files was compared with the same run on the original sources: **1,549 errors each, identical after normalising names,
and no syntax errors**. Those errors are all unresolved third-party types, which is expected without the jars.
This proves the rename did not break an internal reference. It does not prove the project compiles.

## 3. Deliberately NOT renamed, so that existing installations keep working

| Identifier | Why it stays | How it is handled |
|---|---|---|
| Database table `librepremium_data` | It is the schema. Renaming it would orphan every account. | Unchanged. `SourceGuardsTest` fails if it is renamed. The SQL provider is identical to the original apart from branding. |
| `database.type` values `librelogin-*` | Stored in users' `config.conf`. | Still registered, identical to the new `jtlogin-*` ids. |
| LuckPerms context keys `librelogin-authorized`, `librelogin-awaiting2fa` | Existing rules (for example "deny unless authorized") would stop matching, which can silently stop restricting unauthenticated players. | Both the old and the new keys are published. |
| Permission nodes (`librepremium.*`, `librelogin.dump`, ...) | Stored in permission plugins. | An explicit table of exactly 18 nodes (`LegacyNames.PERMISSIONS`). Either node works. No prefix matching, so `librepremium.*` cannot unlock anything it did not before. ACF's `@CommandPermission` uses comma as AND, so aliasing is done by overriding `CommandManager#hasPermission` in the three platform managers instead. |
| `/librelogin` command | Scripts, muscle memory, other plugins. | Registered as an alias of `/jtlogin`. |
| Data folder content | Config, messages, SQLite database, forbidden passwords. | Copied by `LegacyDataMigrator`, see section 6. |
| Attribution | License requirement and honesty. | `@author` tags, `author: kyngs` in the plugin descriptors, `NOTICE.md`, `LICENSE`, `licenses/FASTLOGIN_LICENSE`, the `/jtlogin about` text. |
| Links to the upstream wiki | The configuration is compatible, so those pages still apply. | Kept, and labelled as the upstream LibreLogin wiki. |

## 4. Behaviour changes and removals (all deliberate, all disclosed)

| Change | Reason |
|---|---|
| **Update checker removed.** It called the GitHub Releases API of the original repository. | JTLogin has no release feed. The blanket rename had turned that URL into an invented `kyngs/JTLogin` repository, and keeping the original would have told JTLogin users to "update" to LibreLogin builds. |
| **bStats reporting removed** (and its three libraries). | The plugin ids belong to the original author. Reporting under them would pollute their statistics, and JTLogin has no ids of its own. |
| **Forbidden-passwords list is bundled in the JAR** instead of downloaded from the original repository at startup. | Avoids fetching a password list from a repository JTLogin does not control. The bundled `forbidden-passwords-template.txt` has only 2 entries while the full list has 1,018,876, so shipping only the template would have **silently weakened password validation**. That regression was caught in review. The full list is now packaged as `forbidden-passwords-full.txt` (the JAR grows by about 3.5 MB), and a loud warning is logged if it is ever missing. |
| Publishing configuration removed (Modrinth/Polymart/GitHub release upload, the author's Maven repository, Woodpecker CI). | These are the original author's credentials and targets. Replaced by a credential-free GitHub Actions workflow. |
| Maven publication of the API is local only (`publishToMavenLocal`). | No invented repository URL. |
| The Velocity runtime-library folder now lives under `plugins/jtlogin`. | Follows the rename; libraries are simply downloaded again. |
| Default `database.type` for new installs is `jtlogin-sqlite`. | Existing configs keep their stored value. |
| API package renamed to `jtlogin.api`. | **Breaking for integrating plugins**: update imports and the dependency. |
| Config comment said the SQLite default file is `database.db`. It is `user-data.db`. | Documentation fix. |

## 5. Dependency and version-specific changes

| Item | Change | Evidence | Status |
|---|---|---|---|
| PacketEvents (Paper port) | `2.7.0` to `2.14.0` | `v2.14.0` is the first tag with the 26.3 client version and packet table; every PacketEvents class/constructor/accessor JTLogin uses exists at master with the same signature | artifact resolution and runtime **unverified** |
| Captcha map packet ids | ids for protocol 768-777 added (`MapPacketIds`) | derived from PacketEvents packet tables; the same method reproduces all 11 ids already used for 1.13-1.21.1; body layout unchanged since 1.17 | not confirmed with a real client |
| Paper login encryption | `ConnectionEncryption`: setter found by parameter types, ciphers built with the JDK (AES/CFB8, key as IV) | PaperMC documents that 26.1+ has no remapper and exposes Mojang names; the old code used Spigot names (`MinecraftEncryption`, method `a`) in a static initializer that would have failed at class load | known-answer test against OpenSSL and fake-class tests pass; not run on a server |
| Adventure 5 | `Title.Times.of` to `Title.Times.times` | removed in Adventure 5.0, replacement exists since 4.10 | not run |
| Paper manifest | `paperweight-mappings-namespace: mojang` | documented Paper mechanism | not run |
| Java | explicit `--release 21` for API and Plugin | see section 1 | applied by Gradle, not run |
| Tests | JUnit 5 added to the Plugin module | | 38 pass in the authoring environment |
| Left as in LibreLogin | Velocity API `3.4.0-SNAPSHOT`, Paper API `1.21.4`, Protocolize `2.4.2`, Floodgate API, ACF fork commit `7d5bf7cac0`, Adventure 4.14 pins | compiling against the older APIs is the safer direction for binary compatibility | **unverified** on 26.x; see the risk list in COMPATIBILITY.md |

## 6. Safe data migration

On startup, before any configuration or database file is touched, `LegacyDataMigrator`:

1. does nothing if `.migrated-from-legacy` already exists in the JTLogin folder (repeatable and idempotent);
2. looks for `LibreLogin`, then `LibrePremium` (and the lowercase Velocity spellings) next to the JTLogin folder and
   uses the first one that has content;
3. **copies, never moves**: the legacy folder is left byte-for-byte untouched;
4. **never overwrites** a file that already exists in the JTLogin folder: if the content differs it is reported as a
   conflict, the JTLogin copy is kept and the legacy copy stays where it is;
5. writes each file under a temporary name, verifies it against the source by SHA-256, then moves it into place;
6. skips the library cache (`lib/`) and `LICENSE.txt`, which are rewritten by the plugin;
7. on any failure removes the files it created, writes no marker, and **aborts startup with a clear error**. Starting
   anyway would create a new, empty database next to the existing one.

Covered by `LegacyDataMigratorTest` (10 tests, run against disposable temporary directories). It has not been run
against a real LibreLogin installation. With MySQL or PostgreSQL the copied `config.conf` points at the same database,
so no account data is copied or changed at all.

## 7. Moving an existing installation

**Back up first. Do this with the server stopped.**

1. Stop the proxy/server.
2. Copy the data folder somewhere safe, for example
   `cp -a plugins/LibreLogin /safe/place/LibreLogin-backup` (Velocity: `plugins/librelogin`).
3. External database: `mysqldump --single-transaction -u USER -p DATABASE librepremium_data > backup.sql`, or
   `pg_dump -t librepremium_data DATABASE > backup.sql`. SQLite lives in the data folder (`user-data.db` by default),
   which step 2 already copied.
4. Remove `LibreLogin.jar` from `plugins/`. **Do not run both plugins together.**
5. Add `JTLogin.jar` and start.
6. Check the log for `Found existing data in 'LibreLogin'...` and `Migrated N file(s)...`. If you see
   `Can't migrate the existing data folder`, nothing was changed in the old folder; fix the reported problem and start again.
7. Verify: `/jtlogin user info <a known player>`, log in with an existing account, check `database.type`, then restart
   once and confirm accounts and sessions persist.
8. Update permission groups and LuckPerms rules to the new names at your own pace. The old names keep working.
   Remember that revoking means removing both names.

**Rollback**

1. Stop the server. Remove `JTLogin.jar`, put back `LibreLogin.jar`. The old folder was never modified.
2. SQLite: accounts created or changed while JTLogin was running are in `plugins/JTLogin/user-data.db`, not in the old
   folder. To keep them, copy that file over `plugins/LibreLogin/user-data.db` (after backing up the latter).
3. MySQL/PostgreSQL: the data is shared, so nothing to copy. Restore from `backup.sql` only if you want the exact
   pre-migration state.
4. Schema: JTLogin runs the same startup schema checks as LibreLogin 0.24.0, which (as in LibreLogin) add missing columns
   (`secret`, `ip`, `last_authentication`, `last_server`, `email`) if an older database lacks them. Added columns are
   not removed on rollback, and the old plugin ignores them.

## 8. Security review

What was reviewed, and what that does and does not cover. This is a review of the changes made in this port, not an
audit of LibreLogin.

| Topic | Result |
|---|---|
| Authentication logic | Unchanged. The only edit near it is the `Title.Times` call in the login/register title. |
| Password hashes, UUIDs, usernames, sessions, 2FA data | Not touched; the SQL provider is identical to the original apart from branding. |
| Credentials in logs | The password-hiding log filter was kept and now lists both `/jtlogin` and `/librelogin` explicitly (it already matched broadly through its `l` and `log` entries). No new logging of secrets was added. |
| Permissions | Legacy nodes are matched from an explicit table only. Caveat: either name is enough to grant, so denying only the new name does not revoke a legacy grant. |
| LuckPerms contexts | Old keys still published, so old restrictions keep matching. |
| Password strength | A regression I introduced (tiny bundled list) was found and fixed; the full list ships in the JAR. |
| Network endpoints | Removed: update check, password-list download, bStats. A source guard test fails if these come back. Remaining outbound calls are the original's: Mojang session/profile APIs and the UUID/profile lookup services. |
| Server internals | The only access is `ConnectionEncryption`. It fails loudly (kick plus log) rather than continuing without encryption if the connection class has an unknown shape. |
| Supply chain | `libby` downloads runtime libraries from Maven repositories on first start, as before. Dependency versions were not changed except PacketEvents. Checksums and signatures were not verified. |
| Not done | No penetration test, no fuzzing, no review of upstream code outside the diff, no verification that dependency coordinates resolve. |

## 9. Tests

Run in the authoring environment (JDK 21, JUnit 5.10): **38 passed, 0 failed**, listed in COMPATIBILITY.md. Re-run with
`./gradlew :Plugin:test`.

Not run and therefore **not claimed**: the Gradle build, every platform class, every database provider, authentication,
TOTP, premium login, sessions, Floodgate, routing, RedisBungee, any server or proxy startup.

## 10. Known limitations

- API consumers must change imports (`xyz.kyngs.librelogin.api` to `jtlogin.api`).
- BungeeCord users on Java 17 cannot run the JAR (it targets Java 21, as the original build already did).
- Velocity 3.x cannot serve 26.3 (the proxy lacks the protocol); use Velocity 4.x (Java 25) for that.
- See the open-risk list in [COMPATIBILITY.md](COMPATIBILITY.md) for everything that could still invalidate the
  one-JAR design.
