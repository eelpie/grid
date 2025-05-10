#!/bin/bash
# Finds the first commit on this branch that fails to compile.
# Uses `sbt clean compile` at every step to avoid stale incremental cache issues.
# Results written to /tmp/bisect_result.log

set -euo pipefail

REPO=/Users/tony/git/grid
SBT=/opt/homebrew/bin/sbt
GOOD=1d28b0f5029186436344289e0933092400e53916   # merge-base with main
BAD=e1cf4921f74a63a6cdca189384e3c70faa07d261    # HEAD of branch
LOG=/tmp/bisect_result.log

# Per-step script: kill stale sbt, clear stale sockets, then clean compile.
BISECT_SCRIPT=/tmp/bisect_step.sh
cat > "$BISECT_SCRIPT" << 'STEP'
#!/bin/bash
pkill -f "sbt-launch.jar" 2>/dev/null || true
sleep 2
find ~/.sbt/1.0/server -name "*.sock" -delete 2>/dev/null || true
echo "[bisect step] $(git rev-parse --short HEAD) - $(git log --oneline -1 HEAD)"
/opt/homebrew/bin/sbt clean compile
STEP
chmod +x "$BISECT_SCRIPT"

# Helper: kill stale sbt, clear sockets, then run sbt clean compile
sbt_clean_compile() {
  pkill -f "sbt-launch.jar" 2>/dev/null || true
  sleep 2
  find ~/.sbt/1.0/server -name "*.sock" -delete 2>/dev/null || true
  $SBT clean compile
}

cd "$REPO"
> "$LOG"

echo "=== sbt clean compile bisect ===" | tee -a "$LOG"
echo "Good (merge-base): $GOOD" | tee -a "$LOG"
echo "Bad (HEAD):        $BAD" | tee -a "$LOG"
echo "" | tee -a "$LOG"

echo "[$(date)] Verifying HEAD fails to compile..." | tee -a "$LOG"
git checkout --quiet "$BAD"
if sbt_clean_compile >> "$LOG" 2>&1; then
  echo "HEAD compiles — no compile failure on this branch." | tee -a "$LOG"
  exit 0
fi
echo "HEAD fails to compile. Good." | tee -a "$LOG"

echo "[$(date)] Verifying merge-base compiles..." | tee -a "$LOG"
git checkout --quiet "$GOOD"
if ! sbt_clean_compile >> "$LOG" 2>&1; then
  echo "ERROR: merge-base $GOOD also fails. Cannot bisect." | tee -a "$LOG"
  git checkout --quiet "$BAD"
  exit 1
fi
echo "Merge-base compiles OK." | tee -a "$LOG"

git checkout --quiet "$BAD"

echo "" | tee -a "$LOG"
echo "[$(date)] Starting bisect (~8 steps with sbt clean compile each)..." | tee -a "$LOG"
git bisect reset 2>/dev/null || true
git bisect start
git bisect bad "$BAD"
git bisect good "$GOOD"

git bisect run "$BISECT_SCRIPT" 2>&1 | tee -a "$LOG"

FIRST_BAD=$(grep -m1 "is the first bad commit" "$LOG" | awk '{print $1}')

git bisect reset

echo "" | tee -a "$LOG"
echo "=== RESULT ===" | tee -a "$LOG"
if [ -n "$FIRST_BAD" ]; then
  echo "First compile failure:" | tee -a "$LOG"
  git log --oneline -1 "$FIRST_BAD" | tee -a "$LOG"
  echo "" | tee -a "$LOG"
  echo "Parent (should compile OK):" | tee -a "$LOG"
  git log --oneline -1 "${FIRST_BAD}^" | tee -a "$LOG"
else
  echo "Could not parse first bad commit — check $LOG" | tee -a "$LOG"
fi
echo "[$(date)] Done." | tee -a "$LOG"
