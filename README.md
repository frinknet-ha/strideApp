# STRIDE Compat — C1750 media and trainer fork

Source fork of [keranm/strideApp](https://github.com/keranm/strideApp), preserving its Git history, Apache-2.0 license and attribution. This fork adds console media and local coaching work for a NordicTrack C1750. It is independently maintained and is not an upstream release or a NordicTrack/iFit product.

**Fork version: 0.9.1-compat · Android version code 12.** Read [SAFETY.md](SAFETY.md) before connecting software to a treadmill. Starting a workout can move the belt; guided plans can move the incline.

## Added in this fork

- YouTube text search and embedded playback in a separate media WebView, with default treadmill scenery results and expansion after playback inactivity.
- A native control strip above the video for pause/resume, speed, incline, fan and STOP/end-workout controls.
- Bluetooth headphone discovery and pairing from Settings, with separate paired/connected audio status and Android connection settings as a fallback.
- Three local trainers: Supportive Coach, Pace Coach and an explicitly profane Drill Sergeant. Settings offer Off or a coach, plus tips every 3, 5 or 10 minutes.
- 108 coaching scripts. Timed tips alternate steady effort and motivation. Start, completed whole miles, upcoming planned hills, recovery and finish use workout events.
- Corrected miles display across the interfaces, plus board-specific idle/start interlock compatibility changes and read-only physical-key diagnostics.

The original project includes the treadmill protocol implementation, guided workouts, multiple interfaces, Home Assistant/MQTT integration and Bluetooth FTMS telemetry. See the [upstream documentation](https://github.com/keranm/strideApp/blob/main/README.md) for its background. This fork uses local coaching instead of the upstream remote live-coach audio subscription.

## Hardware and test status

| Component | Observed installation |
| --- | --- |
| Treadmill | NordicTrack C1750, model NTL14122K1F |
| Console | MalataMediatekArgon2, Android 9 |
| WebView | 83.0.4103.120 |
| App package | `dev.stride.hud.compat`, separate from upstream `dev.stride.hud` |

The private 0.9.1 build was installed on this console. YouTube playback, default search, idle expansion, settings layout, Home-app selection, miles display and in-app Bluetooth discovery have been observed across the console updates. Native trainer policy checks cover timing, distance boundaries, lifecycle and guided-plan events.

**Limits of that evidence:** headphone bonding completion/audio routing and trainer speech mixed with YouTube during a moving workout still need attended testing. The media control strip also needs a complete attended moving-workout check. Compatibility with other treadmill models or board revisions is not established. Board-specific interlock writes are not generic settings for arbitrary FitPro hardware.

There is **no PIN-enforced speed limit** or verified interception of physical speed keys in this fork. Read-only keypad diagnostics do not prevent a physical preset from accelerating the belt. Do not treat this app as a child-safety lock.

## Source-only publication and voice packs

This repository does not distribute a signed APK, private signing keys, broker credentials, household configuration, diagnostic screenshots or the generated speech recordings used on the private console.

The public source builds without a speech pack. Coaching then uses the same scripts as **text-only** messages and Settings identifies the missing audio pack. Supply recordings or TTS output you have rights to use if you want spoken coaching. See [voice-pack instructions](docs/VOICE_PACK.md). All three personalities can share one voice; their wording supplies the personality. Drill Sergeant stays silent until its explicit-language acknowledgement is enabled.

Speech uses an independent local MediaPlayer without requesting audio focus or controlling the YouTube player. That design is intended to allow overlapping playback; actual mixing depends on the Android/media implementation and still needs the attended test described above.

## Build

Requirements: Java 17, Android SDK platform 34/build-tools 34, platform-tools and Python 3 for optional checks/tools. `minSdk` and `targetSdk` are 28 for this console. This is an ADB-installed hobby project, not a Play Store app.

```sh
cd console/stride
# Set ANDROID_HOME to your Android SDK directory, or set sdk.dir in local.properties.
./gradlew assembleDebug
```

On Windows without a wrapper batch file:

```powershell
cd console/stride
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Debug output is `app/build/outputs/apk/debug/app-debug.apk`. Do not distribute a debug build as a production release. Release signing is optional for source compilation; `assembleRelease` produces an unsigned APK when no release identity is configured. Configure your own signing identity using the comments in `app/build.gradle.kts`. Never upload your keystore or `local.properties`.

Broker defaults are always empty in public builds, even when local signing/SDK properties exist. Configure MQTT using Settings → Home Assistant on your own console.

The Home alias is disabled by default in public builds. Selecting STRIDE as Home is a separate owner decision; the existing [unchain helper](tools/unchain.sh) documents how to enable/reverse it. An explicit build-time opt-in is `-PstrideHomeEnabled=true`.

## Checks

```sh
cd console/stride
./gradlew assembleRelease
python3 checks/check-voice-policy.py
```

The policy check compiles the actual Android-independent Kotlin policy using the Kotlin compiler already cached by Gradle. It requires Java 17 and a preceding Gradle build. It issues no treadmill commands. The public source build and these checks passed locally before publication. The inherited upstream tag-release workflow has no signing secrets configured for this fork; no binary release or tag is published here.

## Installation and rollback

Use the upstream installation/protocol documentation only after checking your exact hardware and understanding its recovery procedure. Keep a known-good original launcher and an appropriate recovery path available. Never remotely start the belt to test an installation.

The compatibility package can coexist with upstream STRIDE. A different signing key cannot update an already installed package of the same name: keep your signing identity consistent and do not uninstall an existing app just to get past a signature error. The source-only public fork is not a drop-in signed update for the private console installation.

To leave a Home-app setup, restore another launcher/default Home before removing STRIDE; see [tools/unchain.sh](tools/unchain.sh). For an in-place rollback, use a known-good APK signed with the same identity and account for Android version-code/downgrade restrictions. App data/profile compatibility must be checked separately. Avoid factory-resetting a console without its device-specific recovery instructions.

## Contributions and attribution

Please include the exact model, console/Android version, board revision if known, and whether a report concerns stationary UI behavior or a supervised workout. Keep credentials, health/profile data and identifiable diagnostic captures out of public issues. See [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), [LICENSE](LICENSE) and [NOTICE](NOTICE).

Upstream STRIDE is copyright 2026 The Idea Works. Fork modifications cover compatibility, units, isolated media UI, headphone setup and local coaching. Upstream has not reviewed or endorsed these additions. No vendor software is included.
