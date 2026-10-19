#!/usr/bin/env python3
"""
Startup smoke test for JTLogin.

Starts a real server/proxy with the built JAR and checks that the plugin is loaded and enabled without errors.
This covers ONLY "plugin startup". It does not log in a player, so it says nothing about authentication,
premium login, TOTP, Floodgate or routing. Those need a protocol-level test client and are not automated here.

Usage:
  smoke_test.py --platform paper      --version 26.2   --jar Plugin/build/libs/JTLogin.jar
  smoke_test.py --platform velocity   --version-prefix 3.  --jar ...
  smoke_test.py --platform bungeecord --jar ...

Exit codes: 0 = PASSED, 1 = FAILED, 2 = SKIPPED (no server build available, nothing was tested).

Downloads use the PaperMC "Fill" v3 API (https://docs.papermc.io/misc/downloads-api), which requires a custom
User-Agent that contains a contact URL or e-mail. Set SMOKE_USER_AGENT, or run inside GitHub Actions where it is
derived from GITHUB_REPOSITORY. BungeeCord is downloaded from the project's own CI server.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from pathlib import Path

FILL = "https://fill.papermc.io/v3/projects"
BUNGEE_URL = "https://ci.md-5.net/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar"

# Standard messages written by each platform when a plugin is enabled / fails.
ENABLED = {
    "paper": ["Enabling JTLogin"],
    "velocity": ["Loaded plugin jtlogin"],
    "bungeecord": ["Enabled plugin JTLogin"],
}
FAILURE_MARKERS = [
    "Error occurred while enabling JTLogin",
    "Could not load 'plugins/JTLogin",
    "Can't create plugin jtlogin",
    "Unable to load plugin JTLogin",
    "Exception in thread",
    "ExceptionInInitializerError",
    "NoSuchMethodError",
    "NoClassDefFoundError",
    "LinkageError",
]


def user_agent() -> str:
    agent = os.environ.get("SMOKE_USER_AGENT")
    if not agent and os.environ.get("GITHUB_REPOSITORY"):
        agent = f"JTLogin-CI/1.0 (https://github.com/{os.environ['GITHUB_REPOSITORY']})"
    if not agent:
        sys.exit("Set SMOKE_USER_AGENT to a string containing a contact URL or e-mail (required by the PaperMC downloads API).")
    return agent


def http_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": user_agent()})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def download(url: str, target: Path, send_agent: bool = True):
    headers = {"User-Agent": user_agent()} if send_agent else {"User-Agent": "JTLogin-smoke-test/1.0"}
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=300) as response, open(target, "wb") as out:
        shutil.copyfileobj(response, out)


def pick_fill_download(project: str, version: str):
    """Returns (version, build_id, channel, url) for the newest build of the version, preferring STABLE."""
    try:
        builds = http_json(f"{FILL}/{project}/versions/{version}/builds")
    except Exception as e:  # unknown version, API change, network
        print(f"[smoke] cannot list builds for {project} {version}: {e}")
        return None
    if not builds:
        return None
    stable = [b for b in builds if b.get("channel") == "STABLE"]
    chosen = (stable or builds)[0]
    url = chosen.get("downloads", {}).get("server:default", {}).get("url")
    if not url:
        return None
    return version, chosen.get("id"), chosen.get("channel"), url


def newest_version_with_prefix(project: str, prefix: str):
    meta = http_json(f"{FILL}/{project}")
    versions = meta.get("versions", {})
    flat = []
    for group in versions.values() if isinstance(versions, dict) else [versions]:
        flat.extend(group)
    for version in flat:  # the API lists newest first
        if version.startswith(prefix):
            return version
    return None


def evaluate(platform: str, log: str):
    """Pure function, unit-testable: returns (passed, reasons)."""
    reasons = []
    if not any(marker in log for marker in ENABLED[platform]):
        reasons.append("plugin enable message not found in the log")
    for marker in FAILURE_MARKERS:
        if marker in log:
            reasons.append(f"found failure marker: {marker}")
    return (not reasons), reasons


def run_server(platform: str, workdir: Path, java: str, timeout: int):
    if platform == "paper":
        (workdir / "eula.txt").write_text("eula=true\n")
        (workdir / "server.properties").write_text("online-mode=false\nserver-port=25599\n")
        command = [java, "-Xmx1G", "-jar", "server.jar", "--nogui"]
        ready, stop = "Done (", "stop\n"
    elif platform == "velocity":
        command = [java, "-Xmx512M", "-jar", "server.jar"]
        ready, stop = "Done (", "shutdown\n"
    else:
        command = [java, "-Xmx512M", "-jar", "server.jar"]
        ready, stop = "Listening on", "end\n"

    process = subprocess.Popen(command, cwd=workdir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True)
    lines, started = [], threading.Event()

    def reader():
        for line in process.stdout:
            lines.append(line)
            if ready in line:
                started.set()

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()
    started.wait(timeout)
    time.sleep(5)  # let late plugin errors show up
    try:
        process.stdin.write(stop)
        process.stdin.flush()
        process.wait(60)
    except Exception:
        process.kill()
    thread.join(5)
    return "".join(lines), started.is_set()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", required=True, choices=["paper", "velocity", "bungeecord"])
    parser.add_argument("--version")
    parser.add_argument("--version-prefix")
    parser.add_argument("--jar", required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    jar = Path(args.jar).resolve()
    if not jar.is_file():
        sys.exit(f"plugin jar not found: {jar}")

    workdir = Path(tempfile.mkdtemp(prefix=f"jtlogin-smoke-{args.platform}-"))
    (workdir / "plugins").mkdir()
    shutil.copy(jar, workdir / "plugins" / "JTLogin.jar")

    description = args.platform
    if args.platform == "bungeecord":
        download(BUNGEE_URL, workdir / "server.jar", send_agent=False)
        description += " (latest CI build)"
    else:
        project = args.platform
        version = args.version
        if not version and args.version_prefix:
            version = newest_version_with_prefix(project, args.version_prefix)
        if not version:
            print(f"SKIPPED: no {project} version selected/found (prefix={args.version_prefix})")
            sys.exit(2)
        picked = pick_fill_download(project, version)
        if picked is None:
            print(f"SKIPPED: no downloadable {project} build for {version}. Nothing was tested.")
            sys.exit(2)
        version, build, channel, url = picked
        print(f"[smoke] {project} {version} build {build} ({channel})")
        download(url, workdir / "server.jar")
        description += f" {version} build {build} ({channel})"

    print(f"[smoke] starting {description} in {workdir}")
    log, reached_ready = run_server(args.platform, workdir, args.java, args.timeout)
    passed, reasons = evaluate(args.platform, log)
    if not reached_ready:
        passed = False
        reasons.append("server did not report readiness within the timeout")

    (workdir / "smoke.log").write_text(log)
    print("\n".join(log.splitlines()[-60:]))
    if passed:
        print(f"PASSED (startup only): {description}")
        sys.exit(0)
    print(f"FAILED: {description}")
    for reason in reasons:
        print(" - " + reason)
    print(f"full log: {workdir / 'smoke.log'}")
    sys.exit(1)


if __name__ == "__main__":
    main()
