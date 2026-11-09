plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Keep the newest installable APK in the repo root (BlissPlayer-style) so it
// can be attached to a release; older versioned APKs in the root are pruned.
tasks.register("collectRootApk") {
    dependsOn("assembleDebug")
    doLast {
        val outDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        @Suppress("unchecked_cast")
        val meta = groovy.json.JsonSlurper().parse(outDir.resolve("output-metadata.json")) as Map<*, *>
        @Suppress("unchecked_cast")
        val element = (meta["elements"] as List<*>).first() as Map<*, *>
        val version = element["versionName"] as String
        val built = outDir.resolve(element["outputFile"] as String)
        val name = "${rootDir.name.replace(' ', '-')}-v$version-debug.apk"
        val keep = rootDir.resolve(name)
        if (built.canonicalPath != keep.canonicalPath) built.copyTo(keep, overwrite = true)
        rootDir.listFiles { file: java.io.File -> file.isFile && file.name.startsWith("Wonderdesk-") && file.name.endsWith("-debug.apk") && file.name != name }?.forEach { it.delete() }
    }
}
afterEvaluate {
    tasks.named("assembleDebug") { finalizedBy("collectRootApk") }
}
