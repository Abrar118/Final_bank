#!/usr/bin/env bash
# Regenerates docs/screenshots from a fresh demo bank. Needs JDK 25; on a headless Linux box it uses xvfb-run.
#   docs/screenshots.sh [output-dir]
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-docs/screenshots}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"

./mvnw -B -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
CP="target/test-classes:target/classes:$(cat target/cp.txt)"
MP="$(tr ':' '\n' < target/cp.txt | grep -E 'javafx-(base|graphics|controls|swing)-[0-9.]+-' | paste -sd: -)"

RUN=()
if [[ -z "${DISPLAY:-}" ]] && command -v xvfb-run > /dev/null; then
    RUN=(xvfb-run -a -s "-screen 0 1600x1000x24")
fi
${RUN[@]+"${RUN[@]}"} "${JAVA_BIN}java" --module-path "$MP" --add-modules javafx.controls,javafx.swing \
    --enable-native-access=javafx.graphics,ALL-UNNAMED \
    -cp "$CP" io.github.abrar118.matbank.ui.ScreenshotTour "$OUT"
