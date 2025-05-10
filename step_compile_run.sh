#!/bin/bash

LOGFILE="/Users/tony/git/grid/step_compile_run_result.txt"
ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)

COMMITS=(
473fe4183a
2fea7edcbc
510af01dea
68b78c5e71
e051974837
98899fd999
9a7738bcda
0bc07cd938
321a03c944
a301d07b54
006abcabad
a568502fd6
5111862650
bf23536aac
ed64e9f3c3
ffea2da73e
5d013cbe2c
006462a34a
c14f1f7369
a958c46470
7a3a8a6617
1c2acc9dec
24e1721e93
03343b9274
f04585afa7
d13e5f95c2
fb08c15212
59374cc49c
724c02d0ec
ac7285c386
69a2a99671
fb95823923
5b252ba954
a7289c67d9
cf5d7eb792
9f5dcf7264
732d416692
c08580ffa8
)

echo "Starting compile check at $(date)" > "$LOGFILE"
echo "Original branch: $ORIGINAL_BRANCH" >> "$LOGFILE"
echo "Total commits: ${#COMMITS[@]}" >> "$LOGFILE"
echo "---" >> "$LOGFILE"

FAILED_COMMIT=""
FAILED_IDX=""

for i in "${!COMMITS[@]}"; do
  COMMIT="${COMMITS[$i]}"
  NUM=$((i + 1))
  TOTAL=${#COMMITS[@]}
  MSG=$(git log --oneline -1 "$COMMIT")

  echo "" >> "$LOGFILE"
  echo "[$NUM/$TOTAL] Checking out $COMMIT" >> "$LOGFILE"
  echo "  $MSG" >> "$LOGFILE"
  echo "[$NUM/$TOTAL] $(date '+%H:%M:%S') Checking: $MSG"

  git checkout "$COMMIT" --quiet 2>> "$LOGFILE"

  echo "  Running sbt compile..." >> "$LOGFILE"
  if sbt compile >> "$LOGFILE" 2>&1; then
    echo "  PASS" >> "$LOGFILE"
    echo "  => PASS"
  else
    echo "  FAIL" >> "$LOGFILE"
    echo "  => FAIL — stopping"
    FAILED_COMMIT="$COMMIT"
    FAILED_IDX="$NUM"
    break
  fi
done

echo "" >> "$LOGFILE"
echo "---" >> "$LOGFILE"
if [ -n "$FAILED_COMMIT" ]; then
  echo "FIRST FAILURE: [$FAILED_IDX/${#COMMITS[@]}] $FAILED_COMMIT" >> "$LOGFILE"
  git log --oneline -1 "$FAILED_COMMIT" >> "$LOGFILE"
  echo ""
  echo "=== FIRST FAILURE: commit $FAILED_COMMIT ==="
  git log --oneline -1 "$FAILED_COMMIT"
else
  echo "ALL PASSED" >> "$LOGFILE"
  echo "=== ALL COMMITS PASSED ==="
fi

echo "Restoring branch: $ORIGINAL_BRANCH" >> "$LOGFILE"
git checkout "$ORIGINAL_BRANCH" --quiet 2>> "$LOGFILE"
echo "Done at $(date)" >> "$LOGFILE"
