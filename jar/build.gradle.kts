import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar

plugins {
    id("com.gradleup.shadow")
}

val platformPaths = setOf(
    ":bukkit",
    ":bukkit:v26_1"
)

val moddedPaths = setOf<String>(
//    ":fabric",
//    ":neoforge"
//    ":forge"
)

val platforms: List<Project> = platformPaths.map { rootProject.project(it) }
val moddedPlatforms: List<Project> = moddedPaths.map { rootProject.project(it) }

tasks {
    shadowJar {
        archiveFileName.set("ChorusTAB v${project.version}.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        fun registerPlatform(project: Project, jarTask: AbstractArchiveTask) {
            dependsOn(jarTask)
            dependsOn(project.tasks.withType<Jar>())
            from(zipTree(jarTask.archiveFile))
        }

        platforms.forEach { p ->
            val task = p.tasks.named<ShadowJar>("shadowJar").get()
            registerPlatform(p, task)
        }

        moddedPlatforms.forEach { p ->
            val task = p.tasks.named<Jar>("jar").get()
            registerPlatform(p, task)
        }
    }

    val shadowJarVanilla = register<ShadowJar>("shadowJarVanilla") {
        description = "Shadows only vanilla platforms, without any modded platforms that require Java 25+."
        archiveFileName.set("ChorusTAB v${project.version} - Vanilla.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        fun registerPlatform(project: Project, jarTask: AbstractArchiveTask) {
            dependsOn(jarTask)
            dependsOn(project.tasks.withType<Jar>())
            from(zipTree(jarTask.archiveFile))
        }

        platforms.forEach { p ->
            val task = p.tasks.named<ShadowJar>("shadowJar").get()
            registerPlatform(p, task)
        }
    }

    build.get().dependsOn(shadowJar, shadowJarVanilla)
}
