#!/bin/zsh
# usage: run.sh <MainClass> [args...]   compiles the game (partial ok) + harness, then runs
S=/private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad
ROOT="/Volumes/M.2/GitHub/Group Project Paradigm"
J="/Applications/Apache NetBeans.app/Contents/Home/bin"
OUT=$S/build
rm -rf $OUT; mkdir -p $OUT/game $OUT/h
"$J/javac" -Xlint:all -d $OUT/game "$ROOT"/src/main/java/Project3_6813107/*.java || exit 1
"$J/javac" -nowarn -cp $OUT/game -d $OUT/h $S/harness/*.java || exit 1
cd "$ROOT" && "$J/java" -Djava.awt.headless=true ${=JOPTS} -cp $OUT/game:$OUT/h "$@"
