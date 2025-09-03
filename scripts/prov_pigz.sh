#!/usr/bin/env bash
set -euo pipefail

CLI=./build/bin/macosArm64/debugExecutable/zlib-cli.kexe

if ! command -v pigz >/dev/null 2>&1; then
  echo "pigz not found. Please install pigz (e.g., brew install pigz)." >&2
  exit 1
fi

sizes=(0 1 32 1024 65536 1048576)

echo "[PROV-PIGZ] disabling logs for throughput"
$CLI log-off || true

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

pass=0
fail=0

for sz in "${sizes[@]}"; do
  raw="$tmpdir/rnd_${sz}.bin"
  zz="$tmpdir/rnd_${sz}.zz"
  # generate random data deterministically if possible
  if [ "$sz" -eq 0 ]; then
    : > "$raw"
  else
    # macOS: use head -c sz /dev/urandom
    head -c "$sz" /dev/urandom > "$raw"
  fi
  pigz -z -c "$raw" > "$zz"
  echo "[PROV-PIGZ] size=$sz: $zz vs $raw"
  if $CLI prov-zlib "$zz" "$raw" | sed -n '1,4p' ; then
    pass=$((pass+1))
  else
    fail=$((fail+1))
  fi
done

echo "[PROV-PIGZ] summary: passed=$pass failed=$fail"

