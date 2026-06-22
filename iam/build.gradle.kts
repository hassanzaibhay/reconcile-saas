plugins {
    id("reconcile.module-conventions")
}

dependencies {
    "domainImplementation"(project(path = ":shared", configuration = "domainElements"))

    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
