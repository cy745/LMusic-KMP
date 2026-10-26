#!/usr/bin/env python3
"""Create a deterministic silent WAV for native playback control smoke tests."""
import pathlib
import sys
import wave

output = pathlib.Path(sys.argv[1])
output.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(output), 'wb') as audio:
    audio.setnchannels(2)
    audio.setsampwidth(2)
    audio.setframerate(48000)
    audio.writeframes(bytes(20 * 48000 * 4))
