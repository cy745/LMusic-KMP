---
phase: 01-complete-selection-migration
plan: "01"
type: execute
wave: 1
depends_on: []
files_modified:
  - lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt
  - lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt
autonomous: true
requirements:
  - REQ-01
  - REQ-02
  - REQ-03
  - REQ-04
  - REQ-05
  - REQ-06
  - REQ-07

must_haves:
  truths:
    - "User can long press song image to enter selection mode and select that song"
    - "In selection mode, clicking a song card toggles selection state"
    - "In non-selection mode, clicking a song card plays that song"
    - "When selected, song card displays semi-transparent theme accent background"
    - "Background color has smooth transition animation when selection state changes"
  artifacts:
    - path: "lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt"
      provides: "Song card with selection parameters and visual feedback"
      contains: "isSelecting, isSelected, onEnterSelect, onSelect, onPlay, onNavigateToDetail, animateColorAsState"
    - path: "lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt"
      provides: "SongsScreenContent integration with selector lambdas"
      contains: "selector().isSelecting.value, selector().isSelected(item), onPlay, onNavigateToDetail"
  key_links:
    - from: "SongsScreen.kt"
      to: "AudioItemCard.kt"
      via: "isSelecting, isSelected, onEnterSelect, onSelect, onPlay, onNavigateToDetail lambdas"
      pattern: "AudioItemCard.*isSelecting"
    - from: "AudioItemCard.kt"
      to: "ItemSelector.kt"
      via: "isSelecting() and isSelected() lambdas"
      pattern: "isSelecting.*Boolean"
    - from: "SongsScreen.kt"
      to: "PlayerAction"
      via: "onPlay lambda wrapping PlayerAction.UpdateList"
      pattern: "PlayerAction\\.UpdateList.*action"
---

# Phase 01: Complete Selection Migration - Plan

## Plan Summary

**Objective:** Migrate song selection functionality from original Android project to KMP project's AudioItemCard component

**Wave 1:** AudioItemCard Enhancement (2 tasks, fully parallel)

## Tasks

### Task 1.1: Add selection parameters and visual feedback to AudioItemCard

**File to modify:** `lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt`

**read_first:**
- `/Users/miku/Documents/IdeaProjects/LMusic-KMP/lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt` (current implementation)
- `/Users/miku/Documents/IdeaProjects/LMusic-KMP/build/LMusic-source/component/src/main/java/com/lalilu/component/card/SongCard.kt` (lines 130-150 selection background pattern)
- `/Users/miku/Documents/IdeaProjects/LMusic-KMP/component/src/commonMain/kotlin/com/lalilu/extensions/ItemSelector.kt` (state management interface)

**action:**
Add the following imports:
```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
```

Add new parameters to `AudioItemCard` function signature (after `imageData: Any = Unit`):
```kotlin
isSelecting: () -> Boolean = { false },
isSelected: () -> Boolean = { false },
onEnterSelect: () -> Unit = {},
onSelect: () -> Unit = {},
onPlay: () -> Unit = {},
onNavigateToDetail: () -> Unit = {}
```

Add selection background state inside the composable (before the Row):
```kotlin
val selectionColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
val bgColor by animateColorAsState(
    targetValue = if (isSelected()) selectionColor else Color.Transparent,
    label = "selection_bg"
)
```

Wrap the existing Row with selection background and combinedClickable — row onClick handles play vs select, row onLongClick handles detail navigation vs enter-select:
```kotlin
Row(
    modifier = modifier
        .clip(RoundedCornerShape(2.dp))
        .background(color = bgColor)
        .combinedClickable(
            onClick = {
                if (isSelecting()) onSelect() else onPlay()
            },
            onLongClick = {
                if (isSelecting()) onEnterSelect() else onNavigateToDetail()
            }
        )
        .padding(horizontal = 16.dp, vertical = 4.dp),
```

Add onLongClick to the AsyncImage for entering selection mode (image long press always enters selection):
```kotlin
AsyncImage(
    modifier = Modifier
        .size(64.dp)
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))
        .border(...)
        .background(...)
        .combinedClickable(
            onClick = {},
            onLongClick = { onEnterSelect() }
        ),
```

**acceptance_criteria:**
- [ ] `AudioItemCard.kt` contains `isSelecting: () -> Boolean`
- [ ] `AudioItemCard.kt` contains `isSelected: () -> Boolean`
- [ ] `AudioItemCard.kt` contains `onEnterSelect: () -> Unit`
- [ ] `AudioItemCard.kt` contains `onSelect: () -> Unit`
- [ ] `AudioItemCard.kt` contains `onPlay: () -> Unit`
- [ ] `AudioItemCard.kt` contains `onNavigateToDetail: () -> Unit`
- [ ] `AudioItemCard.kt` contains `animateColorAsState`
- [ ] `AudioItemCard.kt` contains `MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)`
- [ ] `AudioItemCard.kt` contains `RoundedCornerShape(2.dp)` for selection background
- [ ] `AudioItemCard.kt` contains `combinedClickable`
- [ ] Row's onClick: `if (isSelecting()) onSelect() else onPlay()` (per REQ-03 fix)
- [ ] Row's onLongClick: `if (isSelecting()) onEnterSelect() else onNavigateToDetail()` (per D-11 fix)

---

