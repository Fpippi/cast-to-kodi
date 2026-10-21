#!/usr/bin/env bash
# Legge da CHANGELOG.md la versione più recente (la prima sezione "## X.Y.Z"), la stampa e ne scrive le novità nel
# file $1. Si ferma se la sezione è vuota o se il tag vX.Y.Z esiste già: un rilascio senza versione nuova
# rifarebbe la stessa release. La usano apk.yml (rilascio) e test.yml (PR verso main, per accorgersene prima).
set -euo pipefail

version=$(grep -m1 -oE '^## [0-9]+\.[0-9]+\.[0-9]+' CHANGELOG.md | cut -c4-) || {
  echo "::error::In CHANGELOG.md manca una sezione \"## X.Y.Z\"" >&2; exit 1; }

awk '/^## /{n++; next} n==1' CHANGELOG.md > "$1"
if ! grep -q '[^[:space:]]' "$1"; then
  echo "::error::La sezione $version di CHANGELOG.md è vuota: scrivi le novità" >&2; exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/v$version" > /dev/null; then
  echo "::error::La versione $version è già uscita: aggiungi in cima a CHANGELOG.md la sezione della nuova" >&2; exit 1
fi

echo "$version"
