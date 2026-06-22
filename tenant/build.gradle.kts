plugins {
    id("reconcile.module-conventions")
}

dependencies {
    // domain source set: only shared's domain output
    "domainImplementation"(project(path = ":shared", configuration = "domainElements"))

    // main: full shared + Spring starters
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
}
