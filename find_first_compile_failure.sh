#!/bin/bash

ORIGINAL=$(git rev-parse HEAD)
LOG=/tmp/find_first_compile_failure.log

# Commits oldest-to-newest, starting from (and including) the given SHA
COMMITS=$(git log --oneline --reverse eca2e6f98b7646fef9a06d87a629d1f3935a0393^..HEAD | awk '{print $1}')

for COMMIT in $COMMITS; do
  echo "Checking $COMMIT..."
  git checkout --quiet "$COMMIT"
  if ! sbt compile > "$LOG" 2>&1; then
    echo "COMPILE FAILED at $COMMIT"
    tail -40 "$LOG"
    git checkout --quiet "$ORIGINAL"
    exit 0
  fi
  echo "OK: $COMMIT"
done

echo "All commits compile OK"
git checkout --quiet "$ORIGINAL"
