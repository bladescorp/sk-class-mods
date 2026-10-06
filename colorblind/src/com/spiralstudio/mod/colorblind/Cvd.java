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
        OFF("Off"), DEUTERANOPIA("Deuteranopia"), PROTANOPIA("Protanopia"), TRITANOPIA("Tritanopia");

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
