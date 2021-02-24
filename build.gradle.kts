plugins {
    kotlin("jvm") version "1.4.30"
    id("org.jetbrains.dokka") version "1.4.20"
}

group = "com.hadiyarajesh"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://jitpack.io")
}

sourceSets {
    main {
        java.srcDir("src/main/kotlin")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
    implementation("com.github.jkcclemens:khttp:-SNAPSHOT")
    implementation("com.nfeld.jsonpathlite:json-path-lite:1.1.0")
    implementation("org.json:json:20180813")
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            moduleName.set("instagram-api")
            includeNonPublic.set(false)

            perPackageOption {
                matchingRegex.set("api($|\\.).*") // will match all api packages and sub-packages
                suppress.set(true)
            }
            perPackageOption {
                matchingRegex.set("util($|\\.).*") // will match all util packages and sub-packages
                suppress.set(true)
            }
            perPackageOption {
                matchingRegex.set("samples($|\\.).*") // will match all samples packages and sub-packages
                suppress.set(true)
            }
            perPackageOption {
                matchingRegex.set("com.nfeld.jsonpathlite($|\\.).*") // will match all com.nfeld.jsonpathlite packages and sub-packages
                suppress.set(true)
            }
        }
    }
}