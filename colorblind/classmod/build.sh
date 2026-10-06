#!/usr/bin/env bash
# Class-mod build for Lucas's KnightLauncher: zip of mod.json + full replacement
# classes (patched ProjectXApp/OptionsDialog) + our classes. Tied to one pcode.
set -euo pipefail
GAME="${GAME:-$HOME/.local/share/Steam/steamapps/common/Spiral Knights}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$HERE/.."
PX="$(grep -m1 '^version' "$GAME/getdown.txt" | sed 's/.*= *//')"
OUT="${OUT:-$HERE/colorblind-classmod.zip}"
B="$(mktemp -d)"; trap 'rm -rf "$B"' EXIT
JAVA="${JAVA:-$GAME/java_vm/bin/java}"; CORE="${CORE:-$HOME/.local/share/Steam/steamapps/common/Spiral Knights/code-mods/core.jar}"
CP="$GAME/code/projectx-pcode.jar:$GAME/code/lwjgl.jar:$GAME/code/lwjgl-glfw.jar:$GAME/code/lwjgl-opengl.jar"
mkdir -p "$B/cls" "$B/zip"
# every class except Main (which needs core.jar's ClassPool)
javac --release 25 -cp "$CP" -d "$B/cls" $(ls "$SRC"/src/com/spiralstudio/mod/colorblind/*.java | grep -v '/Main.java')
javac --release 25 -cp "$CORE" -d "$B/p" "$HERE/Patch.java"
"$JAVA" -cp "$B/p:$CORE" Patch "$GAME/code/projectx-pcode.jar" "$B/zip" "$B/cls"
cp -r "$B/cls/com" "$B/zip/"
cat > "$B/zip/mod.json" <<J
{
  "mod": {
    "name": "Colorblind Filter",
    "description": "Colorblind filter (deuteranopia, protanopia, tritanopia) with correct/simulate mode and a strength slider, added to Options > Video.",
    "author": "Punch & Vise",
    "version": "1.0.0",
    "type": "class",
    "pxVersion": "$PX"
  }
}
J
rm -f "$OUT"; ( cd "$B/zip" && jar --create --file "$OUT" --no-manifest mod.json com )
# no core.jar reference may survive in the shipped classes
if unzip -p "$OUT" 'com/spiralstudio/mod/colorblind/*.class' | grep -aq "spiralstudio/mod/core"; then echo "core.jar reference leaked" >&2; exit 1; fi
unzip -l "$OUT" | grep -E "\.class|mod.json"; echo "built $OUT (pxVersion $PX)"
