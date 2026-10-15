package com.spiralstudio.mod.colorblind;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared, thread-safe settings. The render thread reads the volatile fields
 * every frame; the UI writes them on change. The file is written lazily
 * (see {@link #flushIfDue}) so dragging the slider does not hit the disk once
 * per step.
 *
 * <p>Stored in {@code code-mods/colorblind.yml} like every other code-mod, and
 * therefore also shows up in modconfig. It is global to the install, not per
 * account: colour vision belongs to the person at the keyboard.
 */
public final class Settings {
    private Settings() {
    }

    static final String CONFIG = "colorblind.yml";
    public static final int DEFAULT_STRENGTH = 100;

    public static volatile Cvd.Type type = Cvd.Type.OFF;
    public static volatile Cvd.Mode mode = Cvd.Mode.CORRECT;
    /** 0..100 */
    public static volatile int strength = DEFAULT_STRENGTH;
    /** 0..100, independent of the type above; 0 = off. */
    public static volatile int grayscale;
    public static volatile int saturation;

    private static volatile long saveAt;
    private static volatile boolean dirty;

    static {
        // No core.jar dependency: the class-mod build has no Main to call load().
        load();
    }

    static File file() {
        return new File(System.getProperty("user.dir"), "code-mods/" + CONFIG);
    }

    public static void load() {
        if (file().isFile()) {
            try {
                Map<String, Object> cfg = read(file());
                {
                    type = Cvd.Type.parse(cfg.get("type"), Cvd.Type.OFF);
                    grayscale = intOr(cfg.get("grayscale"), 0);
                    saturation = intOr(cfg.get("saturation"), 0);
                    // grayscale/saturate used to be types; they are their own sliders now
                    String old = String.valueOf(cfg.get("type")).trim().toLowerCase();
                    Object s0 = cfg.get("strength");
                    if (old.equals("grayscale") && grayscale == 0) {
                        grayscale = intOr(s0, 100);
                    } else if (old.equals("saturate") && saturation == 0) {
                        saturation = intOr(s0, 100);
                    }
                    mode = Cvd.Mode.parse(cfg.get("mode"), Cvd.Mode.CORRECT);
                    Object s = cfg.get("strength");
                    if (s instanceof Number) {
                        strength = clamp(((Number) s).intValue());
                    }
                }
            } catch (Throwable t) {
                System.out.println("[colorblind] Could not read " + CONFIG + ", rewriting it: " + t);
            }
        }
        save();
    }

    /** The file is three flat "key: value" lines; no YAML library needed. */
    static Map<String, Object> read(File f) throws java.io.IOException {
        Map<String, Object> m = new HashMap<>();
        for (String line : Files.readAllLines(f.toPath())) {
            int hash = line.indexOf('#');
            if (hash >= 0) {
                line = line.substring(0, hash);
            }
            int c = line.indexOf(':');
            if (c <= 0) {
                continue;
            }
            String k = line.substring(0, c).trim();
            String v = line.substring(c + 1).trim();
            try {
                m.put(k, Integer.valueOf(v));
            } catch (NumberFormatException e) {
                m.put(k, v);
            }
        }
        return m;
    }

    private static int intOr(Object o, int dflt) {
        return o instanceof Number ? clamp(((Number) o).intValue()) : dflt;
    }

    public static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public static float strength01() {
        return strength / 100f;
    }

    /** UI changed something: persist soon. */
    public static void changed() {
        dirty = true;
        saveAt = System.currentTimeMillis() + 750;
    }

    public static void resetToDefaults() {
        type = Cvd.Type.OFF;
        mode = Cvd.Mode.CORRECT;
        strength = DEFAULT_STRENGTH;
        grayscale = 0;
        saturation = 0;
        changed();
    }

    /** Called once a frame from the render hook; cheap when nothing is pending. */
    public static void flushIfDue() {
        if (dirty && System.currentTimeMillis() >= saveAt) {
            save();
        }
    }

    public static synchronized void save() {
        dirty = false;
        File f = file();
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (FileWriter out = new FileWriter(tmp)) {
            out.write("# Colorblind filter. Also editable in Options > Video.\n");
            out.write("# type: off, deuteranopia, protanopia, tritanopia, protan_flip or deutan_flip\n");
            out.write("type: " + type.name().toLowerCase() + "\n\n");
            out.write("# correct = shift hard-to-tell colours so you can separate them\n");
            out.write("# simulate = show what a person with this type sees (for checking)\n# (mode only applies to deuteranopia, protanopia and tritanopia)\n");
            out.write("mode: " + mode.name().toLowerCase() + "\n\n");
            out.write("# How strong the effect is, 0 to 100.\n");
            out.write("strength: " + strength + "\n\n");
            out.write("# Extra filters, 0 (off) to 100. They work with any type above, even Off.\n");
            out.write("grayscale: " + grayscale + "\n");
            out.write("saturation: " + saturation + "\n");
        } catch (Throwable t) {
            System.out.println("[colorblind] Could not write " + CONFIG + ": " + t);
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }
}
