$ErrorActionPreference='Stop'
$checkRoot=Split-Path $PSScriptRoot -Parent
$checkCache=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$checkJars=@()
foreach($checkModule in @('org.jetbrains.kotlin\kotlin-compiler-embeddable','org.jetbrains.kotlin\kotlin-stdlib','org.jetbrains.kotlin\kotlin-script-runtime','org.jetbrains.kotlin\kotlin-reflect','org.jetbrains.kotlin\kotlin-daemon-embeddable','org.jetbrains.intellij.deps\trove4j','org.jetbrains\annotations')){
  $checkJars+=Get-ChildItem (Join-Path $checkCache $checkModule) -Recurse -Filter '*.jar' | Select-Object -ExpandProperty FullName
}
$checkStdlib=Get-ChildItem (Join-Path $checkCache 'org.jetbrains.kotlin\kotlin-stdlib\1.9.24') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$checkOutput=Join-Path $checkRoot 'app\build\voice-policy-check'
New-Item -ItemType Directory -Path $checkOutput -Force | Out-Null
$checkJava=Join-Path $env:LOCALAPPDATA 'Programs\Temurin\jdk-17.0.20.1+1\bin\java.exe'
& $checkJava -cp ($checkJars -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $checkStdlib -d $checkOutput (Join-Path $checkRoot 'app\src\main\java\dev\stride\hud\VoiceTrainerPolicy.kt') (Join-Path $PSScriptRoot 'VoiceTrainerPolicyCheck.kt')
if($LASTEXITCODE -ne 0){throw 'Native policy check compilation failed'}
& $checkJava -cp ($checkOutput+';'+$checkStdlib) dev.stride.hud.VoiceTrainerPolicyCheckKt
if($LASTEXITCODE -ne 0){throw 'Native policy checks failed'}
