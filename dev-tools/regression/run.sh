#!/usr/bin/env bash
# Compare the checked-out game with a Git revision on the same JDK and machine.
# Usage: dev-tools/regression/run.sh <baseline-git-ref> [--ui]
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
baseline=${1:?Supply the baseline Git revision}
mode=${2:-}
if [[ -n "$mode" && "$mode" != --ui ]]; then
    echo "Expected --ui or no second argument." >&2
    exit 2
fi

scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
mkdir -p "$scratch/before" "$scratch/after"
ref=$(git -C "$root" rev-parse --verify "$baseline^{commit}")
git -C "$root" archive "$ref" src/main/java | tar -x -C "$scratch/before"

javac_cmd=javac
java_cmd=java
if [[ -n "${JAVA_HOME:-}" ]]; then
    javac_cmd="$JAVA_HOME/bin/javac"
    java_cmd="$JAVA_HOME/bin/java"
fi

for side in before after; do
    run_dir="$scratch/$side"
    src="$root/src/main/java"
    if [[ "$side" == before ]]; then
        src="$run_dir/src/main/java"
    fi
    "$javac_cmd" --release 26 -Xlint:all -d "$run_dir/classes" \
        "$src"/Project3_6813107/*.java "$root"/dev-tools/regression/Project3_6813107/*.java
    mkdir "$run_dir/work"
    (
        cd "$run_dir/work"
        "$java_cmd" -Djava.awt.headless=true -cp "$run_dir/classes:$src" Project3_6813107.GameRegression
        if [[ "$mode" == --ui ]]; then
            "$java_cmd" -cp "$run_dir/classes:$src" Project3_6813107.SwingRegression
        fi
    ) > "$run_dir/results"
done

diff -u "$scratch/before/results" "$scratch/after/results"
cat "$scratch/after/results"
printf '\nBefore and after match (%s).\n' "$ref"
