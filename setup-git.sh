#!/usr/bin/env bash
set -e

ROOT="$(git rev-parse --show-toplevel)"
git -C "$ROOT" config --local core.excludesFile "$ROOT/.afinityignore"

echo "AFinity ignore rules configured."