### Task 1.2: Update SongsScreenContent to integrate selection parameters

**File to modify:** `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt`

**read_first:**
- `/Users/miku/Documents/IdeaProjects/LMusic-KMP/lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt` (current implementation, lines 183-358)
- `/Users/miku/Documents/IdeaProjects/LMusic-KMP/build/LMusic-source/app/src/main/java/com/lalilu/lmusic/compose/screen/songs/SongsScreenContent.kt` (lines 171-195 selection integration)

**action:**
In `SongsScreenContent` function, the `selector` parameter already exists at line 188.

Pass selection and play/detail lambdas to each `AudioItemCard` call inside the itemsIndexedWithRecord block (lines 298-325):
```kotlin
AudioItemCard(
    title = item.titleValue(),
    subtitle = item.subtitleValue(),
    imageData = item,
    isSelecting = { selector().isSelecting.value },
    isSelected = { selector().isSelected(item) },
    onEnterSelect = { selector().onSelect(item) },
    onSelect = { selector().onSelect(item) },
    onPlay = {
        scope.launch {
            PlayerAction.UpdateList(
                ids = LMedia.instance.get<LAudio>().map(LItem::idValue),
                id = item.idValue(),
                start = true
            ).action()
        }
    },
    onNavigateToDetail = {
        val imageLoader = SingletonImageLoader.get(context)
        val coverMemoryKey = imageLoader.components.key(item, Options(context))
        AppRouter.route("/song/detail")
            .with("mediaId", item.idValue())
            .with("coverCacheKey", coverMemoryKey)
            .jump()
    },
    modifier = Modifier
        .animateItem()
        .padding(horizontal = 16.dp, vertical = 4.dp)
)
```

Remove the existing `combinedClickable` modifier from the modifier chain since selection/play/detail handling is now internal to the card. The entire clickable block (lines 304-323) should be removed.

**acceptance_criteria:**
- [ ] `SongsScreen.kt` contains `isSelecting = { selector().isSelecting.value }`
- [ ] `SongsScreen.kt` contains `isSelected = { selector().isSelected(item) }`
- [ ] `SongsScreen.kt` contains `onEnterSelect = { selector().onSelect(item) }`
- [ ] `SongsScreen.kt` contains `onSelect = { selector().onSelect(item) }`
- [ ] `SongsScreen.kt` contains `onPlay = { scope.launch { PlayerAction.UpdateList...`
- [ ] `SongsScreen.kt` contains `onNavigateToDetail = { AppRouter.route("/song/detail")...`
- [ ] AudioItemCard call in SongsScreen no longer has `combinedClickable` in its modifier chain

---

## Requirements Coverage

| REQ-ID | Requirement | Task |
|--------|-------------|------|
| REQ-01 | Long press to enter selection mode | Task 1.1: onEnterSelect parameter + AsyncImage onLongClick |
| REQ-02 | Selection mode click behavior | Task 1.1: Row onClick checks isSelecting() then calls onSelect() |
| REQ-03 | Normal mode click behavior | Task 1.1: Row onClick calls onPlay() in normal mode; Task 1.2: passes PlayerAction.UpdateList via onPlay |
| REQ-04 | Selection visual feedback (theme accent) | Task 1.1: primaryContainer.copy(alpha = 0.3f) |
| REQ-05 | Smooth animations | Task 1.1: animateColorAsState |
| REQ-06 | Selection mode entry button | Already exists (SongsScreen.kt line 84) |
| REQ-07 | Selection panel actions | Already exists (SongsScreen.kt lines 145-169) |

## Decision Traceability

| Decision | Implementation |
|----------|---------------|
| D-08 | AsyncImage onLongClick calls onEnterSelect() |
| D-09 | Row onClick: isSelecting() -> onSelect(), else -> onPlay() |
| D-10 | Row onClick: else branch calls onPlay() (REQ-03 fix) |
| D-11 | Row onLongClick: isSelecting() -> onEnterSelect(), else -> onNavigateToDetail() |
| D-12 | combinedClickable used on both Row and AsyncImage |
| D-14 | onSelect passed as onClick handler in selection mode |
| D-15 | onPlay wraps PlayerAction.UpdateList for play behavior |

---

## Verification

Run the following to verify the implementation compiles:
```bash
cd /Users/miku/Documents/IdeaProjects/LMusic-KMP && ./gradlew :lhome:compileKotlinDesktop 2>&1 | tail -30
```

Check for compilation errors in AudioItemCard and SongsScreen.

---

## Success Criteria

1. AudioItemCard accepts `isSelecting`, `isSelected`, `onEnterSelect`, `onSelect`, `onPlay`, `onNavigateToDetail` parameters
2. Selecting a song shows semi-transparent primaryContainer background (30% opacity)
3. Background color transitions smoothly using animateColorAsState
4. Long press on image enters selection mode and selects the song
5. Clicking card in selection mode toggles selection
6. Clicking card in normal mode plays the song (PlayerAction.UpdateList via onPlay)
7. Long press on card row in normal mode navigates to song detail page (onNavigateToDetail)
8. Long press on card row in selection mode enters/continues selection (onEnterSelect)
9. SongsScreenContent passes selector lambdas and play/navigate lambdas to AudioItemCard
10. Selection panel (SongsSelectorPanel) remains functional with select all/deselect actions
