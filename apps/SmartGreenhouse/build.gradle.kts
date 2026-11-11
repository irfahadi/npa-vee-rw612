plugins {
    id("com.microej.gradle.application") version libs.versions.microej.sdk
}

microej {
    applicationEntryPoint = "com.nxp.example.smartgreenhouse.Main"
    architectureUsage = System.getProperty("com.microej.architecture.usage") ?: "eval" // or "prod"
    skippedCheckers = "changelog,readme,license,nullanalysis"
    produceExecutableDuringBuild()
}

dependencies {
    implementation(libs.api.edc)
    implementation(libs.api.microui)
    implementation(libs.api.drawing)
    implementation(libs.api.device)
    implementation(libs.api.ecom.wifi)
    implementation("ej.library.iot:micropaho:1.0.0")
    implementation("ej.library.iot:sntpclient:1.4.0")
    implementation(libs.api.net)
    implementation("ej.library.iot:hoka:8.4.0")
    implementation(libs.api.kf)

    implementation(libs.library.mwt)
    implementation(libs.library.widget)
    implementation(libs.library.basictool)
    implementation(libs.library.logging)

    microejVee(project(":vee-port"))
}

