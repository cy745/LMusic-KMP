# TTML regression fixture

`missing-container-times.ttml` is the unmodified UTF-8 `LYRICS` Vorbis comment
extracted from the user's Xiaomi device on 2026-09-14:
`/sdcard/Download/QQ/先说谎的人 (Live版)-汪苏泷_h3R3.flac`.

It has 97 paragraphs, 971 timed word spans and 5 translation spans. The `body`
has no `dur`, and the `div` has neither `begin` nor `end`; paragraph and word
timestamps are present. Keep these omissions to reproduce the parsing failure.
The audio itself is not included.
