subprojects {
    apply(plugin = "java")

    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<ProcessResources>().configureEach {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    // Reproducible jar: fixed entry order and timestamps, so rebuilding the same source gives the same SHA-256.
    tasks.withType<Jar>().configureEach {
        archiveBaseName.set("FishOnBobber")
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
