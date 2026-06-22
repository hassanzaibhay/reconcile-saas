plugins {
    id("reconcile.java-conventions")
    id("io.spring.dependency-management")
    // spring.boot is NOT applied here — only :app applies it explicitly
}

// Spring Boot BOM + Spring Modulith BOM
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.15")
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.4.12")
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

// Universal test dependencies (all modules get these)
dependencies {
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testImplementation"("org.testcontainers:junit-jupiter")
    "testImplementation"("org.testcontainers:postgresql")
    // Gradle 9+ requires this explicitly for JUnit Platform test executor
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
