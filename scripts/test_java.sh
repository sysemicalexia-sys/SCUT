#!/bin/sh

SRC=app/src/main/java
OUT=bin/java-tests

rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
    tests/java/CoreTest.java \
    tests/java/com/scut/media/SelectorTest.java \
    $SRC/com/scut/project/Clip.java \
    $SRC/com/scut/project/Project.java \
    $SRC/com/scut/project/Timeline.java \
    $SRC/com/scut/project/History.java \
    $SRC/com/scut/project/Fmt.java \
    $SRC/com/scut/media/ExportSettings.java \
    $SRC/com/scut/media/PcmConverter.java \
    $SRC/com/scut/media/FrameSelector.java || exit 1

java -cp "$OUT" CoreTest || exit 1
java -cp "$OUT" com.scut.media.SelectorTest
