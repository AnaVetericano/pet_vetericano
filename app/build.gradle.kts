plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.petvetericano"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
        packaging {
            resources {
                excludes += "META-INF/NOTICE.md"
                excludes += "META-INF/LICENSE.md"
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.petvetericano"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val googleWebClientId = (project.findProperty("VETERICANO_GOOGLE_WEB_CLIENT_ID") as? String)
            ?.trim()
            ?.replace("\"", "")
            .orEmpty()
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    buildTypes {
        val productionApiUrl = "https://backendvetericano-production.up.railway.app/api/"
        debug {
            // Por defecto apunta a Railway; para Django local usar -PVETERICANO_DEV_API_URL=http://127.0.0.1:8000/api/ (con `adb reverse tcp:8000 tcp:8000`) o http://10.0.2.2:8000/api/
            val devApiUrl = (project.findProperty("VETERICANO_DEV_API_URL") as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { if (it.endsWith("/")) it else "$it/" }
                ?: productionApiUrl
            buildConfigField("String", "API_BASE_URL", "\"$devApiUrl\"")

            // WebSocket: debe ir al MISMO backend que la API (misma SECRET_KEY y misma BD); si no, el ws
            // rechaza el JWT con 4001. Sin -PVETERICANO_DEV_WS_URL se deriva de la API:
            //  - API de Railway  -> wss://vetericano-ws-production.up.railway.app/ws/notificaciones/
            //  - API local http://<host>:8000/api/ -> ws://<host>:8002/ws/notificaciones/
            //    (emulador: 10.0.2.2; dispositivo físico: 127.0.0.1 con `adb reverse tcp:8000 tcp:8000` y `tcp:8002 tcp:8002`)
            val productionWsUrl = "wss://vetericano-ws-production.up.railway.app/ws/notificaciones/"
            val apiEsRailway = devApiUrl.contains(".up.railway.app", ignoreCase = true)
            val devWsUrl = ((project.findProperty("VETERICANO_DEV_WS_URL") as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: if (apiEsRailway) productionWsUrl else {
                    val apiHost = Regex("^[a-zA-Z]+://([^/:]+)").find(devApiUrl)?.groupValues?.get(1) ?: "10.0.2.2"
                    "ws://$apiHost:8002/ws/notificaciones/"
                })
                .let { if (it.endsWith("/")) it else "$it/" }
            val devWsHabilitado = (project.findProperty("VETERICANO_WS_HABILITADO") as? String)
                ?.trim()
                ?.equals("true", ignoreCase = true)
                ?: false
            if (devWsHabilitado && apiEsRailway != devWsUrl.contains(".up.railway.app", ignoreCase = true)) {
                throw GradleException(
                    "VETERICANO: API ($devApiUrl) y WebSocket ($devWsUrl) apuntan a entornos distintos. " +
                        "El ws rechazaría el token (4001). Usa -PVETERICANO_DEV_API_URL y -PVETERICANO_DEV_WS_URL del mismo backend."
                )
            }
            val wsClientId = (project.findProperty("VETERICANO_WS_CLIENT_ID") as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "android-petvetericano"
            buildConfigField("String", "WS_URL", "\"$devWsUrl\"")
            buildConfigField("boolean", "WS_HABILITADO", devWsHabilitado.toString())
            buildConfigField("String", "WS_CLIENT_ID", "\"$wsClientId\"")
        }
        release {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$productionApiUrl\""
            )
            // En release únicamente se permite wss://; apagado por defecto hasta existir dominio WS validado.
            val releaseWsUrl = (project.findProperty("VETERICANO_RELEASE_WS_URL") as? String)
                ?.trim()
                ?.takeIf { it.startsWith("wss://", ignoreCase = true) }
                ?.let { if (it.endsWith("/")) it else "$it/" }
                ?: ""
            val releaseWsHabilitado = releaseWsUrl.isNotEmpty() && (
                (project.findProperty("VETERICANO_RELEASE_WS_HABILITADO") as? String)
                    ?.trim()
                    ?.equals("true", ignoreCase = true)
                    ?: false
            )
            val wsClientId = (project.findProperty("VETERICANO_WS_CLIENT_ID") as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "android-petvetericano"
            buildConfigField("String", "WS_URL", "\"$releaseWsUrl\"")
            buildConfigField("boolean", "WS_HABILITADO", releaseWsHabilitado.toString())
            buildConfigField("String", "WS_CLIENT_ID", "\"$wsClientId\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.googleid)
    implementation(libs.material)
    implementation(libs.androidx.work.runtime.ktx)
    // Notificaciones push (contrato v1 §7) y detección de primer plano para la recuperación REST
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    //dependencias de google maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Glide para carga de imágenes y miniaturas
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
// retrofit y Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

// para ver las peticiones en Logcat
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// para llamadas asíncronas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    //depen de los graficos

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Reconocimiento facial on-device (CameraX + ML Kit Face Detection + LiteRT / MobileFaceNet)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)
    implementation(libs.litert)

    //cloudinary
    implementation("com.cloudinary:cloudinary-android:2.5.0")

    //para que aparesca una petciion al instante
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // ViewPager2 para el carrusel de eventos
    implementation("androidx.viewpager2:viewpager2:1.1.0")


}