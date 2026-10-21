"""Validate and import a complete, appropriately licensed local voice pack."""
import argparse
import json
import pathlib
import shutil
import wave

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'console/stride/app/src/main/assets/coach'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=pathlib.Path, help='Directory of WAVs you have rights to use')
    args = parser.parse_args()
    library = json.loads((ASSETS / 'library.json').read_text(encoding='utf-8'))
    expected = [f"{person['id']}-{event}-{index}.wav"
                for person in library['personalities']
                for event, lines in person['lines'].items()
                for index in range(len(lines))]
    # Validate the entire set before changing any existing local pack.
    for filename in expected:
        with wave.open(str(args.source / filename), 'rb') as audio:
            if audio.getnframes() == 0 or audio.getnchannels() not in (1, 2):
                raise ValueError(f'Invalid audio: {filename}')
    for filename in expected:
        if (args.source / filename).resolve() != (ASSETS / filename).resolve():
            shutil.copyfile(args.source / filename, ASSETS / filename)
    print(f'Imported {len(expected)} local clips. Audio remains ignored by Git.')

if __name__ == '__main__':
    main()
