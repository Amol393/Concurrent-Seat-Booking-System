#!/bin/bash
# Compile and run:  ./run.sh   or   ./run.sh stress
cd "$(dirname "$0")"
mkdir -p out
javac -cp "lib/*" -d out *.java || exit 1
java --enable-native-access=ALL-UNNAMED -cp "out:lib/*" Main "$@"
