/*
 * Copyright 2010 Vrije Universiteit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ibis.io.rewriter;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

class JMESerializationInfo extends SerializationInfo implements JMERewriterConstants {

    static boolean isJMESpecialCase(JavaClass clazz) {
        String className = clazz.getClassName();
        if (
                "java.lang.Object".equals(className) ||
                "java.lang.Boolean".equals(className) ||
                "java.lang.Byte".equals(className) ||
                "java.lang.Short".equals(className) ||
                "java.lang.Integer".equals(className) ||
                "java.lang.Long".equals(className) ||
                "java.lang.Float".equals(className) ||
                "java.lang.Double".equals(className) ||
                "java.lang.Character".equals(className)
        ) {
            return true;
        }
        return false;
    }

    static boolean isJMESerializable(JavaClass clazz) {
        try {
            return  Repository.implementationOf(clazz, TYPE_IBIS_IO_JME_SERIALIZABLE);
        } catch(ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    static boolean isJMERewritten(JavaClass clazz) {
        try {
            return Repository.implementationOf(clazz, TYPE_IBIS_IO_JME_JMESERIALIZABLE);
        } catch(ClassNotFoundException e) {
            throw new Error(e);
        }

    }


    static boolean isJMEExternalizable(JavaClass clazz) {
        try {
            return Repository.implementationOf(clazz, TYPE_IBIS_IO_JME_EXTERNALIZABLE);
        } catch(ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    static boolean hasJMESerialPersistentFields(Field[] fields) {
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            if (f.getName().equals(FIELD_SERIAL_PERSISTENT_FIELDS)
                    && f.isFinal()
                    && f.isStatic()
                    && f.isPrivate()
                    && f.getSignature().equals(
                            TYPE_LIBIS_IO_JME_OBJECT_STREAM_FIELD)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasJMEConstructor(JavaClass cl) {
        Method[] clMethods = cl.getMethods();

        for (int i = 0; i < clMethods.length; i++) {
            if (clMethods[i].getName().equals(METHOD_INIT)
                    && clMethods[i].getSignature().equals(
                            SIGNATURE_LIBIS_IO_JME_OBJECT_INPUT_STREAM_V)) {
                return true;
            }
        }
        return false;
    }

    JMESerializationInfo(String wn, String rn, String frn, Type t,
            boolean primitive) {
        super(wn, rn, frn, t, primitive);
    }
}
