package io.github.landgrafhomyak.classh

import org.apache.bcel.classfile.Method
import org.apache.bcel.generic.ArrayType
import org.apache.bcel.generic.ObjectType
import org.apache.bcel.generic.Type

private fun signatureOfQualname(string: String): String = string.replace('.', '/')

private fun signatureOf(t: Type): String = when (t) {
    Type.BOOLEAN -> "Z"
    Type.BYTE -> "B"
    Type.CHAR -> "C"
    Type.SHORT -> "S"
    Type.INT -> "I"
    Type.LONG -> "J"
    Type.FLOAT -> "F"
    Type.DOUBLE -> "D"
    Type.VOID -> "V"
    is ArrayType -> "[".repeat(t.dimensions) + signatureOf(t.basicType)
    is ObjectType -> "L${signatureOfQualname(t.className)};"
    else -> throw IllegalArgumentException("Specified type can't be mangled: ${t::class.qualifiedName}: $t")
}

fun signatureOf(method: Method): String {
    val sb = StringBuilder("(")
    for (t in method.argumentTypes)
        sb.append(signatureOf(t))
    sb.append(")")
    sb.append(signatureOf(method.returnType))
    return sb.toString()
}