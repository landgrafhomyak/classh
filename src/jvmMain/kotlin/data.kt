package io.github.landgrafhomyak.classh

import org.apache.bcel.classfile.JavaClass
import org.apache.bcel.generic.ArrayType
import org.apache.bcel.generic.ObjectType
import org.apache.bcel.generic.Type
import org.apache.bcel.util.ClassPath
import org.apache.bcel.util.Repository
import org.apache.bcel.util.SyntheticRepository
import java.util.Objects
import javax.naming.OperationNotSupportedException

class JniMethodHead(
    val classQualname: String,
    val methodName: String,
    val signature: String,
    val jniName: String,
    val isStatic: Boolean,
    val returnType: JniType,
    val argsTypes: Array<JniType>
)

enum class JniType(val cType: String) {
    VOID("void"),
    BOOLEAN("jboolean"),
    BOOLEAN_ARRAY("jbooleanArray"),
    CHAR("jchar"),
    CHAR_ARRAY("jcharArray"),
    BYTE("jbyte"),
    BYTE_ARRAY("jbyteArray"),
    SHORT("jshort"),
    SHORT_ARRAY("jshortArray"),
    INT("jint"),
    INT_ARRAY("jintArray"),
    LONG("jlong"),
    LONG_ARRAY("jlongArray"),
    FLOAT("jfloat"),
    FLOAT_ARRAY("jfloatArray"),
    DOUBLE("jdouble"),
    DOUBLE_ARRAY("jdoubleArray"),
    OBJECT("jobject"),
    OBJECT_ARRAY("jobjectArray"),
    CLASS("jclass"),
    STRING("jstring"),
    THROWABLE("jthrowable")
}

fun java2jniType(t: Type, typesResolver: Type2ClassMap): JniType = when (t) {
    Type.BOOLEAN -> JniType.BOOLEAN
    Type.BYTE -> JniType.BYTE
    Type.CHAR -> JniType.CHAR
    Type.SHORT -> JniType.SHORT
    Type.INT -> JniType.INT
    Type.LONG -> JniType.LONG
    Type.FLOAT -> JniType.FLOAT
    Type.DOUBLE -> JniType.DOUBLE
    Type.VOID -> JniType.VOID
    Type.STRING -> JniType.STRING
    Type.THROWABLE -> JniType.THROWABLE
    Type.CLASS -> JniType.CLASS
    is ArrayType -> if (t.dimensions > 1) JniType.OBJECT_ARRAY else when (t.elementType) {
        Type.BOOLEAN -> JniType.BOOLEAN_ARRAY
        Type.BYTE -> JniType.BYTE_ARRAY
        Type.CHAR -> JniType.CHAR_ARRAY
        Type.SHORT -> JniType.SHORT_ARRAY
        Type.INT -> JniType.INT_ARRAY
        Type.LONG -> JniType.LONG_ARRAY
        Type.FLOAT -> JniType.FLOAT_ARRAY
        Type.DOUBLE -> JniType.DOUBLE_ARRAY
        else -> JniType.OBJECT_ARRAY
    }

    is ObjectType -> when {
        typesResolver[t].instanceOf(typesResolver[Type.STRING]) -> JniType.STRING
        typesResolver[t].instanceOf(typesResolver[Type.THROWABLE]) -> JniType.THROWABLE
        typesResolver[t].instanceOf(typesResolver[Type.CLASS]) -> JniType.CLASS
        else -> JniType.OBJECT
    }

    else -> JniType.OBJECT
}

fun Array<Type>.map2JniTypes(typesResolver: Type2ClassMap): Array<JniType> = Array(this.size) { i -> java2jniType(this[i], typesResolver) }

fun JavaClass.extractNativeMethods(typesResolver: Type2ClassMap): List<JniMethodHead> = this.methods
    .filter { m -> m.isNative }
    .map { m ->
        JniMethodHead(
            this.className,
            m.name,
            signatureOf(m),
            mangleLongName(this, m),
            m.isStatic,
            java2jniType(m.returnType, typesResolver),
            m.argumentTypes.map2JniTypes(typesResolver)
        )
    }

fun Array<JavaClass>.extractNativeMethods(typesResolver: Type2ClassMap): List<JniMethodHead> = this.flatMap { c -> c.extractNativeMethods(typesResolver) }
fun Iterable<JavaClass>.extractNativeMethods(typesResolver: Type2ClassMap): List<JniMethodHead> = this.flatMap { c -> c.extractNativeMethods(typesResolver) }
fun Sequence<JavaClass>.extractNativeMethods(typesResolver: Type2ClassMap): Sequence<JniMethodHead> = this.flatMap { c -> c.extractNativeMethods(typesResolver) }


class Type2ClassMap {
    private val map = HashMap<String, JavaClass>()
    private val systemCp = SyntheticRepository.getInstance()

    operator fun set(name: String, type: JavaClass) {
        this.map[name] = type
    }


    operator fun get(name: String): JavaClass =
        this.map[name] ?: this.systemCp.loadClass(name) ?: throw ClassNotFoundException(name)

    operator fun get(type: ObjectType): JavaClass = this[type.className]
}

