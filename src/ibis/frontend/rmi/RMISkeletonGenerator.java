package ibis.frontend.rmi;

import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.*;

import java.util.Vector;
import java.io.PrintWriter;
import ibis.util.BT_Analyzer;

class RMISkeletonGenerator extends RMIGenerator {

	BT_Analyzer data;
	PrintWriter output;
	boolean verbose;

	String dest_name;

	RMISkeletonGenerator(BT_Analyzer data, PrintWriter output, boolean verbose) {
		this.data   = data;
		this.output = output;
		this.verbose = verbose;
	}

	void header() {
		if (data.packagename != null && ! data.packagename.equals("")) {
			output.println("package " + data.packagename + ";");
			output.println();
		}

		output.println("import ibis.rmi.*;");
		output.println("import java.lang.reflect.*;");
		output.println("import ibis.ipl.*;");
		output.println("import java.io.IOException;");
		output.println();

		output.println("public final class rmi_skeleton_" + dest_name + " extends ibis.rmi.server.Skeleton implements ibis.ipl.Upcall {");
		output.println();
	}

	private boolean simpleMethod(Method m) {
	    Code c = m.getCode();

	    if (c == null) {
		return true;
	    }

	    InstructionList il = new InstructionList(c.getCode());

	    Instruction[] ins = il.getInstructions();

	    int len = 0;

	    for (int i = 0; i < ins.length; i++) {
		if (ins[i] instanceof InvokeInstruction) return false;
		if (ins[i] instanceof MONITORENTER) return false;
		if (ins[i] instanceof BranchInstruction) {
		    BranchInstruction ib = (BranchInstruction) ins[i];
		    if (ib.getIndex() <= len) {
			// A jump backwards. Assume loop.
			return false;
		    }
		}
		len += ins[i].getLength();
	    }
	    return true;
	}

	void messageHandler(Vector methods) {

		output.println("\tpublic final void upcall(ReadMessage r) throws IOException {");
		output.println();

		//gosia
		// Do not use IP addresses, a machine may have multiple, and
		// Ibis does not know about IP anymore --Rob
		output.println("\t\tRTS.setClientHost(r.origin().ibis().name());");
		//end gosia

		output.println("\t\tException ex = null;");
		output.println("\t\tint method = r.readInt();");
		output.println("\t\tint stubID = r.readInt();");
		output.println();

		output.println("\t\tswitch(method) {");

		for (int i=0;i<methods.size();i++) {
			Method m = (Method) methods.get(i);
			Type ret = getReturnType(m);
			Type[] params = getParameterTypes(m);

			output.println("\t\tcase " + i + ":");
			output.println("\t\t{");

			output.println("\t\t\t/* First - Extract the parameters */");

			for (int j=0;j<params.length;j++) {
				Type temp = params[j];
				output.println("\t\t\t" + temp + " p" + j + ";");
			}
			for (int k=0;k<params.length;k++) {
				Type temp = params[k];
				if (temp instanceof BasicType) {
				    output.println(readMessageType("\t\t\t", "p" + k, "r", temp));
				}
				else {
				    output.println("\t\t\ttry {");
				    output.println(readMessageType("\t\t\t\t", "p" + k, "r", temp));
				    output.println("\t\t\t} catch(ClassNotFoundException e) {");
				    output.println("\t\t\t\tthrow new RemoteException(\"Class not found\", e);");
				    output.println("\t\t\t}");
				}
			}

			// we should try to optimize this finish away, to avoid thread cration!!! --Rob
			// We can do this if
			// - the method does not have loops
			// - the method does not do any other method invocations.
			// - no synchronization
			// (Ceriel)
			if (! simpleMethod(m)) {
			    output.println("\t\t\tr.finish();");
			}
			output.println();

			output.println("\t\t\t/* Second - Invoke the method */");

			if (!ret.equals(Type.VOID)) {
				output.println("\t\t\t" + getInitedLocal(ret, "result") + ";");
			}

			output.println("\t\t\ttry {");
			output.print("\t\t\t\t");

			if (!ret.equals(Type.VOID)) {
				output.print("result = ");
			}

			output.print("((" + dest_name + ") destination)." + m.getName() + "(");

			for (int j=0;j<params.length;j++) {
				output.print("p" + j);

				if (j<params.length-1) {
					output.print(", ");
				}
			}
			output.println(");");
			output.println("\t\t\t} catch (Exception e) {");
			output.println("\t\t\t\tex = e;");
			output.println("\t\t\t}");

			output.println("\t\t\tWriteMessage w = stubs[stubID].newMessage();");

			output.println("\t\t\tif (ex != null) {");
			output.println("\t\t\t\tw.writeByte(ibis.rmi.Protocol.EXCEPTION);");
			output.println("\t\t\t\tw.writeObject(ex);");
			output.println("\t\t\t} else {");
			output.println("\t\t\t\tw.writeByte(ibis.rmi.Protocol.RESULT);");

			if (!ret.equals(Type.VOID)) {
				output.println(writeMessageType("\t\t\t\t", "w", ret, "result"));
			}

			output.println("\t\t\t}");
			output.println("\t\t\tw.send();");
			output.println("\t\t\tw.finish();");

			output.println("\t\t\tbreak;");
			output.println("\t\t}");
			output.println();
		}

		output.println("\t\tcase -1:");
		output.println("\t\t{");
		output.println("\t\t\t/* Special case for new stubs that are connecting */");
		output.println("\t\t\tReceivePortIdentifier rpi;");
		output.println("\t\t\ttry {");
		output.println("\t\t\t\trpi = (ReceivePortIdentifier) r.readObject();");
		output.println("\t\t\t} catch(ClassNotFoundException e) {");
		output.println("\t\t\t\tthrow new Error(\"while reading ReceivePortIdentifier\", e);");
		output.println("\t\t\t}");
		/* output.println("\t\t\tr.finish();"); Not needed! */
		output.println("\t\t\tint id = addStub(rpi);");
		output.println("\t\t\tWriteMessage w = stubs[id].newMessage();");
		output.println("\t\t\tw.writeInt(id);");
		output.println("\t\t\tw.writeInt(skeletonId);");
		output.println("\t\t\tw.writeString(stubType);");
		output.println("\t\t\tw.send();");
		output.println("\t\t\tw.finish();");
		output.println("\t\t\tbreak;");
		output.println("\t\t}");
		output.println();

		output.println("\t\tdefault:");
		output.println("\t\t\tthrow new Error(\"illegal method number\");");

		output.println("\t\t}");

		output.println("\t}");
		output.println();
	}

	void trailer() {
		output.println("}\n");
	}

	void constructor(Vector methods) {

		output.println("\tpublic rmi_skeleton_" + data.classname + "() {");

//		output.println("\t\tsuper();");
		if (data.packagename != null && ! data.packagename.equals("")) {
		    output.println("\t\tstubType = \"" + data.packagename + ".rmi_stub_" + data.classname + "\";");
		}
		else {
		    output.println("\t\tstubType = \"rmi_stub_" + data.classname + "\";");
		}
		output.println();
		output.println("\t}\n");
	}

	void generate() {

		dest_name = data.classname;

		header();
		constructor(data.subjectSpecialMethods);
		messageHandler(data.subjectSpecialMethods);
		trailer();
	}
}






