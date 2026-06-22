plugins {
    id("reconcile.spring-conventions")
}

// ---------------------------------------------------------------------------
// Domain source set — pure Java 21, zero Spring/JPA on classpath.
// Compile-time enforcement: a Spring/JPA import in src/domain/java fails here.
// ---------------------------------------------------------------------------
val domain: SourceSet by sourceSets.creating {
    java {
        srcDir("src/domain/java")
    }
    resources {
        srcDir("src/domain/resources")
    }
}

// domainImplementation is auto-created for the domain source set and does NOT
// extend from 'implementation', so Spring/JPA cannot leak in via transitivity.
// Subprojects add ":shared" domainElements here to share the domain root types.

// Package domain classes into a dedicated JAR that consumers can depend on
// without pulling in any Spring dependencies.
val domainJar = tasks.register<Jar>("domainJar") {
    from(domain.output)
    archiveClassifier = "domain"
    dependsOn(tasks.named(domain.classesTaskName))
}

// Expose the domain JAR as a consumable variant ("domainElements").
// Consumers declare: project(path = ":xyz", configuration = "domainElements")
configurations.create("domainElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(domainJar)
}

// Wire domain output into main and test so they can import domain types.
sourceSets.main {
    compileClasspath += domain.output
    runtimeClasspath += domain.output
}
sourceSets.test {
    compileClasspath += domain.output
    runtimeClasspath += domain.output
}
