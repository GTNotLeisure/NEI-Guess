import com.diffplug.spotless.LineEnding
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

spotless {
    lineEndings = LineEnding.UNIX
}

minecraft {
    extraRunJvmArguments.addAll("-Xmx8G", "-Xms8G", "-Dgtnhlib.dumpkeys=true")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    minimize {
        exclude(dependency("dev.ghostflyby:PinIn:.*"))
        exclude(dependency("org.luaj:luaj-jse:.*"))
    }
}

val runConfigs = listOf(
    "runClient" to "run/client",
    "runClient17" to "run/client_new",
    "runClient21" to "run/client_new",
    "runClient25" to "run/client_new",
    "runServer" to "run/server",
    "runServer17" to "run/server_new",
    "runServer21" to "run/server_new",
    "runServer25" to "run/server_new"
)

runConfigs.forEach { (taskName, path) ->
    tasks.named<JavaExec>(taskName) {
        workingDir = file("${projectDir}/$path")
        systemProperty("fml.coreMods.load", "com.hfstudio.neiguess.core.NEIGuessCoreMod")
        doFirst {
            workingDir.mkdirs()
        }
    }
}
