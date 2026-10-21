# Local trainer speech packs

The 108 original coaching scripts are in `console/stride/app/src/main/assets/coach/library.json`. There are 36 lines per coach: eight steady-effort tips, eight motivation lines and four each for start, upcoming hill, completed mile, recovery and finish.

The private console used one neural voice for all three personalities. Those generated recordings are not published with this source fork because their public redistribution rights have not been established. This is not a claim that a third-party speech library's code license licenses its service's output. See Microsoft's [discussion of Edge Read Aloud redistribution](https://learn.microsoft.com/en-us/answers/questions/6007729/commercial-use-of-microsoft-edge-text-to-speech-vi). The scripts and app source remain available under this project's license.

## Prepare recordings

Record the scripts yourself or use a speech provider/output license appropriate for your intended use and distribution. Keep the same voice and delivery settings across personalities if desired. Do not imitate a person's voice without appropriate permission. Drill Sergeant scripts contain explicit profanity and personal roasting.

For each personality and event, name the WAV after its zero-based line index:

```text
supportive-start-0.wav
supportive-start-1.wav
...
pace-steady-0.wav
...
drill-finish-3.wav
```

The importer reads the JSON rather than a hardcoded filename list. Recommended format: PCM WAV, mono, 24 kHz. For example, with an existing recording and FFmpeg installed:

```sh
ffmpeg -i input.mp3 -ar 24000 -ac 1 -c:a pcm_s16le supportive-start-0.wav
```

Do not rename a different sentence to satisfy a filename: the app displays the script corresponding to that file. Preview and verify each recording before installing it on a console.

## Import and rebuild

From the repository root:

```sh
python3 tools/import-coach-audio.py /path/to/your/complete-pack
cd console/stride
./gradlew assembleDebug
```

The importer checks that all expected files are nonempty readable WAVs before copying any. Audio stays ignored by Git. Android packages WAVs uncompressed so MediaPlayer can open an asset descriptor. The entire set is local after build; speech playback needs neither an online synthesis service nor an Android TTS engine.

Without recordings the app still builds and presents coaching as text. Do not label a source-only build as including spoken coaching. A public audio-pack distribution must supply its own provenance/license and be reviewed separately.
