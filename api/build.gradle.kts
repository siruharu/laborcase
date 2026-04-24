import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    val kotlinVersion = "2.0.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "11.1.1"
}

group = "kr.laborcase"
version = "0.1.0-SNAPSHOT"

springBoot {
    // Allow overriding the main class via -Pspring.boot.main=... so the same
    // JAR can be launched either as the API server (default) or as a sync
    // job (SyncMain) from the Cloud Run Job container.
    mainClass.set(
        project.findProperty("spring.boot.main")?.toString() ?: "kr.laborcase.LaborcaseApiApplicationKt",
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}


dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation(platform("com.google.cloud:libraries-bom:26.49.0"))
    implementation("com.google.cloud:google-cloud-storage")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Testcontainers is not used yet because docker-java 3.4 cannot negotiate
    // with Docker Desktop 29 (the /info endpoint returns a Status 400
    // placeholder). Tests drive Postgres via scripts/dev-postgres.sh instead.
    // Track: docs/research/drf-schema-notes.md for follow-up.
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    testImplementation("com.google.cloud:google-cloud-nio")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    // Live tests hit the real 법제처 DRF endpoint and require OC_LAW.
    // Excluded from the default run so CI stays offline-safe.
    useJUnitPlatform {
        if (System.getenv("RUN_LIVE_TESTS") != "true") {
            excludeTags("live")
        }
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Flyway plugin config — parameters come from env vars so no secrets live in
// build scripts or gradle.properties. Typical invocation:
//   DATABASE_URL=... DATABASE_USER=... DATABASE_PASSWORD=... \
//     ./gradlew flywayMigrate
flyway {
    url = System.getenv("DATABASE_URL") ?: ""
    user = System.getenv("DATABASE_USER") ?: ""
    password = System.getenv("DATABASE_PASSWORD") ?: ""
    locations = arrayOf("classpath:db/migration")
}

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.1.1")
    }
}
