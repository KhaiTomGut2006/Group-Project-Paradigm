#!/bin/zsh
# Renders every screen of the redesigned UI to PNG files.
# usage: dev-tools/ui-harness/run.sh <output-folder>
# Opens real windows for a few seconds. UiShots2 writes 6 fake scores, so it runs inside a temp folder.
ROOT="${0:A:h:h:h}"
JDK="/Applications/Apache NetBeans.app/Contents/Home/bin"
OUT="${1:?output folder}"
WORK=$(mktemp -d)
"$JDK/javac" -nowarn -d "$WORK/classes" "$ROOT"/src/main/java/Project3_6813107/*.java "$ROOT"/dev-tools/ui-harness/Project3_6813107/*.java || exit 1
cd "$WORK" && "$JDK/java" -cp "classes:$ROOT/src/main/java" Project3_6813107.UiShots "$OUT"
mkdir -p "$WORK/scores" && cd "$WORK/scores" && "$JDK/java" -cp "../classes:$ROOT/src/main/java" Project3_6813107.UiShots2 "$OUT"
