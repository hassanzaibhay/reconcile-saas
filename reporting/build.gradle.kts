plugins {
    id("reconcile.module-conventions")
}

dependencies {
    "domainImplementation"(project(path = ":shared", configuration = "domainElements"))

    implementation(project(":shared"))
    implementation(project(":reconciliation"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
}
