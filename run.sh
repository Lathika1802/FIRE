#!/bin/sh
# Compile and start the Java program.   ./run.sh          -> console menu
#                                       ./run.sh api      -> only the web API (for web/backend.html)
cd "$(dirname "$0")" || exit 1
mkdir -p out lib
javac -d out src/sudar/*.java || exit 1
if [ "$1" = "api" ]; then shift; exec java -cp "out:lib/*" sudar.Main --api "$@"; fi
exec java -cp "out:lib/*" sudar.Main "$@"
