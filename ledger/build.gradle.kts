plugins {
    id("reconcile.module-conventions")
}

dependencies {
    "domainImplementation"(project(path = ":shared", configuration = "domainElements"))

    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
