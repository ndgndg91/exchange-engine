import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.exchange"
version = "1.0-SNAPSHOT"

val mainClassFullPath = "com.exchange.MatchingEngineKt"

repositories {
    mavenCentral()
}

dependencies {
    implementation("uk.co.real-logic:sbe-all:1.30.0")
    implementation("io.aeron:aeron-all:1.44.0")
    implementation("com.lmax:disruptor:4.0.0")
    implementation("org.agrona:agrona:1.20.0")
    implementation("org.eclipse.collections:eclipse-collections:11.1.0")
    implementation("org.eclipse.collections:eclipse-collections-api:11.1.0")
    implementation("net.openhft:affinity:3.23.3")
    implementation("net.openhft:chronicle-queue:5.25ea17")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("io.netty:netty-all:4.1.107.Final")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val sbe by configurations.creating
dependencies {
    sbe("uk.co.real-logic:sbe-all:1.30.0")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-exports", "jdk.unsupported/sun.misc=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED"
    )
}

kotlin { jvmToolchain(21) }
tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "21" }

val generatedSrcDir = layout.projectDirectory.dir("src/main/generated")
val schemaFile = layout.projectDirectory.file("../shared/exchange-schema.xml")

sourceSets {
    main { java.srcDir(generatedSrcDir) }
}

tasks.register<JavaExec>("generateSbe") {
    group = "sbe"
    classpath = sbe
    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    systemProperties("sbe.output.dir" to generatedSrcDir.asFile.absolutePath, "sbe.target.language" to "Java")
    args(schemaFile.asFile.absolutePath)
    inputs.file(schemaFile)
    outputs.dir(generatedSrcDir)
}

tasks.named("compileKotlin") { dependsOn("generateSbe") }

tasks.register<JavaExec>("runBenchmark") {
    group = "benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.exchange.benchmark.OrderBookBenchmarkKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = mainClassFullPath
    }
    dependsOn("generateSbe")
}
