import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: no Android dependencies, so parsers are fast to unit-test.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // SamplesFileTest reads the user's real SMS from <project root>/samples.txt (if present)
    // and writes a per-message report next to the other test reports.
    val samples = rootProject.layout.projectDirectory.file("samples.txt").asFile
    val report = layout.buildDirectory.file("reports/samples-report.txt").get().asFile
    systemProperty("samples.file", samples.absolutePath)
    systemProperty("samples.report", report.absolutePath)
    inputs.property("samplesPresent", samples.exists())
    if (samples.exists()) inputs.file(samples)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
