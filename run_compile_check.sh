#!/usr/bin/env bash
set -euo pipefail

LOGFILE="/Users/tony/git/grid/compile_check_$(date +%Y%m%d_%H%M%S).log"
RESULTFILE="/Users/tony/git/grid/compile_check_result.txt"
START_SHA="e83787c9869b88ecfafeadd4b69c8734db210601"

# Get original HEAD to restore later
ORIG_HEAD=$(git rev-parse HEAD)
ORIG_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "HEAD")

echo "Starting compile sweep at $(date)" | tee "$LOGFILE"
echo "Start SHA: $START_SHA" | tee -a "$LOGFILE"
echo "Original branch: $ORIG_BRANCH ($ORIG_HEAD)" | tee -a "$LOGFILE"

# Get commits oldest-first (exclusive of the start SHA itself)
COMMITS=$(git log --oneline --reverse "${START_SHA}..HEAD" | awk '{print $1}')
TOTAL=$(echo "$COMMITS" | wc -l | tr -d ' ')

echo "Total commits to check: $TOTAL" | tee -a "$LOGFILE"

# Restore branch on exit
cleanup() {
  echo "" | tee -a "$LOGFILE"
  echo "Restoring original branch: $ORIG_BRANCH" | tee -a "$LOGFILE"
  git checkout -q "$ORIG_HEAD" 2>/dev/null || true
  if [ "$ORIG_BRANCH" != "HEAD" ]; then
    git checkout -q "$ORIG_BRANCH" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

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
    echo "Logfile: $LOGFILE" >> "$RESULTFILE"
    echo "" | tee -a "$LOGFILE"
    echo "Done. First failure: $SHA" | tee -a "$LOGFILE"
    exit 1
  fi
done

echo "" | tee -a "$LOGFILE"
echo "All $TOTAL commits compiled successfully!" | tee -a "$LOGFILE"
echo "SUCCESS: all $TOTAL commits passed" > "$RESULTFILE"
