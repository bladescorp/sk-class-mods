#!/usr/bin/env bash
# 1) colour maths, 2) negative control (a broken matrix must turn the maths red),
# 3) Core mount + -Xverify:all of both hooked classes.
set -euo pipefail
GAME="${GAME:-$HOME/.local/share/Steam/steamapps/common/Spiral Knights}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
JAVA="$GAME/java_vm/bin/java"
LIBS="$GAME/code/projectx-pcode.jar:$GAME/code/projectx-config.jar:$GAME/code/config.jar:$GAME/code/lwjgl-opengl.jar:$GAME/code/lwjgl-glfw.jar:$GAME/code/lwjgl.jar:$GAME/code-mods/core.jar"
"$HERE/../build.sh" >/dev/null
javac --release 25 -cp "$LIBS:$HERE/../colorblind.jar" -d "$TMP" "$HERE/CvdTest.java" "$HERE/Harness.java"
"$JAVA" -cp "$TMP:$HERE/../colorblind.jar:$LIBS" colorblindtest.CvdTest

# negative control: break the deutan matrix, the test must fail (and for the right reason)
mkdir -p "$TMP/neg/src"; cp -r "$HERE/../src/com" "$TMP/neg/src/"
F="$TMP/neg/src/com/spiralstudio/mod/colorblind/Cvd.java"
grep -q "0.367322f, 0.860646f, -0.227968f" "$F"
sed -i 's/0.367322f, 0.860646f, -0.227968f/1.0f, 0.0f, 0.0f/' "$F"
mkdir -p "$TMP/neg/out"
javac --release 25 -cp "$LIBS" -d "$TMP/neg/out" "$F"
if "$JAVA" -cp "$TMP/neg/out:$TMP:$LIBS" colorblindtest.CvdTest >"$TMP/neg.log" 2>&1; then
  echo "NEGATIVE CONTROL VACUOUS: broken matrix still passed"; exit 1
fi
grep -q "FAIL" "$TMP/neg.log" && echo "negative control: red as expected"

"$JAVA" -Xverify:all --add-opens java.base/java.lang=ALL-UNNAMED -Djava.awt.headless=true \
  -cp "$TMP:$HERE/../colorblind.jar:$LIBS" colorblindtest.Harness
