/**
rootProject.name = "CloudstreamPlugins"

include(
    "powerDizi",
    "powerSinema",
    "TvBahcesi",
    "DaddyLive",
    "DaddyLiveEvents",
    "DaddyLiveVPN",
    "DDizi",
    "WebDramaTurkey",
    "YTEglence",
    "YTCanliTV",
    "TOGrup",
    "DiziFun",
    "AniworldMC",
    "mywayTV",
    "KickTR",
    "Filmhe"
)
*/    

rootProject.name = "CloudstreamPlugins"

val disabled = listOf("")

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}