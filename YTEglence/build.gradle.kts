version = 1

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

cloudstream {

    authors = listOf("GitLatte", "patr0nq")
    language    = "tr"
    description = "Youtube üzerindeki kullanıcı tercihleriyle eklenen eğlence kanallarının içeriklerini izleyebilirsiniz."

    status = 1

    tvTypes = listOf("Others", "Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=youtube.com&sz=%size%"
}