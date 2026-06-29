plugins {
    id("reconcile.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":tenant"))
    implementation(project(":iam"))
    implementation(project(":ingestion"))
    implementation(project(":ledger"))
    implementation(project(":reconciliation"))
    implementation(project(":reporting"))
    implementation(project(":notification"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-batch")
}

val integrationTest by sourceSets.creating {
    java { srcDir("src/integrationTest/java") }
    resources { srcDir("src/integrationTest/resources") }
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (requires Docker/Testcontainers)"
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    maxHeapSize = "1g"
    jvmArgs("-XX:+UseG1GC")
}

springBoot {
    mainClass = "com.reconcile.Application"
}

// Spring Boot disables the plain jar task; re-enable so modulith-verification can depend on :app
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier = "plain"
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    layered {
        enabled = true
    }
}
