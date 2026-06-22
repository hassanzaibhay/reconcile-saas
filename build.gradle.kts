// Root build — plugin declarations + conventions are managed via buildSrc.
// Each module applies reconcile.java-conventions or reconcile.module-conventions.
// Spotless is applied per-module via reconcile.java-conventions.

// Verify the build works for all subprojects
tasks.register("checkAll") {
    dependsOn(subprojects.map { it.tasks.matching { t -> t.name == "check" } })
}
