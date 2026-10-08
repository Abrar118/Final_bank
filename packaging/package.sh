#!/usr/bin/env bash
# Builds a native MAT Bank installer for the current OS.
#
#   packaging/package.sh            # deb on Linux, dmg on macOS, msi on Windows (Git Bash)
#   packaging/package.sh app-image  # just the runnable folder, no installer
#
# Steps: Maven builds the jar and copies its dependencies to target/lib; jlink makes a trimmed Java 21 runtime
# that already contains JavaFX; jpackage wraps runtime + jars into a native app. Needs JDK 21 on PATH or
# JAVA_HOME. Windows MSIs also need the WiX Toolset.
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
case "$(uname -s)" in
    Linux*)               OS=linux; DEFAULT_TYPE=deb; ICON=packaging/icons/mat-bank.png;  SEP=":" ;;
    Darwin*)              OS=mac;   DEFAULT_TYPE=dmg; ICON=packaging/icons/mat-bank.icns; SEP=":" ;;
    MINGW*|MSYS*|CYGWIN*) OS=win;   DEFAULT_TYPE=msi; ICON=packaging/icons/mat-bank.ico;  SEP=";" ;;
    *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
TYPE="${1:-$DEFAULT_TYPE}"

VERSION="$(sed -n 's|^    <version>\(.*\)</version>|\1|p' pom.xml | head -1)"
JAR="mat-bank-${VERSION}.jar"
WORK=target/package
DIST=target/dist

echo "==> Building MAT Bank ${VERSION} (${OS}, ${TYPE})"
./mvnw -B -q clean package -DskipTests

rm -rf "$WORK" "$DIST"
mkdir -p "$WORK/input" "$DIST"
cp "target/$JAR" "$WORK/input/"

# JavaFX goes into the runtime image (as real modules); everything else stays on the class path.
JAVAFX_JARS=()
for jar in target/lib/*.jar; do
    name="$(basename "$jar")"
    if [[ "$name" == javafx-* ]]; then
        # Only the platform-specific jars (e.g. javafx-graphics-21.0.12-linux.jar) contain the modules.
        [[ "$name" =~ ^javafx-[a-z]+-[0-9.]+-.+\.jar$ ]] && JAVAFX_JARS+=("$jar")
    else
        cp "$jar" "$WORK/input/"
    fi
done
MODULE_PATH="$(IFS="$SEP"; echo "${JAVAFX_JARS[*]}")"

# Found with jdeps; jdk.crypto.ec for HTTPS to the exchange-rate API, jdk.localedata for date pickers.
MODULES="java.base,java.desktop,java.logging,java.net.http,java.prefs,java.sql,java.xml,jdk.crypto.ec"
MODULES="$MODULES,jdk.jfr,jdk.localedata,jdk.unsupported,javafx.base,javafx.graphics,javafx.controls"

echo "==> Creating runtime image"
"${JAVA_BIN}jlink" \
    --module-path "$MODULE_PATH" \
    --add-modules "$MODULES" \
    --include-locales en,bn \
    --strip-debug --no-header-files --no-man-pages \
    --compress zip-6 \
    --output "$WORK/runtime"

echo "==> Packaging"
ARGS=(
    --type "$TYPE"
    --dest "$DIST"
    --name "MAT Bank"
    --app-version "$VERSION"
    --vendor "MAT Bank"
    --description "Desktop banking demo, first built in 2022 and rebuilt with JavaFX 21"
    --copyright "Copyright (c) 2022-2026 Abrar Mahir Esam, Mehmil Khan, Farheen Mahjarin Trisha"
    --input "$WORK/input"
    --main-jar "$JAR"
    --main-class io.github.abrar118.matbank.Launcher
    --runtime-image "$WORK/runtime"
    --icon "$ICON"
    --java-options "--enable-native-access=ALL-UNNAMED,javafx.graphics"
)
if [[ "$TYPE" != "app-image" ]]; then
    case "$OS" in
        linux) ARGS+=(--linux-package-name mat-bank --linux-shortcut --linux-menu-group Office
                      --linux-app-category misc) ;;
        win)   ARGS+=(--win-menu --win-menu-group "MAT Bank" --win-shortcut --win-dir-chooser
                      --win-upgrade-uuid 6f1c7b2e-2a5d-4b9a-9d3e-3c8f6a1e2022) ;;
        mac)   ARGS+=(--mac-package-name "MAT Bank") ;;
    esac
fi
"${JAVA_BIN}jpackage" "${ARGS[@]}"

echo "==> Done:"
ls -1 "$DIST"
