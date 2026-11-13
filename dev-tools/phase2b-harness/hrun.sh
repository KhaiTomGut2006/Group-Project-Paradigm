#!/bin/zsh
# usage: hrun.sh <MainClass> [args]  -- compiles my 2 files + 8 model files + contract stubs + harness, runs headless
S=/private/tmp/claude-501/-Users-tanawatgut--claude-spares-spare-79cmOx/96a86cfa-b7a6-4475-ba1d-800397290095/scratchpad
SRC="/Volumes/M.2/GitHub/Group Project Paradigm/src/main/java/Project3_6813107"
J="/Applications/Apache NetBeans.app/Contents/Home/bin"
B=$S/phase2b/build
rm -rf $B; mkdir -p $B
"$J/javac" -Xlint:all -d $B $SRC/{GameData,AssetLoader,Entity,Player,Monster,Bullet,DroppedItem,GameMap,GamePanel,GameFrame}.java $S/stubs/MainApplication.java $S/stubs/UpgradeDialog.java 2>&1 | grep -v "^$S/stubs" | grep -A2 "warning\|error" 
"$J/javac" -nowarn -cp $B -d $B $S/phase2b/harness/Project3_6813107/*.java || exit 1
mkdir -p $S/phase2b/wd && cd $S/phase2b/wd && "$J/java" ${=JOPTS} -cp $B:"/Volumes/M.2/GitHub/Group Project Paradigm/src/main/java" "$@"
