plugins {
    // No application or Spring plugins at root level.
    // Root project only provides shared configuration for subprojects.
}

allprojects {
    group = "cbs.nove"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    tasks.withType<Test> {
        testLogging {
            showStandardStreams = true
            showExceptions = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }
}
