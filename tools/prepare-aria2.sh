#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ref=290755e646f62fb26ed4112f6ea2c0e5d6cbea82
while read -r abi sum; do
  f=src/main/jniLibs/$abi/libaria2c.so
  mkdir -p "$(dirname "$f")"
  if ! echo "$sum  $f" | sha256sum --check --status 2>/dev/null; then
    curl --fail --location --silent --show-error -H 'Accept: application/vnd.github.raw+json' "https://api.github.com/repos/devgianlu/aria2lib/contents/src/main/jniLibs/$abi/libaria2c.so?ref=$ref" -o "$f.tmp"
    echo "$sum  $f.tmp" | sha256sum --check && mv "$f.tmp" "$f"
  fi
  echo "$sum  $f" | sha256sum --check
done <<'HASHES'
arm64-v8a 88b8b5feec7e4e7392f129fed4cf61f52a88fdc5dfe1e1464ed467fb41bd1a7f
armeabi-v7a 7556bfad1e6cd4780acc768be1a5a0f064d8dd60d463e2b109c821fc227548fd
x86 009de6b12b6af8da77600c11968b0fd85dc8be8279e30536e10fee28c595fcbb
x86_64 69040afa14cfb54a82af35702e3b50478c73366e44a100abb88e710ff93265c2
HASHES
