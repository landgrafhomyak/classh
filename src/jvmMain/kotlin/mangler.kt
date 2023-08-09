package io.github.landgrafhomyak.classh

import org.apache.bcel.classfile.JavaClass
import org.apache.bcel.classfile.Method
import org.apache.bcel.generic.*

private fun mangle(everything: String): String = everything.asSequence().joinToString(prefix = "", postfix = "", separator = "") { c ->
    when (c) {
        in 'a'..'z', in 'A'..'Z', in '0'..'9' -> return@joinToString c.toString()
        '_' -> return@joinToString "_1"
        ';' -> return@joinToString "_2"
        '[' -> return@joinToString "_3"
        else -> "_0${c.code.toString(16).lowercase().padStart(4, '0')}"
    }
}

private fun manglePackageOrQualname(pkgString: String): String =
    pkgString.split(".").joinToString(prefix = "", postfix = "", separator = "_", transform = ::mangle)

private fun mangleArrayType(t: ArrayType) = "_3".repeat(t.dimensions) + mangleType(t.basicType)

private fun mangleType(t: Type): String = when (t) {
    Type.BOOLEAN -> "Z"
    Type.BYTE -> "B"
    Type.CHAR -> "C"
    Type.SHORT -> "S"
    Type.INT -> "I"
    Type.LONG -> "J"
    Type.FLOAT -> "F"
    Type.DOUBLE -> "D"
    is ArrayType -> mangleArrayType(t)
    is ObjectType -> "L${manglePackageOrQualname(t.className)}_2"
    else -> throw IllegalArgumentException("Specified type can't be mangled: ${t::class.qualifiedName}: $t")
}

private fun mangleShortName0(cls: JavaClass, method: Method): StringBuilder {
    val sb = StringBuilder("Java_")
    sb.append(manglePackageOrQualname(cls.className))
    sb.append("_")
    sb.append(mangle(method.name))
    return sb
}

fun mangleShortName(cls: JavaClass, method: Method): String {
    return mangleShortName0(cls, method).toString()
}


fun mangleLongName(cls: JavaClass, method: Method): String {
    val sb = mangleShortName0(cls, method)
    sb.append("__")
    for (t in method.argumentTypes) {
        sb.append(mangleType(t))
    }
    return sb.toString()
}

