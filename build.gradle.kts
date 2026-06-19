import java.io.File

plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.instagram.analyze"
version = "0.0.1-SNAPSHOT"
description = "instagram-analyze"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── 프론트엔드 번들링 (Vite dist → Spring static, 단일 JAR 동봉) ──────────────
// 백엔드만 빠르게 빌드할 땐 `-PskipFrontend` 로 npm 단계를 건너뛴다.
val frontendDir = layout.projectDirectory.dir("frontend")
val isWin = System.getProperty("os.name").lowercase().contains("win")
// npm 경로: -Pnpm=... 우선, 없으면 표준 설치 위치의 절대경로(Gradle 데몬 PATH 가 축소돼도 동작).
// + npm 스크립트 내부의 node 조회를 위해 environment 로 PATH 도 보강.
val npmExecutable = listOfNotNull(
    providers.gradleProperty("npm").orNull,
    if (isWin) null else "/usr/local/bin/npm",
    if (isWin) null else "/opt/homebrew/bin/npm",
).firstOrNull { File(it).exists() } ?: if (isWin) "npm.cmd" else "npm"
val augmentedPath = listOf("/usr/local/bin", "/opt/homebrew/bin", File(System.getProperty("user.home"), ".nvm/current/bin").path)
    .joinToString(File.pathSeparator) + File.pathSeparator + (System.getenv("PATH") ?: "")
val skipFrontend = providers.gradleProperty("skipFrontend").isPresent

val frontendInstall by tasks.registering(Exec::class) {
    workingDir = frontendDir.asFile
    environment("PATH", augmentedPath)
    commandLine(npmExecutable, "ci")
    onlyIf { !skipFrontend && !frontendDir.dir("node_modules").asFile.exists() }
}

val frontendBuild by tasks.registering(Exec::class) {
    dependsOn(frontendInstall)
    workingDir = frontendDir.asFile
    environment("PATH", augmentedPath)
    commandLine(npmExecutable, "run", "build")
    onlyIf { !skipFrontend }
    // src/설정 미변경 시 Gradle up-to-date 캐시로 재빌드 생략
    inputs.dir(frontendDir.dir("src"))
    inputs.file(frontendDir.file("package.json"))
    inputs.file(frontendDir.file("package-lock.json"))
    inputs.file(frontendDir.file("vite.config.ts"))
    inputs.file(frontendDir.file("index.html"))
    outputs.dir(frontendDir.dir("dist"))
}

tasks.named<ProcessResources>("processResources") {
    if (!skipFrontend) {
        dependsOn(frontendBuild)
        from(frontendDir.dir("dist")) { into("static") }
    }
}

// plain jar 비활성 → bootJar(팻 JAR)만 산출(패키징 입력 단순화)
tasks.named<Jar>("jar") { enabled = false }

// ── jpackage 네이티브 설치본 (JRE 동봉, Java 설치 불필요) ─────────────────────
// 실행: `./gradlew jpackage` → 빌드한 OS 용 설치본(mac=.dmg / win=.exe) 생성.
val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val jpackageDist = layout.buildDirectory.dir("jpackage/dist")

