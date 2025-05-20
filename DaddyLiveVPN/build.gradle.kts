version = 1

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    authors     = listOf("GitLatte", "patr0nq")
    language    = "tr"
    description = "DaddyLive Spor kanallarını VPN ile izleyebilmek için"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/GitLatte/temporarylists/refs/heads/main/img/daddylive-vpn.png"
}