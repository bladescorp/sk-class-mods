package colorblindtest;

/** Mounts through Core exactly like the agent and verifies both hooked classes load under -Xverify:all. */
public final class Harness {
    public static void main(String[] args) throws Exception {
        Class.forName("com.spiralstudio.mod.colorblind.Main");
        com.spiralstudio.mod.core.Registers.init();
        com.spiralstudio.mod.core.ClassPool.init();
        Class.forName("com.threerings.projectx.client.ProjectXApp");
        Class.forName("com.threerings.projectx.client.OptionsDialog");
        System.out.println("[test] PASS ProjectXApp and OptionsDialog transformed and verified");
    }
}
