@file:JvmName("CliMain")

package io.github.landgrafhomyak.classh

import org.apache.bcel.classfile.ClassParser
import org.apache.bcel.classfile.JavaClass
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.LinkedList
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess


private class Args {
    val classpath = LinkedList<String>()
    var outputHeader: String? = null
    var outputDirectory: String? = null
    var printToStdout = false
    var exportAll = false
    val exportClasses = HashSet<String>()
    var isHelp = false
    val generatorOptions = GeneratorOptions()
    val errors = LinkedList<ArgError>()
    var noColor = false
}


private class ArgError(val argPos: Int, val msg: String)

private fun parseArgs(argv: Array<String>) = parseArgs(Args(), argv)

private fun parseArgs(data: Args, argv: Array<String>): Args {
    if (argv.isNotEmpty() && argv[0] == "-help") {
        data.isHelp = true
        return data
    }

    var i = 0;
    argv@ while (i < argv.size) {
        when (argv[i]) {
            "-cp", "--cpasspath" -> {
                if (i + 1 >= argv.size) {
                    data.errors.add(ArgError(i, "Argument to classpath option (${argv[i]}) not passed"))
                    return data
                }
                data.classpath.add(argv[i + 1])
                i += 2
                continue@argv
            }

            "-o" -> {
                if (i + 1 >= argv.size) {
                    data.errors.add(ArgError(i, "Argument to output header option (-o) not passed"))
                    return data
                }
                when {
                    data.outputDirectory != null -> data.errors.add(ArgError(i, "Output directory already specified"))
                    data.outputHeader != null -> data.errors.add(ArgError(i, "Output header file already specified"))
                    data.printToStdout -> data.errors.add(ArgError(i, "Output already redirected to stdout"))
                    else -> data.outputHeader = argv[i + 1]
                }
                i += 2
                continue@argv
            }

            "-d" -> {
                if (i + 1 >= argv.size) {
                    data.errors.add(ArgError(i, "Argument to output directory option (-d) not passed"))
                    return data
                }
                when {
                    data.generatorOptions.includeGuardName != null -> data.errors.add(ArgError(i, "Can't generate multiple files becuse include guard specified, use -o option"))
                    data.outputDirectory != null -> data.errors.add(ArgError(i, "Output directory already specified"))
                    data.outputHeader != null -> data.errors.add(ArgError(i, "Output header file already specified"))
                    data.printToStdout -> data.errors.add(ArgError(i, "Output already redirected to stdout"))
                    else -> data.outputDirectory = argv[i + 1]
                }
                i += 2
                continue@argv
            }

            "--print" -> {
                when {
                    data.outputDirectory != null -> data.errors.add(ArgError(i, "Output directory already specified"))
                    data.outputHeader != null -> data.errors.add(ArgError(i, "Output header file already specified"))
                    data.printToStdout -> data.errors.add(ArgError(i, "Output already redirected to stdout"))
                    else -> data.printToStdout = true
                }
                i++
                continue@argv
            }

            "--pragma-once" -> {
                when {
                    data.generatorOptions.usePragmaOnce -> data.errors.add(ArgError(i, "#pragma once already enabled"))
                    data.generatorOptions.includeGuardName != null -> data.errors.add(ArgError(i, "Include guard already enabled"))
                    else -> data.generatorOptions.usePragmaOnce = true
                }
                i++
                continue@argv
            }

            "--include-guard-name" -> {
                if (i + 1 >= argv.size) {
                    data.errors.add(ArgError(i, "Argument to include guard name (--include-guard-name) not passed"))
                    return data
                }
                when {
                    data.outputDirectory != null -> data.errors.add(ArgError(i, "#pragma once can be used only if single output file specified (-o)"))
                    data.generatorOptions.usePragmaOnce -> data.errors.add(ArgError(i, "#pragma once already enabled"))
                    data.generatorOptions.includeGuardName != null -> data.errors.add(ArgError(i, "Include guard already specified"))
                    else -> data.generatorOptions.includeGuardName = argv[i + 1]
                }
                i += 2
                continue@argv
            }

            "--no-comments" -> {
                if (!data.generatorOptions.verbose)
                    data.errors.add(ArgError(i, "Comments already disabled"))
                else
                    data.generatorOptions.verbose = false
                i++
                continue@argv
            }

            "--no-color" -> {
                if (data.noColor)
                    data.errors.add(ArgError(i, "Coloring already disabled"))
                else
                    data.noColor = true
                i++
                continue@argv
            }
            "--all" -> break@argv
            else -> {
                if (argv[i].startsWith("-")) {
                    data.errors.add(ArgError(i, "Unknown option"))
                    i++
                    continue@argv
                } else
                    break@argv
            }
        }
    }
    val j = i
    export@ while (i < argv.size) {
        if (argv[i] == "--all") {
            if (i != j || i + 1 < argv.size) {
                data.errors.add(ArgError(i, "Wildcard can be used only if there are no another requested classes"))
                break@export
            }
            data.exportAll = true
        } else {
            data.exportClasses.add(argv[i])
        }
        i++
    }

    if (data.outputDirectory == null && data.outputHeader == null && !data.printToStdout) {
        data.errors.add(ArgError(-1, "You should use one of this options: -o, -d or --print"))
    }
    if (data.classpath.isEmpty() && !data.exportAll) {
        data.errors.add(ArgError(-1, "You should request at least one class to export or use wildcard"))
    }
    if (data.outputDirectory == null && data.generatorOptions.includeGuardName == null) {
        data.errors.add(ArgError(-1, "Printing to stdout or joining to single file requires manually set include guard (--include-guard-name)"))
    }
    return data
}


