import fr.xpdustry.toxopid.extension.MindustryRepository
import fr.xpdustry.toxopid.extension.ModDependency
import fr.xpdustry.toxopid.extension.ModTarget
import fr.xpdustry.toxopid.util.ModMetadata
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import java.io.ByteArrayOutputStream

plugins {
    java
    `maven-publish`
    id("net.ltgt.errorprone") version "2.0.2"
    id("fr.xpdustry.toxopid") version "1.3.2"
    id("com.github.ben-manes.versions") version "0.42.0"
    id("net.kyori.indra") version "2.1.1"
    id("net.kyori.indra.publishing") version "2.1.1"
}

val metadata = ModMetadata(file("${rootProject.rootDir}/plugin.json"))
group = property("props.project-group").toString()
version = metadata.version + if (indraGit.headTag() == null) "-SNAPSHOT" else ""

toxopid {
    val compileVersion = "v135" // Need v135 to access WorldLabel
    modTarget.set(ModTarget.HEADLESS)
    arcCompileVersion.set(compileVersion)
    mindustryCompileVersion.set(compileVersion)

    mindustryRepository.set(MindustryRepository.BE)
    mindustryRuntimeVersion.set("22349")

    modDependencies.set(listOf(
        ModDependency("Xpdustry/Distributor", "v2.6.1", "distributor-core.jar")
    ))
}

repositories {
    mavenCentral()
    maven("https://repo.xpdustry.fr/releases") {
        name = "xpdustry-releases-repository"
        mavenContent { releasesOnly() }
    }
}

dependencies {
    // Distributor goes brrrrr
    compileOnly("fr.xpdustry:distributor-core:2.6.1")

    val junit = "5.8.2"
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit")

    val jetbrains = "23.0.0"
    compileOnly("org.jetbrains:annotations:$jetbrains")
    testCompileOnly("org.jetbrains:annotations:$jetbrains")

    // Static analysis
    annotationProcessor("com.uber.nullaway:nullaway:0.9.5")
    errorprone("com.google.errorprone:error_prone_core:2.11.0")
}

tasks.withType(JavaCompile::class.java).configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        disable("MissingSummary")
        if (!name.contains("test", true)) {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", project.property("props.root-package").toString())
        }
    }
}

// Required if you want to use the Release GitHub action
tasks.create("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.create("createRelease") {
    dependsOn("requireClean")

    doLast {
        // Checks if a signing key is present
        val signing = ByteArrayOutputStream().use { out ->
            exec {
                commandLine("git", "config", "--global", "user.signingkey")
                standardOutput = out
            }.run { exitValue == 0 && out.toString().isNotBlank() }
        }

        exec {
            commandLine(arrayListOf("git", "tag", "v${metadata.version}", "-F", "./CHANGELOG.md", "-a").apply { if (signing) add("-s") })
        }

        exec {
            commandLine("git", "push", "origin", "--tags")
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishReleasesTo("xpdustry", "https://repo.xpdustry.fr/releases")
    publishSnapshotsTo("xpdustry", "https://repo.xpdustry.fr/snapshots")

    gpl3OnlyLicense()

    if (metadata.repo != null) {
        val repo = metadata.repo!!.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            developers {
                developer { id.set(metadata.author) }
            }
        }
    }
}
