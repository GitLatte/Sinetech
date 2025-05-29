/* For using manual plugins list to add/remove builds
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
    "KickTR"
)
*/

rootProject.name = "CloudstreamPlugins"

// disabled değişkenine eklenmediği sürece tüm eklentiler projeye dahil edilir

val disabled = listOf<String>("") // geçersiz bırakılacak eklentiler buraya

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) { // eklenti dizini içerisinde build.gradle.kts dosyası varsa eklenti projeye dahil edili
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}