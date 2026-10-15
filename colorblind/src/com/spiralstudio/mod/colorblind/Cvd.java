package com.spiralstudio.mod.colorblind;

/**
 * Colour-vision-deficiency maths. Pure, no game or GL types, so the tests can
 * run it headless and the shader gets exactly the matrix the tests checked.
 *
 * <p>Simulation matrices are Machado, Oliveira &amp; Fernandes (2009) at full
 * severity, in LINEAR RGB. They are the Vienot/Brettel dichromat models
 * refit so that severity interpolates linearly, which is what the strength
 * slider needs: {@code S(s) = (1-s)*I + s*M}.
 *
 * <p>"Correct" (daltonize) shows the original colours with the information a
 * dichromat loses pushed into channels they still see:
 * {@code C = I + E*(I - S)}, E being the usual error-redistribution matrix.
 * Both reduce to the identity at strength 0.
 */
public final class Cvd {
    private Cvd() {
    }

    public enum Type {
        OFF("Off"), DEUTERANOPIA("Deuteranopia"), PROTANOPIA("Protanopia"), TRITANOPIA("Tritanopia"),
        // Appended after the originals: ordinals/names of old configs stay valid.
        PROTAN_FLIP("Protan flip"), DEUTAN_FLIP("Deutan flip");

        public final String label;

        Type(String label) {
            this.label = label;
        }

        public static Type parse(Object o, Type fallback) {
            if (o == null) {
                return fallback;
            }
            String s = o.toString().trim();
            for (Type t : values()) {
                if (t.name().equalsIgnoreCase(s) || t.label.equalsIgnoreCase(s)) {
                    return t;
                }
            }
            return fallback;
        }
    }

    public enum Mode {
        CORRECT("Correct"), SIMULATE("Simulate");

        public final String label;

        Mode(String label) {
            this.label = label;
        }

