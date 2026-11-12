#!/bin/bash
# Fast JVM-only engine harness (no Android SDK): compiles game/ + data/ with a Context stub and runs Calib.
set -e
K=/home/user/.cache/kt
SRC=../../app/src/main/java/com/cricketcoach/manager
JAVA_OPTS="-Xmx900m" $K/kotlinc/bin/kotlinc -nowarn -jvm-target 11 -cp $K/gson.jar Stub.kt Calib.kt $SRC/game/*.kt $SRC/data/*.kt -d /home/user/.cache/kt/calib.jar 2>&1 | grep -v "^warning" | head -40
