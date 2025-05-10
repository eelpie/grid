#!/bin/bash
set -euo pipefail

REPO=/Users/tony/git/grid
RESULTS="$REPO/compile_check_results.log"
CURRENT_BRANCH=$(git -C "$REPO" rev-parse --abbrev-ref HEAD)

cd "$REPO"

echo "Compile check results - $(date)" > "$RESULTS"
echo "Branch: $CURRENT_BRANCH" >> "$RESULTS"
echo "---" >> "$RESULTS"

PASS=0
FAIL=0
FIRST_FAIL=""

COMMITS=$(git log --reverse --format="%H %s" e891be36150f01bd6a13db6e294b366dd7b8c49e^..HEAD)

while IFS= read -r line; do
  HASH=$(echo "$line" | cut -d' ' -f1)
  MSG=$(echo "$line" | cut -d' ' -f2-)
  SHORT="${HASH:0:9}"

  echo -n "Checking $SHORT $MSG ... "
  git checkout "$HASH" --quiet 2>/dev/null

  if /opt/homebrew/bin/sbt compile > "/tmp/sbt_${SHORT}.log" 2>&1; then
    echo "PASS"
    echo "PASS: $SHORT $MSG" >> "$RESULTS"
    PASS=$((PASS+1))
  else
    EXIT_CODE=$?
    echo "FAIL (exit $EXIT_CODE)"
    echo "FAIL($EXIT_CODE): $SHORT $MSG" >> "$RESULTS"
    FAIL=$((FAIL+1))
    if [ -z "$FIRST_FAIL" ]; then
      FIRST_FAIL="$SHORT $MSG"
    fi
  fi
done <<< "$COMMITS"

echo ""
echo "Restoring branch: $CURRENT_BRANCH"
git checkout "$CURRENT_BRANCH" --quiet

echo "---" >> "$RESULTS"
echo "SUMMARY: PASS=$PASS FAIL=$FAIL" >> "$RESULTS"
if [ -n "$FIRST_FAIL" ]; then
  echo "First failure: $FIRST_FAIL" >> "$RESULTS"
fi

echo ""
echo "=== SUMMARY ==="
echo "PASS: $PASS  FAIL: $FAIL"
if [ -n "$FIRST_FAIL" ]; then
  echo "First failure: $FIRST_FAIL"
fi
echo "Full results: $RESULTS"
