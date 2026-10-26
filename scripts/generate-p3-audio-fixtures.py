"""Generate synthetic P3 codec fixtures (soundfile 0.13.1, numpy 2.0.2).

Run in a temporary virtual environment; these packages are not application dependencies.
All audio is generated here, not downloaded music. WAV runtime fixtures are generated in Kotlin.
"""
from pathlib import Path
import numpy as np
import soundfile as sf

root = Path(__file__).resolve().parents[1] / "androidApp/src/androidTest/assets/p3"
root.mkdir(parents=True, exist_ok=True)
sf.write(root / "constant-16k.flac", np.full(16000, 2000, dtype=np.int16), 16000, subtype="PCM_16")
tone = (np.sin(np.arange(16000) * 2 * np.pi * 440 / 16000) * 2000).astype(np.int16)
sf.write(root / "tone-16k.mp3", tone, 16000, format="MP3")
sf.write(root / "constant-44100-stereo.wav", np.full((44100, 2), 2000, dtype=np.int16), 44100, subtype="PCM_16")
for file in root.iterdir():
    samples, rate = sf.read(file, dtype="int16")
    assert len(samples) == rate, f"Expected exactly one second after codec trimming: {file}"
    print(file.name, rate, samples.shape)
