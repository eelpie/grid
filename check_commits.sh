#!/bin/bash

# Check that every commit from START_COMMIT to HEAD compiles with sbt compile
START_COMMIT="eaffaefc0cac1d19a1841810e984be4ea7646fc4"
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
LOG_FILE="compile_check_results.log"

echo "Checking commits from $START_COMMIT to HEAD" | tee "$LOG_FILE"
echo "Current branch: $CURRENT_BRANCH" | tee -a "$LOG_FILE"
echo "======================================" | tee -a "$LOG_FILE"

# Get list of commits from oldest to newest
COMMITS=$(git log --oneline "$START_COMMIT^..HEAD" --reverse | awk '{print $1}')

PASS=0
FAIL=0
FAIL_LIST=""

for COMMIT in $COMMITS; do
    MSG=$(git log --oneline -1 "$COMMIT")
    echo "" | tee -a "$LOG_FILE"
    echo "Checking: $MSG" | tee -a "$LOG_FILE"

    git checkout "$COMMIT" --quiet 2>&1

    if sbt compile >> "$LOG_FILE" 2>&1; then
        echo "PASS: $MSG" | tee -a "$LOG_FILE"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $MSG" | tee -a "$LOG_FILE"
        FAIL=$((FAIL + 1))
        FAIL_LIST="$FAIL_LIST\n  FAIL: $MSG"
    fi
done

echo "" | tee -a "$LOG_FILE"
echo "======================================" | tee -a "$LOG_FILE"
echo "Results: $PASS passed, $FAIL failed" | tee -a "$LOG_FILE"

if [ -n "$FAIL_LIST" ]; then
    echo -e "Failed commits:$FAIL_LIST" | tee -a "$LOG_FILE"
fi

# Return to original branch
git checkout "$CURRENT_BRANCH" --quiet 2>&1
echo "" | tee -a "$LOG_FILE"
echo "Returned to branch: $CURRENT_BRANCH" | tee -a "$LOG_FILE"
