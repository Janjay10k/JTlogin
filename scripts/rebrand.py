#!/usr/bin/env python3
"""
Mechanical LibreLogin -> JTLogin rebrand.

Phase 1 (this script) is purely mechanical:
  * move the Java package   xyz.kyngs.librelogin  ->  jtlogin
  * rename files/classes    *LibreLogin*          ->  *JTLogin*
  * rewrite branding tokens (LibreLogin/libreLogin/LIBRELOGIN/librelogin)

Identifiers that are PERSISTED in users' data/configs/permission plugins are
protected from the blanket replace and handled deliberately by hand afterwards:
  * the database table name `librepremium_data`
  * config values  database.type = librelogin-{mysql,sqlite,postgresql}
  * LuckPerms context keys librelogin-authorized / librelogin-awaiting2fa
  * upstream URLs / third-party repo hosts / original-author attribution
Permission nodes are mapped to canonical jtlogin.* names here; the legacy
nodes are honoured at runtime through LegacyNames (see Plugin sources).
"""
import os
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve()

TEXT_EXT = {".java", ".kts", ".gradle", ".yml", ".yaml", ".md", ".json", ".properties", ".txt"}
SKIP_NAMES = {"forbidden-passwords.txt", "LICENSE.txt", "LICENSE", "gradlew", "gradlew.bat"}
SKIP_DIRS = {".git", "licenses", "gradle"}

# --- 1. protected tokens (restored verbatim after the blanket replace) ---------
PROTECTED = [
    "librepremium_data",
    "librelogin-mysql",
    "librelogin-sqlite",
    "librelogin-postgresql",
    "librelogin-authorized",
    "librelogin-awaiting2fa",
    "github.com/kyngs/LibreLogin",
    "github.com/kyngs/LibrePremium",
    "repo.kyngs.xyz",
]

# --- 2. permission nodes: exact, explicit map (no prefix matching) -------------
PERMISSION_MAP = {
    # historic "librelogin." nodes
    "librelogin.dump": "jtlogin.dump",
    "librelogin.email.test": "jtlogin.email.test",
    "librelogin.limbo.access": "jtlogin.limbo.access",
    "librelogin.user.alts": "jtlogin.user.alts",
    # historic "librepremium." staff nodes
    "librepremium.reload.configuration": "jtlogin.reload.configuration",
    "librepremium.reload.messages": "jtlogin.reload.messages",
    "librepremium.user.2faoff": "jtlogin.user.2faoff",
    "librepremium.user.cracked": "jtlogin.user.cracked",
    "librepremium.user.delete": "jtlogin.user.delete",
    "librepremium.user.emailoff": "jtlogin.user.emailoff",
    "librepremium.user.info": "jtlogin.user.info",
    "librepremium.user.login": "jtlogin.user.login",
    "librepremium.user.migrate": "jtlogin.user.migrate",
    "librepremium.user.pass-change": "jtlogin.user.pass-change",
    "librepremium.user.premium": "jtlogin.user.premium",
    "librepremium.user.register": "jtlogin.user.register",
    "librepremium.user.setemail": "jtlogin.user.setemail",
    "librepremium.user.unregister": "jtlogin.user.unregister",
}


def is_text(path: Path) -> bool:
    return path.suffix in TEXT_EXT and path.name not in SKIP_NAMES


def rewrite(text: str) -> str:
    # permissions first (quoted literals only)
    for old, new in PERMISSION_MAP.items():
        text = text.replace('"%s"' % old, '"%s"' % new)

    holders = {}
    for i, tok in enumerate(PROTECTED):
        key = "\u0001KEEP%d\u0002" % i
        holders[key] = tok
        text = text.replace(tok, key)

    text = text.replace("xyz.kyngs.librelogin", "jtlogin")
    text = text.replace("xyz/kyngs/librelogin", "jtlogin")
    text = text.replace("LibreLogin", "JTLogin")
    text = text.replace("libreLogin", "jtLogin")
    text = text.replace("LIBRELOGIN", "JTLOGIN")
    text = text.replace("librelogin", "jtlogin")

    for key, tok in holders.items():
        text = text.replace(key, tok)
    return text


def main():
    # remove upstream-specific CI/publishing (credentials/targets belong to the original author)
    shutil.rmtree(ROOT / ".woodpecker", ignore_errors=True)

    # move package directories
    for module in ("API", "Plugin"):
        base = ROOT / module / "src" / "main" / "java"
        old = base / "xyz" / "kyngs" / "librelogin"
        new = base / "jtlogin"
        if old.exists():
            shutil.move(str(old), str(new))
            # prune now-empty xyz/kyngs
            for d in (base / "xyz" / "kyngs", base / "xyz"):
                try:
                    d.rmdir()
                except OSError:
                    pass

    # rewrite file contents
    changed = 0
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            p = Path(dirpath) / fn
            if not is_text(p):
                continue
            try:
                s = p.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            n = rewrite(s)
            if n != s:
                p.write_text(n, encoding="utf-8")
                changed += 1

    # rename files whose names carry the old brand (deepest first)
    renamed = []
    for dirpath, dirnames, filenames in os.walk(ROOT, topdown=False):
        if ".git" in Path(dirpath).parts:
            continue
        for fn in filenames:
            if "LibreLogin" in fn:
                src = Path(dirpath) / fn
                dst = Path(dirpath) / fn.replace("LibreLogin", "JTLogin")
                src.rename(dst)
                renamed.append((fn, dst.name))

    print("rewrote %d files, renamed %d files" % (changed, len(renamed)))
    for a, b in sorted(renamed):
        print("  %s -> %s" % (a, b))


if __name__ == "__main__":
    main()
