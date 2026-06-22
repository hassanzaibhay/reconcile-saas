plugins {
    id("reconcile.module-conventions")
}

// shared has no domainImplementation deps — it IS the shared domain root.
// main gets spring-context for @Component/@Configuration in adapters.
dependencies {
    implementation("org.springframework:spring-context")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.validation:jakarta.validation-api")

    // Testcontainers Redis for integration tests
    testImplementation("org.testcontainers:redis")
}
