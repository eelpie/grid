#!/bin/bash
set -euo pipefail

REPO=/Users/tony/git/grid
GOOD_COMMIT="4016ef1d4e9135b811c3485e45b21df1a4b495bb"  # merge-base main..HEAD
BISECT_LOG=/tmp/bisect_compile_run.log
SBT=/opt/homebrew/bin/sbt

cd "$REPO"

HEAD_COMMIT=$(git rev-parse HEAD)

echo "=== sbt compile bisect ==="
echo "Good (merge-base): $GOOD_COMMIT"
echo "Head:              $HEAD_COMMIT"
echo ""

echo "Checking if HEAD compiles..."
if $SBT compile > "$BISECT_LOG" 2>&1; then
  echo "HEAD compiles — no compile failure in this branch."
  exit 0
fi
echo "HEAD fails to compile. Proceeding with bisect."
echo ""

echo "Verifying merge-base compiles..."
git checkout --quiet "$GOOD_COMMIT"
if ! $SBT compile >> "$BISECT_LOG" 2>&1; then
  echo "ERROR: merge-base $GOOD_COMMIT also fails to compile. Cannot bisect from here."
  git checkout --quiet "$HEAD_COMMIT"
  exit 1
fi
echo "Merge-base compiles OK."
echo ""
git checkout --quiet "$HEAD_COMMIT"

# Run bisect
git bisect reset 2>/dev/null || true
git bisect start
git bisect bad "$HEAD_COMMIT"
git bisect good "$GOOD_COMMIT"

# Capture bisect run output to find the first bad commit
BISECT_OUTPUT=$(git bisect run $SBT compile 2>&1 | tee /tmp/bisect_output.log)
FIRST_BAD=$(echo "$BISECT_OUTPUT" | grep -m1 "^[0-9a-f]\{40\} is the first bad commit" | awk '{print $1}')

git bisect reset

echo ""
echo "=== RESULT ==="
if [ -n "$FIRST_BAD" ]; then
  echo "First compile failure:"
  git log --oneline -1 "$FIRST_BAD"
  echo ""
  echo "Parent commit (should compile OK):"
  git log --oneline -1 "${FIRST_BAD}^"
else
  echo "Could not determine first bad commit from bisect output."
  echo "Check /tmp/bisect_output.log for details."
fi
