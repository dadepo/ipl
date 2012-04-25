package ibis.compile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;

public class ASMClassInfo implements ClassInfo {
    
    private ClassNode n;
    
    public ASMClassInfo(ClassNode n) {
        this.n = n;
    }
    
    @Override
    public String getClassName() {
        return n.name.replaceAll("/", ".");
    }

    @Override
    public Object getClassObject() {
        return n;
    }
    

    void setClassObject(ClassNode n) {
        this.n = n;
    }

    @Override
    public void dump(String fileName) throws IOException {
        byte[] b = getBytes();
        FileOutputStream o = new FileOutputStream(fileName);
        o.write(b);
        o.close();
    }

    @Override
    public byte[] getBytes() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        // TraceClassVisitor tw = new TraceClassVisitor(new PrintWriter(System.out));
        // n.accept(tw);
        n.accept(w);
        return w.toByteArray();
    }

    @Override
    public boolean doVerify() {
        return true;
    }
}