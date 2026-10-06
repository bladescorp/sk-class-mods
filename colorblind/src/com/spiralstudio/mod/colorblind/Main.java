package com.spiralstudio.mod.colorblind;

import com.spiralstudio.mod.core.ClassPool;
import com.spiralstudio.mod.core.MethodModifier;
import com.spiralstudio.mod.core.Registers;

/**
 * Colorblind accessibility filter.
 *
 * <p>Two insert-only hooks (inserts compose with other mods; a body()
 * replacement would delete theirs - see windowtoosmall's regression):
 * <ul>
 *   <li>{@code ProjectXApp.renderView()} insertAfter: filter the finished
 *       frame. Both names are unobfuscated and have survived every patch.</li>
 *   <li>{@code OptionsDialog.wasAdded()} insertBefore-of-return: add the
 *       controls to Options &gt; Video each time the dialog opens.</li>
 * </ul>
 * Game types live in {@link Support}/{@link Filter}, never here.
 */
public final class Main {
    private static boolean mounted;

    static {
        Registers.add(Main.class);
    }

    private Main() {
    }

    public static void mount() {
        if (mounted) {
            return;
        }
        mounted = true;
        Settings.load();
        try {
            ClassPool.from("com.threerings.projectx.client.ProjectXApp")
                .modifyMethod(new MethodModifier()
                    .methodName("renderView")
                    .paramTypeNames()
                    .insertAfter("com.spiralstudio.mod.colorblind.Support.frame($0.getWindow());"));
        } catch (Throwable t) {
            System.err.println("[colorblind] Could not hook the render loop (filter inactive): " + t);
        }
        try {
            ClassPool.from("com.threerings.projectx.client.OptionsDialog")
                .modifyMethod(new MethodModifier()
                    .methodName("wasAdded")
                    .paramTypeNames()
                    .insertAfter("com.spiralstudio.mod.colorblind.Support.inject($0);"));
        } catch (Throwable t) {
            System.err.println("[colorblind] Could not hook Options (no Video controls; edit colorblind.yml): " + t);
        }
        System.out.println("[colorblind] Mounted: " + Settings.type + " " + Settings.mode + " " + Settings.strength + "%");
    }
}
