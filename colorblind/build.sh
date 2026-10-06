#!/usr/bin/env bash
# Builds colorblind.jar. --release 25: the game's java_vm is Temurin 25 (class major 69).
set -euo pipefail
GAME="${GAME:-$HOME/.local/share/Steam/steamapps/common/Spiral Knights}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$HERE/colorblind.jar"
BUILD="$(mktemp -d)"; trap 'rm -rf "$BUILD"' EXIT
CP="$GAME/code-mods/core.jar:$GAME/code/projectx-pcode.jar:$GAME/code/lwjgl.jar:$GAME/code/lwjgl-glfw.jar:$GAME/code/lwjgl-opengl.jar"
javac --release 25 -Xlint:all -cp "$CP" -d "$BUILD" "$HERE/src/com/spiralstudio/mod/colorblind/"*.java
cp "$HERE/src/mod.json" "$BUILD/mod.json"
rm -f "$JAR"; ( cd "$BUILD" && jar --create --file "$JAR" mod.json com )
MAJOR=$(javap -verbose -cp "$JAR" com.spiralstudio.mod.colorblind.Main | awk '/major version/ {print $3; exit}')
[ "$MAJOR" = "69" ] || { echo "BAD class major: $MAJOR" >&2; exit 1; }
unzip -tq "$JAR"; echo "built $JAR (major $MAJOR)"
