package com.spiralstudio.mod.colorblind;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Whole-frame colour filter. Runs once per frame after the game has drawn the
 * world AND the GUI, just before the buffer swap: the back buffer is copied
 * into a texture and drawn back through a 3x3 colour matrix in a fragment
 * shader. Everything the player sees is filtered, UI included, which is what
 * a colourblind filter should do (health bars and rarity colours are UI).
 *
 * <p>Safety rules, because the game's own Renderer caches GL state and this
 * code bypasses it: every piece of state touched is queried first and put
 * back afterwards, in a finally. If anything fails once, the filter disables
 * itself for the session and says so once; a broken filter must never cost
 * the player their frame loop.
 *
 * <p>Cost: one glCopyTexSubImage2D plus one 3-vertex draw, no per-frame
 * allocation. When the matrix is the identity (Off / strength 0) the frame
 * returns before touching GL at all.
 */
final class Filter {
    private Filter() {
    }

    private static boolean failed;
    private static boolean inited;
    private static boolean core;
    private static int program, uTex, uM;
    private static int vao;
    private static int tex, texW, texH;

    private static final FloatBuffer M = BufferUtils.createFloatBuffer(9);
    private static final IntBuffer I16 = BufferUtils.createIntBuffer(16);

    private static final String FRAG_BODY =
        "uniform sampler2D uTex;\n"
        + "uniform mat3 uM;\n"
        + "vec3 toLin(vec3 c){ return mix(c/12.92, pow((c+0.055)/1.055, vec3(2.4)), step(0.04045, c)); }\n"
        + "vec3 toSrgb(vec3 c){ c = clamp(c, 0.0, 1.0);\n"
        + "  return mix(c*12.92, 1.055*pow(c, vec3(1.0/2.4)) - 0.055, step(0.0031308, c)); }\n"
        + "vec3 filt(vec2 uv){ return toSrgb(uM * toLin(texture2D(uTex, uv).rgb)); }\n";

    private static final String VERT_LEGACY =
        "#version 120\nvarying vec2 vUv;\n"
        + "void main(){ vUv = gl_Vertex.xy*0.5+0.5; gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0); }\n";
    private static final String FRAG_LEGACY =
        "#version 120\nvarying vec2 vUv;\n" + FRAG_BODY
        + "void main(){ gl_FragColor = vec4(filt(vUv), 1.0); }\n";

    private static final String VERT_CORE =
        "#version 150\nout vec2 vUv;\n"
        + "void main(){ vec2 p = vec2((gl_VertexID==1)?3.0:-1.0, (gl_VertexID==2)?3.0:-1.0);\n"
        + "  vUv = p*0.5+0.5; gl_Position = vec4(p, 0.0, 1.0); }\n";
    private static final String FRAG_CORE =
        "#version 150\nin vec2 vUv;\nout vec4 o;\n"
        + FRAG_BODY.replace("texture2D", "texture")
        + "void main(){ o = vec4(filt(vUv), 1.0); }\n";

    /** Entry point from the render hook. Never throws. */
    static void frame(long window) {
        if (failed) {
            return;
        }
        try {
            Settings.flushIfDue();
            float[] m = Cvd.matrix(Settings.type, Settings.mode, Settings.strength01());
            if (Cvd.isIdentity(m)) {
                return;
            }
            int[] fb = new int[2];
            int[] w = new int[1];
            int[] h = new int[1];
            GLFW.glfwGetFramebufferSize(window, w, h);
            fb[0] = w[0];
            fb[1] = h[0];
            if (fb[0] <= 0 || fb[1] <= 0) {
                return;
            }
            if (!inited) {
                init();
            }
            run(m, fb[0], fb[1]);
        } catch (Throwable t) {
            failed = true;
            System.out.println("[colorblind] Filter disabled for this session: " + t);
        }
    }

