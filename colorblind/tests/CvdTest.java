package colorblindtest;

import com.spiralstudio.mod.colorblind.Cvd;

public final class CvdTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) {
            fails++;
        }
    }

    static float srgb(float lin) {
        return lin <= 0.0031308f ? lin * 12.92f : (float) (1.055 * Math.pow(lin, 1 / 2.4) - 0.055);
    }

    static float dist(float[] x, float[] y) {
        return (float) Math.sqrt(Math.pow(x[0] - y[0], 2) + Math.pow(x[1] - y[1], 2) + Math.pow(x[2] - y[2], 2));
    }

    static boolean near(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }

    public static void main(String[] a) {
        // strength 0 and Off are exact identities for every type/mode
        for (Cvd.Type t : Cvd.Type.values()) {
            for (Cvd.Mode m : Cvd.Mode.values()) {
                check(Cvd.isIdentity(Cvd.matrix(t, m, 0f)), "identity at 0%: " + t + "/" + m);
            }
        }
        check(Cvd.isIdentity(Cvd.matrix(Cvd.Type.OFF, Cvd.Mode.SIMULATE, 1f)), "Off ignores strength");

        // greys must stay grey in every simulation (matrix rows sum to ~1)
        for (Cvd.Type t : new Cvd.Type[] {Cvd.Type.DEUTERANOPIA, Cvd.Type.PROTANOPIA, Cvd.Type.TRITANOPIA}) {
            float[] m = Cvd.matrix(t, Cvd.Mode.SIMULATE, 1f);
            float[] g = Cvd.apply(m, 0.5f, 0.5f, 0.5f);
            check(near(g[0], 0.5f, 0.01f) && near(g[1], 0.5f, 0.01f) && near(g[2], 0.5f, 0.01f),
                "grey preserved: " + t + " -> " + g[0] + "," + g[1] + "," + g[2]);
        }

        // known behaviour: deuteranopia collapses red and green toward each other
        float[] d = Cvd.matrix(Cvd.Type.DEUTERANOPIA, Cvd.Mode.SIMULATE, 1f);
        float[] red = Cvd.apply(d, 1, 0, 0), green = Cvd.apply(d, 0, 1, 0);
        float gap = Math.abs(red[0] - green[0]) + Math.abs(red[1] - green[1]);
        check(gap < 1.2f, "deutan sim brings red/green closer (gap " + gap + " vs 2.0 original)");

        // The case that matters: a red and a green a deutan cannot tell apart (they sit on the
        // same confusion line: linear red 0.466, linear green 0.2). Correction must pull them
        // apart in what the deutan then sees. Pure primaries would not show this - they already
        // differ in brightness.
        float[] r0 = {srgb(0.466f), 0f, 0f}, g0 = {0f, srgb(0.2f), 0f};
        float before = dist(Cvd.apply(d, r0[0], r0[1], r0[2]), Cvd.apply(d, g0[0], g0[1], g0[2]));
        float[] c = Cvd.matrix(Cvd.Type.DEUTERANOPIA, Cvd.Mode.CORRECT, 1f);
        float[] rc = Cvd.apply(c, r0[0], r0[1], r0[2]), gc = Cvd.apply(c, g0[0], g0[1], g0[2]);
        float after = dist(Cvd.apply(d, rc[0], rc[1], rc[2]), Cvd.apply(d, gc[0], gc[1], gc[2]));
        check(before < 0.15f, "confusable pair really is confusable to a deutan (" + before + ")");
        check(after > before + 0.1f, "correction separates it for a deutan (" + before + " -> " + after + ")");

        // strength interpolates monotonically: more strength, bigger change
        float prev = -1;
        boolean mono = true;
        for (int s = 0; s <= 100; s += 10) {
            float[] m = Cvd.matrix(Cvd.Type.PROTANOPIA, Cvd.Mode.SIMULATE, s / 100f);
            float[] o = Cvd.apply(m, 1, 0, 0);
            float delta = Math.abs(o[0] - 1) + Math.abs(o[1]) + Math.abs(o[2]);
            mono &= delta >= prev - 1e-5f;
            prev = delta;
        }
        check(mono, "strength monotonic (protan, red)");
        check(Cvd.Type.parse("Deuteranopia", Cvd.Type.OFF) == Cvd.Type.DEUTERANOPIA, "parse label");
        check(Cvd.Type.parse("garbage", Cvd.Type.OFF) == Cvd.Type.OFF, "parse garbage falls back");
        System.out.println(fails == 0 ? "PASS" : "FAILURES: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
