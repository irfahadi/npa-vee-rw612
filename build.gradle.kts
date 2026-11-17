allprojects {
    group = "com.nxp.vee.frdmrw612"
    version = "1.0.0"

    // Disambiguate newer architecture metadata (e.g. 8.2.0+) when using microej-sdk 1.0.0.
    val archUsage = if (System.getProperty("com.microej.architecture.usage") == "prod") {
        "microej-production-architecture"
    } else {
        "microej-evaluation-architecture"
    }
    configurations.matching { it.name.startsWith("microej") && it.isCanBeResolved }.configureEach {
        attributes {
            attribute(
                Attribute.of("com.microej.architecture.usage", String::class.java),
                archUsage
            )
        }
    }
}
