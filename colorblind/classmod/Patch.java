import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import java.io.File;

/** args: projectx-pcode.jar, outdir, dir of compiled mod classes */
public class Patch {
    public static void main(String[] a) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(a[0]);
        pool.insertClassPath(a[2]);
        edit(pool, a[1], "com.threerings.projectx.client.ProjectXApp", "renderView",
            "com.spiralstudio.mod.colorblind.Support.frame($0.getWindow());");
        edit(pool, a[1], "com.threerings.projectx.client.OptionsDialog", "wasAdded",
            "com.spiralstudio.mod.colorblind.Support.inject($0);");
    }

    static void edit(ClassPool pool, String out, String cls, String method, String code) throws Exception {
        CtClass c = pool.get(cls);
        CtMethod[] ms = c.getDeclaredMethods(method);
        if (ms.length != 1 || ms[0].getParameterTypes().length != 0) {
            throw new IllegalStateException(cls + "." + method + " not unique/no-arg: " + ms.length);
        }
        ms[0].insertAfter(code);
        c.writeFile(out);
        System.out.println("patched " + cls + "." + method);
    }
}
