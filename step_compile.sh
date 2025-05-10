#!/usr/bin/env bash
set -euo pipefail

LOGFILE="/Users/tony/git/grid/step_compile.log"
RESULTFILE="/Users/tony/git/grid/step_compile_result.txt"
START_SHA="c5068b1b2db830ed3ee19787caf7e7173a98395f"

# Get original HEAD to restore later
ORIG_HEAD=$(git rev-parse HEAD)
ORIG_BRANCH=$(git rev-parse --abbrev-ref HEAD)

echo "Starting compile sweep at $(date)" | tee "$LOGFILE"
echo "Original branch: $ORIG_BRANCH ($ORIG_HEAD)" | tee -a "$LOGFILE"

# Get commits oldest-first
COMMITS=$(git log --oneline --reverse "${START_SHA}..HEAD" | awk '{print $1}')
TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')

echo "Total commits to check: $TOTAL" | tee -a "$LOGFILE"

N=0
for SHA in $COMMITS; do
  N=$((N + 1))
  MSG=$(git log --oneline -1 "$SHA")
  echo "" | tee -a "$LOGFILE"
  echo "[$N/$TOTAL] Checking out $MSG" | tee -a "$LOGFILE"
  git checkout -q "$SHA"

  if sbt --batch compile >> "$LOGFILE" 2>&1; then
    echo "  ✓ compile OK" | tee -a "$LOGFILE"
  else
    echo "" | tee -a "$LOGFILE"
    echo "  ✗ COMPILE FAILED at commit $N/$TOTAL: $MSG" | tee -a "$LOGFILE"
    echo "FAILED: $SHA $MSG" > "$RESULTFILE"
    echo "" | tee -a "$LOGFILE"
    echo "Restoring original branch..." | tee -a "$LOGFILE"
    git checkout -q "$ORIG_BRANCH"
    echo "Done. First failure: $SHA" | tee -a "$LOGFILE"
    exit 1
  fi
done

echo "" | tee -a "$LOGFILE"
echo "All $TOTAL commits compiled successfully!" | tee -a "$LOGFILE"
echo "SUCCESS: all $TOTAL commits passed" > "$RESULTFILE"
git checkout -q "$ORIG_BRANCH"
