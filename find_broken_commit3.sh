#!/bin/bash
set -e

BRANCH="multi-tenant"
ORIGINAL=$(git rev-parse HEAD)

# Get commits oldest-to-newest
COMMITS=$(git log main..$BRANCH --oneline --reverse | awk '{print $1}')

for COMMIT in $COMMITS; do
  echo "Checking $COMMIT..."
  git checkout --quiet "$COMMIT"
  if ! sbt compile > /tmp/sbt_out.txt 2>&1; then
    echo "COMPILE FAILED at $COMMIT"
    echo "--- Output ---"
    tail -30 /tmp/sbt_out.txt
    git checkout --quiet "$ORIGINAL"
    exit 1
  fi
  echo "OK: $COMMIT"
done

echo "All commits compile OK"
git checkout --quiet "$ORIGINAL"
