# sk-class-mods

Spiral Knights class mods (KnightLauncher). Each folder is one mod.

## colorblind

Colorblind filter (deuteranopia, protanopia, tritanopia) with correct/simulate mode and a 0-100% strength
slider, added to Options > Video. Settings are stored in `code-mods/colorblind.yml` in the game folder.

Build the class mod for an install (a class mod is tied to one game version):

    GAME="/path/to/Spiral Knights" OUT=colorblind.zip colorblind/classmod/build.sh

Needs a Java 25 JDK. The zip goes in KnightLauncher's `mods` folder. `colorblind/build.sh` builds the same
code as a core.jar code mod instead.