        public static Mode parse(Object o, Mode fallback) {
            if (o == null) {
                return fallback;
            }
            String s = o.toString().trim();
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(s) || m.label.equalsIgnoreCase(s)) {
                    return m;
                }
            }
            return fallback;
        }
    }

    /** Correct/Simulate only means something for the three deficiency types. */
    public static boolean usesMode(Type t) {
        return t == Type.DEUTERANOPIA || t == Type.PROTANOPIA || t == Type.TRITANOPIA;
    }

    // ---- Tuning constants (linear RGB). Raise to strengthen, lower to soften. ----
    /** Rec.709 luma weights (linear light). */
    private static final float[] LUMA = {0.2126f, 0.7152f, 0.0722f};
    /** Saturation slider: chroma gain at 100% is 1 + SAT_K. */
    static final float SAT_K = 2f;
    /** Flip variants: red chroma gain / brightness, green chroma gain / brightness (1 = unchanged). */
    static final float PROTAN_FLIP_R_SAT = 1.8f, PROTAN_FLIP_R_GAIN = 1.3f,
        PROTAN_FLIP_G_SAT = 0.6f, PROTAN_FLIP_G_GAIN = 0.9f;
    static final float DEUTAN_FLIP_R_SAT = 1.5f, DEUTAN_FLIP_R_GAIN = 1.1f,
        DEUTAN_FLIP_G_SAT = 0.3f, DEUTAN_FLIP_G_GAIN = 0.8f;

    static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    private static final float[] PROTAN = {
        0.152286f, 1.052583f, -0.204868f,
        0.114503f, 0.786281f, 0.099216f,
        -0.003882f, -0.048116f, 1.051998f};
    private static final float[] DEUTAN = {
        0.367322f, 0.860646f, -0.227968f,
        0.280085f, 0.672501f, 0.047413f,
        -0.011820f, 0.042940f, 0.968881f};
    private static final float[] TRITAN = {
        1.255528f, -0.076749f, -0.178779f,
        -0.078411f, 0.930809f, 0.147602f,
        0.004733f, 0.691367f, 0.303900f};

    /** Error redistribution: where the lost information is sent. */
    private static final float[] E_RG = {0, 0, 0, 0.7f, 1, 0, 0.7f, 0, 1};
    private static final float[] E_TRI = {1, 0, 0.7f, 0, 1, 0.7f, 0, 0, 0};

    /** Row-major 3x3 for linear RGB; the identity when off or strength is 0. */
    public static float[] matrix(Type type, Mode mode, float strength) {
        float s = Math.max(0f, Math.min(1f, strength));
        float[] full;
        float[] e;
        switch (type) {
            case DEUTERANOPIA: full = DEUTAN; e = E_RG; break;
            case PROTANOPIA: full = PROTAN; e = E_RG; break;
            case TRITANOPIA: full = TRITAN; e = E_TRI; break;
            case PROTAN_FLIP:
                return lerpId(flip(PROTAN_FLIP_R_SAT, PROTAN_FLIP_R_GAIN, PROTAN_FLIP_G_SAT, PROTAN_FLIP_G_GAIN), s);
            case DEUTAN_FLIP:
                return lerpId(flip(DEUTAN_FLIP_R_SAT, DEUTAN_FLIP_R_GAIN, DEUTAN_FLIP_G_SAT, DEUTAN_FLIP_G_GAIN), s);
            default: return IDENTITY.clone();
        }
        float[] sim = new float[9];
        for (int i = 0; i < 9; i++) {
            sim[i] = (1 - s) * IDENTITY[i] + s * full[i];
        }
        if (mode == Mode.SIMULATE) {
            return sim;
        }
        float[] diff = new float[9];
        for (int i = 0; i < 9; i++) {
            diff[i] = IDENTITY[i] - sim[i];
        }
        float[] out = mul(e, diff);
        for (int i = 0; i < 9; i++) {
            out[i] += IDENTITY[i];
        }
        return out;
    }

    /** luma + k*(c - luma): k=0 is grey, 1 identity, >1 boosted. */
    private static float[] saturation(float k) {
        float[] m = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                m[r * 3 + c] = (1 - k) * LUMA[c] + (r == c ? k : 0f);
            }
        }
        return m;
    }

    /** Red row scaled in chroma and brightness, green row likewise, blue untouched. */
    private static float[] flip(float rSat, float rGain, float gSat, float gGain) {
        float[] r = saturation(rSat), g = saturation(gSat);
        float[] m = IDENTITY.clone();
        for (int c = 0; c < 3; c++) {
            m[c] = r[c] * rGain;
            m[3 + c] = g[3 + c] * gGain;
        }
        return m;
    }

    private static float[] lerpId(float[] full, float s) {
        float[] m = new float[9];
        for (int i = 0; i < 9; i++) {
            m[i] = (1 - s) * IDENTITY[i] + s * full[i];
        }
        return m;
    }

    /**
     * Everything the filter applies: colourblind type first, then the saturation
     * boost, then grayscale (so grayscale at 100% always wins). Each slider is 0..1
     * and is the identity at 0.
     */
    public static float[] combined(Type type, Mode mode, float strength, float gray, float sat) {
        float[] m = matrix(type, mode, strength);
        float sv = Math.max(0f, Math.min(1f, sat));
        float g = Math.max(0f, Math.min(1f, gray));
        if (sv > 0f) {
            m = mul(saturation(1f + SAT_K * sv), m);
        }
        if (g > 0f) {
            m = mul(lerpId(saturation(0f), g), m);
        }
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    public static boolean isIdentity(float[] m) {
        for (int i = 0; i < 9; i++) {
            if (Math.abs(m[i] - IDENTITY[i]) > 1e-6f) {
                return false;
            }
        }
        return true;
    }

    static float toLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    static float toSrgb(float c) {
        c = Math.max(0f, Math.min(1f, c));
        return c <= 0.0031308f ? c * 12.92f : (float) (1.055 * Math.pow(c, 1 / 2.4) - 0.055);
    }

    /** CPU reference of the shader: sRGB 0..1 in, sRGB 0..1 out. */
    public static float[] apply(float[] m, float r, float g, float b) {
        float lr = toLinear(r), lg = toLinear(g), lb = toLinear(b);
        return new float[] {
            toSrgb(m[0] * lr + m[1] * lg + m[2] * lb),
            toSrgb(m[3] * lr + m[4] * lg + m[5] * lb),
            toSrgb(m[6] * lr + m[7] * lg + m[8] * lb)};
    }
}
