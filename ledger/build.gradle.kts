plugins {
    id("reconcile.module-conventions")
}

dependencies {
    "domainImplementation"(project(path = ":shared", configuration = "domainElements"))

    compileOnly("org.springframework.modulith:spring-modulith-api")
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
