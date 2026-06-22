plugins {
    id("reconcile.module-conventions")
}

// shared has no domainImplementation deps — it IS the shared domain root.
// main gets spring-context for @Component/@Configuration in adapters.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("jakarta.validation:jakarta.validation-api")

    // Redis containers use GenericContainer from the base testcontainers artifact
    testImplementation("org.testcontainers:testcontainers")
}
