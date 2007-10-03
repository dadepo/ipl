/* $Id:$ */

package ibis.frontend.ibis;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.SyntheticRepository;


/**
 * BCEL implementation of the <code>ByteCodeWrapper</code> interface.
 */
public class BCELWrapper implements ByteCodeWrapper {

    private HashMap javaClasses = new HashMap();

    public BCELWrapper(ArrayList args) {
        String classPath = ClassPath.getClassPath();
        String sep = System.getProperty("path.separator");
        for (int i = 0; i < args.size(); i++) {
            String arg = (String) args.get(i);
            classPath = classPath + sep + arg;
        }
        SyntheticRepository rep = SyntheticRepository.getInstance(
                new ClassPath(classPath));
        Repository.setRepository(rep);
    }

    public ClassInfo getInfo(Object o) {
        JavaClass cl = (JavaClass) o;
        String name = cl.getClassName();
        BCELClassInfo e = (BCELClassInfo) javaClasses.get(name);
        if (e == null) {
            e = new BCELClassInfo(cl);
            javaClasses.put(name, e);
        }
        e.setClassObject(cl);
        return e;
    }

    public ClassInfo parseClassFile(String fileName) throws IOException {
        JavaClass cl = new ClassParser(fileName).parse();
        Repository.addClass(cl);
        String name = cl.getClassName();
        BCELClassInfo e = (BCELClassInfo) javaClasses.get(name);
        if (e == null) {
            e = new BCELClassInfo(cl);
            javaClasses.put(name, e);
        }
        e.setClassObject(cl);
        Repository.addClass(cl);
        return e;
    }

    public ClassInfo parseInputStream(InputStream in, String fileName)
            throws IOException {
        JavaClass cl = new ClassParser(in, fileName).parse();
        Repository.addClass(cl);
        String name = cl.getClassName();
        BCELClassInfo e = (BCELClassInfo) javaClasses.get(name);
        if (e == null) {
            e = new BCELClassInfo(cl);
            javaClasses.put(name, e);
        }
        e.setClassObject(cl);
        Repository.addClass(cl);
        return e;
    }
}
