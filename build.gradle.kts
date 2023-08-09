plugins {
    kotlin("multiplatform") version "1.9.0"
    java
}

group = "me.admin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }

        tasks.register<Jar>("fatJar") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            group = "build"
//            baseName = "classh"
            archiveFileName.set("classh.jar")
            manifest {
                attributes["Main-Class"] = "io.github.landgrafhomyak.classh.CliMain"
            }
            from(
                compilations["main"].runtimeDependencyFiles
                    .filter(File::exists)
                    .map { d -> if (d.isDirectory) d else zipTree(d) }
            )
            with(tasks.jar.get() as CopySpec)
            destinationDir = rootDir.resolve("out")
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val jvmMain by getting {
            dependencies {
                implementation("org.apache.bcel:bcel:6.7.0")
            }
        }
        val jvmTest by getting
    }
}