    private static void init() {
        GLCapabilities caps = GL.getCapabilities();
        if (!caps.OpenGL20) {
            throw new IllegalStateException("needs OpenGL 2.0");
        }
        core = false;
        if (caps.OpenGL32) {
            core = (GL11.glGetInteger(0x9126) & 2) == 0; // GL_CONTEXT_PROFILE_MASK, compat bit
        }
        program = link(core ? VERT_CORE : VERT_LEGACY, core ? FRAG_CORE : FRAG_LEGACY);
        uTex = GL20.glGetUniformLocation(program, "uTex");
        uM = GL20.glGetUniformLocation(program, "uM");
        tex = GL11.glGenTextures();
        if (core) {
            vao = GL30.glGenVertexArrays();
        }
        inited = true;
        System.out.println("[colorblind] Filter ready (" + (core ? "core" : "compatibility") + " profile).");
    }

    private static int link(String vs, String fs) {
        int v = compile(GL20.GL_VERTEX_SHADER, vs);
        int f = compile(GL20.GL_FRAGMENT_SHADER, fs);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        boolean ok = GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) != 0;
        String log = GL20.glGetProgramInfoLog(p);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (!ok) {
            GL20.glDeleteProgram(p);
            throw new IllegalStateException("link failed: " + log);
        }
        return p;
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException("shader compile failed: " + log);
        }
        return s;
    }

    private static boolean enabled(int cap) {
        return GL11.glIsEnabled(cap);
    }

    private static void set(int cap, boolean on) {
        if (on) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private static void run(float[] m, int w, int h) {
        // Only filter the default framebuffer; mid-pass FBO targets are not ours.
        if (GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) != 0) {
            return;
        }
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int oldTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldVao = core ? GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) : 0;
        int oldArrayBuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        I16.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, I16);
        int vx = I16.get(0), vy = I16.get(1), vw = I16.get(2), vh = I16.get(3);
        boolean blend = enabled(GL11.GL_BLEND), depth = enabled(GL11.GL_DEPTH_TEST),
            cull = enabled(GL11.GL_CULL_FACE), scissor = enabled(GL11.GL_SCISSOR_TEST),
            stencil = enabled(GL11.GL_STENCIL_TEST);
        boolean alphaTest = false, lighting = false, tex2d = false;
        if (!core) {
            alphaTest = enabled(GL11.GL_ALPHA_TEST);
            lighting = enabled(GL11.GL_LIGHTING);
            tex2d = enabled(GL11.GL_TEXTURE_2D);
        }
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            if (w != texW || h != texH) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB,
                    GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12ClampToEdge);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12ClampToEdge);
                texW = w;
                texH = h;
            }
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

            set(GL11.GL_BLEND, false);
            set(GL11.GL_DEPTH_TEST, false);
            set(GL11.GL_CULL_FACE, false);
            set(GL11.GL_SCISSOR_TEST, false);
            set(GL11.GL_STENCIL_TEST, false);
            if (!core) {
                set(GL11.GL_ALPHA_TEST, false);
                set(GL11.GL_LIGHTING, false);
            }
            GL11.glDepthMask(false);
            GL11.glViewport(0, 0, w, h);

            GL20.glUseProgram(program);
            GL20.glUniform1i(uTex, 0);
            // Row-major in, column-major out: uM * v needs the transpose.
            M.clear();
            for (int c = 0; c < 3; c++) {
                for (int r = 0; r < 3; r++) {
                    M.put(m[r * 3 + c]);
                }
            }
            M.flip();
            GL20.glUniformMatrix3fv(uM, false, M);

            if (core) {
                GL30.glBindVertexArray(vao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            } else {
                GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glVertex2f(-1f, -1f);
                GL11.glVertex2f(3f, -1f);
                GL11.glVertex2f(-1f, 3f);
                GL11.glEnd();
            }
        } finally {
            GL20.glUseProgram(oldProgram);
            if (core) {
                GL30.glBindVertexArray(oldVao);
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArrayBuf);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTex);
            GL13.glActiveTexture(oldActive);
            GL11.glViewport(vx, vy, vw, vh);
            GL11.glDepthMask(depthMask);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_DEPTH_TEST, depth);
            set(GL11.GL_CULL_FACE, cull);
            set(GL11.GL_SCISSOR_TEST, scissor);
            set(GL11.GL_STENCIL_TEST, stencil);
            if (!core) {
                set(GL11.GL_ALPHA_TEST, alphaTest);
                set(GL11.GL_LIGHTING, lighting);
                set(GL11.GL_TEXTURE_2D, tex2d);
            }
        }
    }

    private static final int GL12ClampToEdge = 0x812F;
}
