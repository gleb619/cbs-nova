plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java", "src/main/kotlin")
        }
    }
    test {
        java {
            srcDirs("src/test/java", "src/test/kotlin")
        }
    }
}

// ---------------------------------------------------------------------------
// Annotation-processor ordering: Lombok MUST be resolved/applied before
// MapStruct so that Lombok-generated getters/setters are visible to the
// MapStruct processor. Declaration order below enforces this.
// ---------------------------------------------------------------------------

dependencies {
    // -- Spring Boot starters (bundles) --
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.bundles.spring.security)

    // -- Flyway --
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // -- Avaje Validator --
    implementation(libs.avaje.validator.spring.starter)

    // -- Kotlin runtime --
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    // -- Lombok (compileOnly) + MapStruct (implementation for generated classes) --
    compileOnly(libs.lombok)
    implementation(libs.mapstruct)

    // -- Annotation processors (Lombok FIRST, then MapStruct) --
    // Gradle processes annotation-processor dependencies in declaration order
    // within the same configuration, so Lombok is guaranteed to run before
    // MapStruct.
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)

    // -- Testing --
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("25"))
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.bootRun {
    jvmArgs("--spring.profiles.active=dev")
}
