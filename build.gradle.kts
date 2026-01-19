import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.exchange"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // 1. SBE Protocol
    implementation("uk.co.real-logic:sbe-all:1.30.0")

    // 2. Aeron Messaging
    implementation("io.aeron:aeron-all:1.44.0")

    // 3. LMAX Disruptor
    implementation("com.lmax:disruptor:4.0.0")

    // 4. Agrona (High Performance Buffers)
    implementation("org.agrona:agrona:1.20.0")

    // 5. Primitive Collections (Zero-GC)
    implementation("org.eclipse.collections:eclipse-collections:11.1.0")
    implementation("org.eclipse.collections:eclipse-collections-api:11.1.0")

    // 6. CPU Pinning
    implementation("net.openhft:affinity:3.23.3")

    // 7. Chronicle Queue (Journaling)
    implementation("net.openhft:chronicle-queue:5.25ea17")

    // 8. Database (Persistence)
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.2")

    // 9. Network (Gateway)
    implementation("io.netty:netty-all:4.1.107.Final")

    // Testing
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

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

application {
    mainClass.set("com.exchange.MatchingEngineKt")
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED" // Often needed for Agrona/Aeron
    )
}

// Custom Task for SBE Code Generation
val generatedSrcDir = layout.projectDirectory.dir("src/main/generated")
val schemaFile = layout.projectDirectory.file("src/main/resources/exchange-schema.xml")

sourceSets {
    main {
        java.srcDir(generatedSrcDir)
    }
}

tasks.register<JavaExec>("generateSbe") {
    group = "sbe"
    description = "Generates Java stubs from SBE schema"
    
    classpath = sbe
    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    
    systemProperties(
        "sbe.output.dir" to generatedSrcDir.asFile.absolutePath,
        "sbe.target.language" to "Java",
        "sbe.validation.stop.on.error" to "true"
    )
    
    args(schemaFile.asFile.absolutePath)
    
    // Only run if schema changed or output is missing
    inputs.file(schemaFile)
    outputs.dir(generatedSrcDir)
}

// Hook into build process
tasks.named("compileKotlin") {
    dependsOn("generateSbe")
}

tasks.named("shadowJar") {
    dependsOn("generateSbe")
}