val stageJpackageJar by tasks.registering(Copy::class) {
    dependsOn("bootJar")
    from(layout.buildDirectory.dir("libs")) { include("instagram-analyze-${project.version}.jar") }
    into(jpackageInput)
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "JRE 동봉 네이티브 설치본 생성(jpackage)"
    dependsOn(stageJpackageJar)

    val osName = System.getProperty("os.name").lowercase()
    val isWindows = osName.contains("win")
    val type = when {
        osName.contains("mac") -> "dmg"
        isWindows -> "exe"
        else -> "app-image"
    }
    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val binName = if (isWindows) "bin/jpackage.exe" else "bin/jpackage"
    val jpackageTool = launcher.get().metadata.installationPath.file(binName).asFile.absolutePath

    doFirst {
        delete(jpackageDist)
        jpackageDist.get().asFile.mkdirs()
    }

    val winArgs = if (isWindows) listOf("--win-shortcut", "--win-menu", "--win-dir-chooser") else emptyList()
    commandLine(
        listOf(
            jpackageTool,
            "--type", type,
            "--name", "Instagram Analyzer",
            "--app-version", "1.0.0",
            "--input", jpackageInput.get().asFile.absolutePath,
            "--main-jar", "instagram-analyze-${project.version}.jar",
            "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
            "--java-options", "-Dspring.profiles.active=desktop",
            "--java-options", "-Dfile.encoding=UTF-8",
            "--add-modules", "ALL-MODULE-PATH",
            "--vendor", "InstagramAnalyzer",
            "--dest", jpackageDist.get().asFile.absolutePath,
        ) + winArgs,
    )
}

// ── 동봉 런타임 (jlink) — Java 설치 없이 실행 가능하게 deploy/ 에 넣을 JRE ─────────
// 실행: `./gradlew bundleRuntime`                         (이 머신 OS 용 런타임)
//      `./gradlew bundleRuntime -PruntimeJdk=/경로/windows-jdk-21`   (크로스 타깃)
// jlink 는 타깃 OS 의 jmods 를 읽어 그 OS 용 런타임 이미지를 만든다(호스트 OS 와 달라도 됨).
// → 이 Mac 한 대에서 win/linux 용 런타임까지 생성 가능. 단 타깃 JDK 는 호스트와 같은 major(21).
val runtimeImageDir = layout.buildDirectory.dir("runtime")
// Spring Boot 는 클래스패스 실행이라 java.se + 흔히 reflection 으로 쓰는 jdk.* 만 담아 경량화.
val runtimeModules = listOf(
    "java.se", "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki",
    "jdk.zipfs", "jdk.charsets", "jdk.localedata", "jdk.management",
    "jdk.net", "jdk.httpserver", "jdk.naming.dns",
).joinToString(",")

tasks.register<Exec>("bundleRuntime") {
    group = "distribution"
    description = "jlink 로 동봉 런타임 생성(-PruntimeJdk=<타깃 JDK 홈> 으로 크로스 타깃)"

    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val isWinHost = System.getProperty("os.name").lowercase().contains("win")
    val jlinkBin = if (isWinHost) "bin/jlink.exe" else "bin/jlink"
    val jlinkTool = launcher.get().metadata.installationPath.file(jlinkBin).asFile.absolutePath
    // jmods 소스: -PruntimeJdk 우선(크로스), 없으면 호스트 toolchain JDK
    val hostJdkHome = launcher.get().metadata.installationPath.asFile.absolutePath
    val targetJdkHome = providers.gradleProperty("runtimeJdk").orNull ?: hostJdkHome
    val jmods = File(targetJdkHome, "jmods").absolutePath

    doFirst {
        if (!File(jmods).isDirectory) {
            throw GradleException("jmods 없음: $jmods — -PruntimeJdk 에 타깃 JDK(jmods 포함) 홈을 지정하세요")
        }
        delete(runtimeImageDir)   // jlink 는 출력 폴더가 이미 있으면 실패
    }
    commandLine(
        jlinkTool,
        "--module-path", jmods,
        "--add-modules", runtimeModules,
        "--strip-debug", "--no-header-files", "--no-man-pages",
        "--compress", "zip-6",
        "--output", runtimeImageDir.get().asFile.absolutePath,
    )
}

