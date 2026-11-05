import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "com.vericore"
version = "1.0.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.28.2")
    implementation("com.squareup:kotlinpoet:1.16.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0")
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.12")
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
}

application {
    mainClass.set("com.vericore.MainKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

tasks.test {
    useJUnitPlatform()
    workingDir(project.projectDir)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.vericore.MainKt"
    }
}

// Keep the generated Windows launcher classpath compact and enforce runtime Java requirements.
tasks.withType<CreateStartScripts>().configureEach {
    doLast {
        val winScript = windowsScript
        val winLines = winScript.readLines().toMutableList()
        val winClasspathIndex = winLines.indexOfFirst { it.startsWith("set CLASSPATH=") }
        require(winClasspathIndex >= 0) {
            "Expected Gradle Windows start script to contain a CLASSPATH declaration: $winScript"
        }
        winLines[winClasspathIndex] = "set CLASSPATH=%APP_HOME%\\lib\\*"

        val winExecuteIndex = winLines.indexOfFirst { it.trim() == ":execute" }
        require(winExecuteIndex >= 0) {
            "Expected Gradle Windows start script to contain :execute label: $winScript"
        }
        val winJavaCheck = listOf(
            "@rem Check Java version requirement: Java 25 or newer",
            "set VERICORE_JAVA_MAJOR=",
            "for /f \"tokens=3\" %%g in ('\"%JAVA_EXE%\" -version 2^>^&1') do (",
            "    if not defined VERICORE_JAVA_MAJOR (",
            "        for /f \"tokens=1,2 delims=.- \" %%a in (\"%%~g\") do (",
            "            if \"%%a\"==\"1\" (set VERICORE_JAVA_MAJOR=%%b) else (set VERICORE_JAVA_MAJOR=%%a)",
            "        )",
            "    )",
            ")",
            "if not defined VERICORE_JAVA_MAJOR goto skipJavaCheck",
            "if %VERICORE_JAVA_MAJOR% lss 25 goto javaOutdated",
            "goto skipJavaCheck",
            "",
            ":javaOutdated",
            "echo VERICORE JAVA VERSION REQUIREMENT 1>&2",
            "echo. 1>&2",
            "echo Installed Java version: %VERICORE_JAVA_MAJOR% 1>&2",
            "echo Required Java version: 25 or newer 1>&2",
            "echo. 1>&2",
            "echo Vericore cannot start because your Java runtime is outdated. 1>&2",
            "echo. 1>&2",
            "echo Please install JDK 25 or newer and try again. 1>&2",
            "exit /b 1",
            "",
            ":skipJavaCheck"
        )
        winLines.addAll(winExecuteIndex + 1, winJavaCheck)
        winScript.writeText(winLines.joinToString("\r\n"))

        val uScript = unixScript
        val uLines = uScript.readLines().toMutableList()
        val uAfterJavaCmd = uLines.indexOfFirst { it.trim() == "# Increase the maximum file descriptors if we can." }
        require(uAfterJavaCmd >= 0) {
            "Expected Gradle Unix start script to contain descriptor limit section: $uScript"
        }
        val uJavaCheck = listOf(
            "# Check Java version requirement: Java 25 or newer",
            "VERICORE_JAVA_RAW=\"$(\"\$JAVACMD\" -version 2>&1 | awk -F'\"' '/version/ {print \$2; exit}')\"",
            "VERICORE_JAVA_MAJOR=\"\${VERICORE_JAVA_RAW%%.*}\"",
            "if [ \"\$VERICORE_JAVA_MAJOR\" = \"1\" ]; then",
            "    VERICORE_JAVA_MAJOR=\"\${VERICORE_JAVA_RAW#1.}\"",
            "    VERICORE_JAVA_MAJOR=\"\${VERICORE_JAVA_MAJOR%%.*}\"",
            "fi",
            "if [ -n \"\$VERICORE_JAVA_MAJOR\" ] && [ \"\$VERICORE_JAVA_MAJOR\" -lt 25 ] 2>/dev/null; then",
            "    cat >&2 <<EOF",
            "VERICORE JAVA VERSION REQUIREMENT",
            "",
            "Installed Java version: \$VERICORE_JAVA_MAJOR",
            "Required Java version: 25 or newer",
            "",
            "Vericore cannot start because your Java runtime is outdated.",
            "",
            "Please install JDK 25 or newer and try again.",
            "EOF",
            "    exit 1",
            "fi",
            ""
        )
        uLines.addAll(uAfterJavaCmd, uJavaCheck)
        uScript.writeText(uLines.joinToString("\n"))
    }
}
