#!/bin/zsh
# usage: run.sh [dialog]  - compiles game files (with contract stubs) + harness, runs in a temp work dir
P=/private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad/phase2c
rm -rf $P/build; mkdir -p $P/build $P/work/Project3_6813107
"/Applications/Apache NetBeans.app/Contents/Home/bin/javac" -Xlint:all -d $P/build "/Volumes/M.2/GitHub/Group Project Paradigm"/src/main/java/Project3_6813107/{GameData,AssetLoader,Entity,Player,Monster,Bullet,DroppedItem,GameMap,MainApplication,UpgradeDialog}.java /private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad/stubs/GameFrame.java /private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad/stubs/GamePanel.java 2>&1 | grep -v "stubs/" | grep -B1 -A3 "warning\|error"
"/Applications/Apache NetBeans.app/Contents/Home/bin/javac" -nowarn -cp $P/build -d $P/build $P/h/UiHarness.java || exit 1
cd $P/work && "/Applications/Apache NetBeans.app/Contents/Home/bin/java" -cp "$P/build:/Volumes/M.2/GitHub/Group Project Paradigm/src/main/java" Project3_6813107.UiHarness $P "$@"
