"""Compile/run the real, Android-independent voice policy after a Gradle build."""
import os
import pathlib
import shutil
import subprocess

PROJECT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path(os.environ.get('GRADLE_USER_HOME', pathlib.Path.home()/'.gradle'))/'caches/modules-2/files-2.1'
MODULES = ['org.jetbrains.kotlin/kotlin-compiler-embeddable', 'org.jetbrains.kotlin/kotlin-stdlib',
           'org.jetbrains.kotlin/kotlin-script-runtime', 'org.jetbrains.kotlin/kotlin-reflect',
           'org.jetbrains.kotlin/kotlin-daemon-embeddable', 'org.jetbrains.intellij.deps/trove4j',
           'org.jetbrains/annotations']

def main():
    java = str(pathlib.Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')) if os.environ.get('JAVA_HOME') else shutil.which('java')
    if not java:
        raise SystemExit('Set JAVA_HOME to Java 17 before running this check.')
    jars = [str(p) for module in MODULES for p in (CACHE/module).rglob('*.jar')]
    stdlib = next((CACHE/'org.jetbrains.kotlin/kotlin-stdlib/1.9.24').rglob('*.jar'), None)
    if not stdlib:
        raise SystemExit('Run a Gradle build first to cache Kotlin 1.9.24.')
    output = PROJECT/'app/build/voice-policy-check'
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run([java,'-cp',os.pathsep.join(jars),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib','-no-reflect','-jvm-target','17','-classpath',str(stdlib),'-d',str(output),
                    str(PROJECT/'app/src/main/java/dev/stride/hud/VoiceTrainerPolicy.kt'),
                    str(PROJECT/'checks/VoiceTrainerPolicyCheck.kt')],check=True)
    subprocess.run([java,'-cp',str(output)+os.pathsep+str(stdlib),'dev.stride.hud.VoiceTrainerPolicyCheckKt'],check=True)

if __name__ == '__main__': main()