fun main(argv: Array<String>) {
    val args = parseArgs(argv)
    if (args.isHelp) {
        println("help")
        exitProcess(0)
    }
    val stdOutHeader: PrintStream?
    if (args.printToStdout) {
        stdOutHeader = System.out
        System.setOut(System.err)
    } else {
        stdOutHeader = null
    }

    if (args.errors.isNotEmpty()) {
        for (err in args.errors) {
            if (err.argPos < 0) {
                println("!")
            } else {
                println("#${err.argPos}: ${argv[err.argPos]}")
            }
            println("\t${err.msg}")
            println()
        }
        exitProcess(1)
    }

    args.outputDirectory?.let(::File)?.let { f ->
        if (!f.exists()) {
            println("Destination directory doesn't exists")
            exitProcess(1)
        }
        if (!f.isDirectory) {
            println("Destination directory is not a directory")
            exitProcess(1)
        }
        if (!f.canWrite()) {
            println("Can't write to destination directory")
            exitProcess(1)
        }
    }

    args.outputHeader?.let(::File)?.let { f ->
        if (f.exists() && !f.isFile) {
            println("Destination file is not a file")
            exitProcess(1)
        }
        if (!f.absoluteFile.parentFile.exists()) {
            println("Directory for destination file doesn't exists")
            exitProcess(1)
        }
        if (f.exists() && !f.canWrite()) {
            println("Can't write to destination file")
            exitProcess(1)
        }
    }

    val data = LinkedList<JavaClass>()
    cp@ for (req in args.classpath) {
        val f = File(req)
        if (!f.exists()) {
            println("Can't find file: ${req}")
            continue@cp
        }

        if (f.isDirectory) {
            f.walkTopDown()
                .filter { sf -> sf.name.endsWith(".class") }
                .map { sf -> ClassParser(sf.inputStream(), sf.name).parse() }
                .forEach(data::add)
        } else {
            val z: ZipFile? = try {
                ZipFile(f)
            } catch (_: ZipException) {
                null
            }

            if (z != null) {
                z.entries()
                    .asSequence()
                    .filter { e -> e.name.endsWith(".class") }
                    .map { e -> e to z.getInputStream(e) }
                    .map { (e, s) -> ClassParser(s, e.name).parse() }
                    .forEach(data::add)
            } else {
                data.add(ClassParser(f.inputStream(), f.name).parse())
            }
        }
    }

    val data2export: List<JavaClass> = if (args.exportAll) data else data.filter { c -> c.className in args.exportClasses }

    if (args.outputHeader != null) {
        generateHeader(
            args.generatorOptions,
            data2export.extractNativeMethods(),
            PrintStream(FileOutputStream(args.outputHeader!!, false))
        )
    } else if (args.outputDirectory != null) {
        val re = Regex("""[_$]""")
        val d = File(args.outputDirectory!!)
        if (!d.isDirectory) {
            println("${d.absolutePath} is not a directory")
            return
        }
        data2export
            .groupBy({ c -> c.className.replace(re, "_") }) { c -> c.extractNativeMethods() }
            .entries.associate { (c, n) -> c to n.flatten() }
            .asSequence()
            .onEach { (c, n) ->
                generateHeader(
                    args.generatorOptions.withIncludeGuard("_Included_$c"),
                    n,
                    PrintStream(FileOutputStream(d.resolve("${c}.h"), false))
                )
            }
    } else {
        generateHeader(
            args.generatorOptions,
            data2export.extractNativeMethods(),
            stdOutHeader!!
        )
    }
}