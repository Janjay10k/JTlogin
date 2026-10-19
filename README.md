# JTLogin

JTLogin is an open-source, multiplatform authentication plugin for Minecraft Java Edition servers and proxies.
It is a **rebrand and port of [LibreLogin](https://github.com/kyngs/LibreLogin) by kyngs** (formerly LibrePremium),
licensed under the Mozilla Public License 2.0. See [NOTICE.md](NOTICE.md) for attribution.

> ## Status: unbuilt source port, read this first
>
> This source tree was prepared in an environment with **no access to Gradle, Maven Central, PaperMC or Mojang**,
> so it has **never been compiled as a whole, and no server or proxy has ever been started with it**.
> What *was* verified is listed, with its limits, in [COMPATIBILITY.md](COMPATIBILITY.md) and
> [MIGRATION_REPORT.md](MIGRATION_REPORT.md). Until the CI workflow in `.github/workflows/ci.yml` (or your own
> build) has run and you have tested a login on your own platform, treat every "supported" statement as a
> *source-level expectation*, not a tested fact.

## Features (inherited from LibreLogin, unchanged)

- Automatic login for premium (online-mode) players
- TOTP two-factor authentication (Authy, Google Authenticator, ...)
- Session system
- Name validation (including case sensitivity check)
- Automatic data migration for premium players who change their username
- Migration of a player's data with one command
- Bedrock support through Floodgate, including account linking
- Limbo and lobby routing, least-players lobby selection, health checks
- Public API and integration hooks
- Database backends: SQLite, MySQL, PostgreSQL
- Migration from other authentication plugins
- RedisBungee / multi-proxy support
- Consideration: when used on a proxy, you must secure your limbo server.

## Platforms and versions

One shaded JAR (`JTLogin.jar`) contains the Velocity, BungeeCord and Paper entry points.

| Platform | Minecraft | Notes |
|---|---|---|
| Velocity 3.x | 1.21.11 to 26.2 | needs Java 21 |
| Velocity 4.x | 1.21.11 to 26.3 | needs Java 25 |
| BungeeCord (current builds) | 1.21.11 to 26.3 | the JTLogin jar itself needs Java 21+ |
| Paper (standalone, no proxy) | 1.21.11, 26.1.x, 26.2, 26.3 | Java 21 for 1.21.11, Java 25 for 26.x |

This table says what the platforms' own sources declare. **Nothing in it has been tested with JTLogin.**
The detailed matrix, with the test status of every combination, is in [COMPATIBILITY.md](COMPATIBILITY.md).

## Requirements

- Java 21 or newer at runtime (the JAR is compiled for Java 21; Java 25 runtimes run it as well).
- Optional integrations are detected at runtime: Floodgate, LuckPerms, Protocolize (captcha maps on proxies),
  RedisBungee, NanoLimbo.
- The first start downloads the plugin's runtime libraries from Maven repositories, so the host needs internet access.

## Installation

1. Stop the server/proxy.
2. Put `JTLogin.jar` into `plugins/`.
3. **If you are upgrading from LibreLogin, read the migration notes below first and remove `LibreLogin.jar`.**
   Never run both plugins at the same time.
4. Start, then edit `plugins/JTLogin/config.conf` (Velocity: `plugins/jtlogin/config.conf`).

On Paper without a proxy the server must run with `online-mode=false`; JTLogin refuses to start in online mode and
behind a proxy.

## Upgrading from LibreLogin

JTLogin copies an existing `plugins/LibreLogin` (Velocity: `plugins/librelogin`) folder, or the older `LibrePremium`
folder, into its own data folder on first start. The old folder is **never modified or deleted**, so rolling back
means putting the old jar back. Your database table keeps its name (`librepremium_data`), `database.type` values
such as `librelogin-sqlite` keep working, and old permission nodes, the `/librelogin` command and the old LuckPerms
context keys are still honoured.

Back up first. Full instructions, including backup and rollback, are in [MIGRATION_REPORT.md](MIGRATION_REPORT.md).

## Commands and permissions

Staff command: `/jtlogin` (the old alias `/librelogin` still works). Subcommands and the permission each one needs:

| Subcommand | Permission | Legacy permission still accepted |
|---|---|---|
| `dump` | `jtlogin.dump` | `librelogin.dump` |
| `email test` | `jtlogin.email.test` | `librelogin.email.test` |
| `user alts` | `jtlogin.user.alts` | `librelogin.user.alts` |
| `reload configuration` | `jtlogin.reload.configuration` | `librepremium.reload.configuration` |
| `reload messages` | `jtlogin.reload.messages` | `librepremium.reload.messages` |
| `user info` | `jtlogin.user.info` | `librepremium.user.info` |
| `user login` | `jtlogin.user.login` | `librepremium.user.login` |
| `user register` | `jtlogin.user.register` | `librepremium.user.register` |
| `user unregister` | `jtlogin.user.unregister` | `librepremium.user.unregister` |
| `user delete` | `jtlogin.user.delete` | `librepremium.user.delete` |
| `user pass-change` | `jtlogin.user.pass-change` | `librepremium.user.pass-change` |
| `user migrate` | `jtlogin.user.migrate` | `librepremium.user.migrate` |
| `user premium` | `jtlogin.user.premium` | `librepremium.user.premium` |
| `user cracked` | `jtlogin.user.cracked` | `librepremium.user.cracked` |
| `user setemail` | `jtlogin.user.setemail` | `librepremium.user.setemail` |
| `user emailoff` | `jtlogin.user.emailoff` | `librepremium.user.emailoff` |
| `user 2faoff` | `jtlogin.user.2faoff` | `librepremium.user.2faoff` |
| (Paper) access to the limbo world | `jtlogin.limbo.access` | `librelogin.limbo.access` |

Legacy nodes are matched one by one from an explicit table, so a wildcard such as `librepremium.*` does not unlock
anything it did not unlock before. Either the new or the old node is enough, so to *revoke* access remove both.
(`about` has no entry here because it is not in the table of permission nodes; check `/jtlogin about` on your build.)

Player commands (unchanged): `/login` (`/l`, `/log`), `/register` (`/reg`), `/2fa`, `/2faconfirm`,
`/changepassword`, `/premium`, `/premiumconfirm`, `/cracked`, `/setemail`, `/verifyemail`, `/resetpassword`,
`/confirmpasswordreset`.

### LuckPerms contexts

JTLogin publishes `jtlogin-authorized` and `jtlogin-awaiting2fa`, **and** the old `librelogin-authorized` and
`librelogin-awaiting2fa`, so that existing rules keep matching.

## Configuration

Generated on first start: `config.conf`, `messages.conf`, `forbidden-passwords.txt`.
Configuration written by LibreLogin is compatible, and the upstream
[LibreLogin documentation](https://github.com/kyngs/LibreLogin/wiki) describes the same options.

`database.type` accepts `jtlogin-sqlite`, `jtlogin-mysql`, `jtlogin-postgresql` and the legacy
`librelogin-sqlite`, `librelogin-mysql`, `librelogin-postgresql` (identical behaviour).

The full forbidden-passwords list (about one million entries) ships inside the JAR. JTLogin downloads nothing at
startup except its runtime libraries.

## Building

Requires **JDK 21** and internet access. The bundled Gradle 8.8 cannot run on JDK 25, so build with 21.

```bash
./gradlew :API:build :Plugin:test :Plugin:shadowJar
# result: Plugin/build/libs/JTLogin.jar
```

Install the API into your local Maven repository: `./gradlew :API:publishToMavenLocal`.
No remote repository or credentials are configured on purpose.

CI (`.github/workflows/ci.yml`) builds, runs the unit tests and starts real servers/proxies with the JAR as a startup
smoke test. It does not test logging in.

## API

Package `jtlogin.api` (was `xyz.kyngs.librelogin.api`). **This is a breaking change for plugins that integrate
with LibreLogin: update the imports and the dependency.** For example `LibreLoginPlugin` is now `JTLoginPlugin`.

## Credits

- [kyngs](https://github.com/kyngs) and the LibreLogin contributors, original authors
- [Raixo](https://github.com/RAIXOCZ), original BungeeCord port
- [FastLogin contributors](https://github.com/games647/FastLogin), whose work was the base for the Paper port
- [Fejby](https://github.com/Fejby), Floodgate test server and testing

## License

Mozilla Public License 2.0, see [LICENSE](LICENSE). Third-party notices are in `licenses/` and [NOTICE.md](NOTICE.md).
