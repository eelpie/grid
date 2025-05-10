#!/usr/bin/env bash
set -euo pipefail

START="${1:-9a361c8e6713a7e3118149cf48b2eed21bcff599}"
ORIGINAL=$(git rev-parse HEAD)
LOG=/tmp/sbt_compile_scan.log

COMMITS=()
while IFS= read -r line; do
  COMMITS+=("$line")
done < <(git log --oneline --reverse "${START}..multi-tenant" | awk '{print $1}')

TOTAL=${#COMMITS[@]}
echo "Scanning $TOTAL commits from $(git log --oneline -1 $START)"
echo ""

COUNT=0
for COMMIT in "${COMMITS[@]}"; do
  COUNT=$((COUNT + 1))
  MSG=$(git log --oneline -1 "$COMMIT")
  echo -n "[$COUNT/$TOTAL] $MSG ... "
  git checkout --quiet "$COMMIT"
  if ! sbt compile > "$LOG" 2>&1; then
    echo "FAIL"
    echo ""
    echo "FIRST COMPILE FAILURE: $MSG"
    echo "--- Last 25 lines of sbt output ---"
    tail -25 "$LOG"
    git checkout --quiet "$ORIGINAL"
    exit 0
  fi
  echo "PASS"
done

echo ""
echo "All $TOTAL commits compiled OK"
git checkout --quiet "$ORIGINAL"
