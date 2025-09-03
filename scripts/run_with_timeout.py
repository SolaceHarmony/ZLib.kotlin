#!/usr/bin/env python3
"""
Run a command with a wall-clock timeout (macOS-friendly).

- Sends SIGTERM on timeout, then (after --grace) SIGKILL.
- Streams stdout/stderr directly to the parent console.

Usage:
  ./scripts/run_with_timeout.py --timeout 60 -- ./build/bin/macosArm64/debugExecutable/zlib-cli.kexe prov-dir /path 100
"""
import argparse
import os
import signal
import subprocess
import sys
import time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--timeout", type=int, required=True, help="seconds before timeout")
    ap.add_argument("--grace", type=int, default=5, help="grace seconds before SIGKILL after SIGTERM")
    ap.add_argument("--cwd", default=None, help="working directory")
    ap.add_argument("--env", action="append", default=[], help="env var in KEY=VAL form; can be repeated")
    ap.add_argument("--", dest="cmdsep", action="store_true")
    ap.add_argument("cmd", nargs=argparse.REMAINDER, help="command and args")
    args = ap.parse_args()

    if not args.cmd:
        print("No command specified", file=sys.stderr)
        return 2

    # Strip leading '--' if present
    if args.cmd and args.cmd[0] == "--":
        args.cmd = args.cmd[1:]

    env = os.environ.copy()
    for kv in args.env:
        if "=" in kv:
            k, v = kv.split("=", 1)
            env[k] = v

    start = time.time()
    proc = subprocess.Popen(args.cmd, cwd=args.cwd, env=env)
    timed_out = False
    try:
        while True:
            ret = proc.poll()
            if ret is not None:
                break
            if time.time() - start > args.timeout:
                timed_out = True
                break
            time.sleep(0.25)
    except KeyboardInterrupt:
        proc.terminate()
        raise

    if timed_out:
        print(f"[TIMEOUT] {args.timeout}s exceeded. Sending SIGTERM...", file=sys.stderr)
        try:
            proc.terminate()
        except Exception:
            pass
        # Wait grace period
        deadline = time.time() + args.grace
        while time.time() < deadline:
            if proc.poll() is not None:
                break
            time.sleep(0.25)
        if proc.poll() is None:
            print("[TIMEOUT] Escalating to SIGKILL", file=sys.stderr)
            try:
                proc.kill()
            except Exception:
                pass
        return 124  # common timeout exit code
    else:
        return proc.returncode


if __name__ == "__main__":
    sys.exit(main())