// ── deploy/ 조립 (jar + data/ + 더블클릭 런처, 선택적으로 런타임 동봉) ──────────────
// 실행:
//   `./gradlew assembleDeploy`                                  jar + 런처 (Java 설치 전제)
//   `./gradlew assembleDeploy -PwithRuntime`                    + 이 OS 용 동봉 런타임(Java 불필요)
//   `./gradlew assembleDeploy -PwithRuntime -PruntimeJdk=/win-jdk -PdeployDir=deploy-win`
//                                                               크로스: Mac 에서 Windows 동봉본 생성
// 타깃은 -Ptarget=mac|win|linux (기본=빌드 머신 OS). GUI 런처(콘솔 없이 더블클릭)를 타깃별로 동봉:
//   win 타깃   → "Instagram Analyzer.vbs" (javaw 숨김 실행, 콘솔 0) + start.bat(진단용)
//   mac 타깃   → "Instagram Analyzer.app" (Terminal 0) + start.command(진단용)
//   linux 타깃 → start.sh
// 브라우저 자동열기·트레이 종료는 desktop 프로파일(DesktopLauncher)이 담당.
//   deploy[-os]/
//   ├── instagram-analyze.jar
//   ├── runtime/                       (-PwithRuntime 일 때만)
//   ├── data/
//   ├── (win) Instagram Analyzer.vbs / (mac) Instagram Analyzer.app / (linux) start.sh
//   ├── start.bat 또는 start.command   (문제 진단용 — 콘솔에 로그 표시)
//   └── README.txt
val withRuntime = providers.gradleProperty("withRuntime").isPresent
val deployDirName = providers.gradleProperty("deployDir").orNull ?: "deploy"
val deployDir = layout.projectDirectory.dir(deployDirName)
val deployTarget = providers.gradleProperty("target").orNull ?: when {
    System.getProperty("os.name").lowercase().contains("win") -> "win"
    System.getProperty("os.name").lowercase().contains("mac") -> "mac"
    else -> "linux"
}

tasks.register<Copy>("assembleDeploy") {
    group = "distribution"
    description = "deploy/ 묶음 생성(타깃별 GUI 런처 + jar + data/; -PwithRuntime 로 런타임 동봉)"
    dependsOn("bootJar")
    if (withRuntime) dependsOn("bundleRuntime")

    doFirst { delete(deployDir) }

    // 1) 팻 JAR → 고정 이름으로
    from(layout.buildDirectory.dir("libs")) {
        include("instagram-analyze-${project.version}.jar")
        rename { "instagram-analyze.jar" }
    }
    // 2) 공통 안내문
    from("packaging/launchers") { include("README.txt") }
    // 3) 타깃별 런처
    when (deployTarget) {
        "win" -> from("packaging/launchers") { include("Instagram Analyzer.vbs", "start.bat") }
        "mac" -> {
            from("packaging/launchers") {
                include("start.command")
                filePermissions { unix("755") }
            }
            // .app 번들(디렉토리 트리) — 내부 run 실행권한은 doLast 에서 부여
            // packaging/macapp/Contents/** → Instagram Analyzer.app/Contents/**
            from("packaging/macapp") { into("Instagram Analyzer.app") }
        }
        else -> from("packaging/launchers") {
            include("start.sh")
            filePermissions { unix("755") }
        }
    }
    // 4) 동봉 런타임(선택)
    if (withRuntime) {
        from(runtimeImageDir) { into("runtime") }
    }
    into(deployDir)

    // 5) data/ 보장 + 실행권한 복원(Copy 가 unix 권한을 떨굴 수 있음)
    doLast {
        val dataKeep = deployDir.file("data/.gitkeep").asFile
        dataKeep.parentFile.mkdirs()
        dataKeep.writeText("")

        // .app 실행 파일
        val appRun = deployDir.file("Instagram Analyzer.app/Contents/MacOS/run").asFile
        if (appRun.exists()) appRun.setExecutable(true, false)

        // 동봉 런타임 실행권한
        val binDir = deployDir.dir("runtime/bin").asFile
        if (binDir.isDirectory) {
            binDir.listFiles()?.forEach { it.setExecutable(true, false) }
            val jspawn = deployDir.file("runtime/lib/jspawnhelper").asFile
            if (jspawn.exists()) jspawn.setExecutable(true, false)
        }
    }
}
