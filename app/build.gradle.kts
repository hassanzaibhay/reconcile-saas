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

    testImplementation("org.testcontainers:redis")
    testImplementation("org.springframework.security:spring-security-test")
}

springBoot {
    mainClass = "com.reconcile.app.Application"
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    layered {
        enabled = true
    }
}
