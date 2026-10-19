# Notice and attribution

**JTLogin is a modified version of LibreLogin.** LibreLogin (formerly LibrePremium) was written by
[kyngs](https://github.com/kyngs) and the LibreLogin contributors and is licensed under the Mozilla Public
License 2.0. This repository is a rebrand and compatibility port of that project. It does not claim that the original
code was written by JTLogin, and the original authorship is kept in the source (`@author` tags), in the plugin
descriptors (`author`), in `/jtlogin about`, and here.

## License

Everything in this repository that derives from LibreLogin remains under the **Mozilla Public License 2.0**, the full
text is in [LICENSE](LICENSE). Files modified or added in this port carry the same MPL 2.0 header (`HEADER.txt`).
The MPL requires that the source of modified files stays available under the same license. This repository is that source.

## Third-party code

- **FastLogin** (games647 and contributors): parts of the Paper port, notably `EncryptionUtil` and the login
  handshake handling, are derived from FastLogin. Its license is in [licenses/FASTLOGIN_LICENSE](licenses/FASTLOGIN_LICENSE).
- **Raixo**: original BungeeCord port of LibrePremium.
- **libby-gradle-plugin 1.2.1** (kyngs, MPL-2.0): a build tool that writes `libby.json`. Its published artifact can no longer be
  downloaded, so its source is vendored unchanged in [build-plugins/libby-gradle-plugin](build-plugins/libby-gradle-plugin/README-JTLOGIN.md)
  with its license. Only the generated `libby.json` ends up in the JTLogin jar.
- Runtime libraries (ACF fork, HikariCP, JDBC drivers, Caffeine, Configurate, bcrypt, totp, BouncyCastle, Adventure,
  PacketEvents, ...) are downloaded by their original coordinates at first start and keep their own licenses.
- The bundled `forbidden-passwords-full.txt` is the upstream list (see its header for its sources).

## What this port changed

See [MIGRATION_REPORT.md](MIGRATION_REPORT.md), section 2 (rename), section 4 (removals) and section 5 (compatibility changes).
