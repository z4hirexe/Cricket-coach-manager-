#!/bin/bash
# usage: ./run.sh [N] [-Dcap=0.3 -Dbias=12 -Dsd=1.0]
K=/home/user/.cache/kt; N=${1:-60}; shift
java -Xmx700m "$@" -cp $K/calib.jar:$K/gson.jar:$K/kotlinc/lib/kotlin-stdlib.jar CalibKt ../../app/src/main/assets $N
