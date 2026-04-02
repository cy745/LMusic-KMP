# Phase 01: Complete Selection Migration - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Migrate song selection functionality from original Android project to current KMP project's AudioItemCard component. The goal is to enable users to batch-select songs for operations (add to favorites, add to playlist, etc.).

**Scope:** Modify AudioItemCard component and SongsScreenContent integration only.
**Out of scope:** New batch operations, modification of selection panel UI, adding favouriteIds or prefixContent features.
</domain>

<decisions>
## Implementation Decisions

### Component Architecture
- **D-01:** Extend existing `AudioItemCard` component rather than creating a new one — reduces scope and maintains consistency
- **D-02:** Use existing `ItemSelector<T>` from `com.lalilu.extensions` for state management — no new state management needed
- **D-03:** Integration happens in `SongsScreenContent` where `selector`, `isSelecting`, `isSelected`, and `onSelect` are passed to `AudioItemCard`

### Visual Design
- **D-04:** Selection background color: Use theme's primary/secondary color with alpha — a darker, more saturated version of the theme accent color. Use `MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)` or similar, or define a custom selection color that complements the current theme (e.g., primary color at 20-30% opacity). **Do NOT use neutral gray** — prefer a color that feels intentional and themed.
- **D-05:** Use `animateColorAsState` for smooth background color transition when selection state changes
- **D-06:** Card corner radius for selection highlight: `RoundedCornerShape(2.dp)` — applied to background modifier
- **D-07:** Selection background should cover the entire card row including image area

### Interaction Behavior
- **D-08:** Selection mode entry: Long press on song image (AsyncImage) → enter selection mode and select the song
- **D-09:** Selection mode click: Clicking on song row while in selection mode → toggle selection state for that song
- **D-10:** Normal mode click: Clicking on song row while NOT in selection mode → play the song
- **D-11:** Long press on card (non-image area): Keep existing behavior → navigate to song detail page
- **D-12:** Use `combinedClickable` modifier to handle multiple clickable areas with different behaviors

### AudioItemCard Parameters
Required new parameters:
```kotlin
@Composable
fun AudioItemCard(
    // ... existing parameters ...
    isSelecting: () -> Boolean = { false },      // Whether selection mode is active
    isSelected: () -> Boolean = { false },       // Whether this item is selected
    onEnterSelect: () -> Unit = {},             // Called when user enters selection via this item
    onSelect: () -> Unit = {}                   // Called when user toggles selection
)
```

### SongsScreenContent Integration
- **D-13:** Pass `vm.selector.isSelecting`, `vm.selector::isSelected`, and `vm.selector::onSelect` to `AudioItemCard`
- **D-14:** In selection mode, clicking item should call `onSelect` (not play)
- **D-15:** In normal mode, clicking item should call existing `PlayerAction.UpdateList` (play behavior)

### Out of Scope (Deferred)
- Adding `favouriteIds` display functionality
- Adding `prefixContent` for sort extras display
- Adding new batch operations
- Modifying `SongsSelectorPanel` UI

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Original Implementation Reference
- `build/LMusic-source/component/src/main/java/com/lalilu/component/card/SongCard.kt` — Original SongCard with selection implementation (lines 59-185 show selection parameters and background animation logic)
- `build/LMusic-source/app/src/main/java/com/lalilu/lmusic/compose/screen/songs/SongsScreenContent.kt` — Original screen content showing selection integration (lines 171-195 show selection handling in item click/longclick)

### Current Project Implementation
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt` — Current AudioItemCard to be extended
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt` — Current SongsScreen with commented-out selection code (lines 327-353)
- `component/src/commonMain/kotlin/com/lalilu/extensions/ItemSelector.kt` — ItemSelector state management class
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/dialog/SongsSelectorPanel.kt` — Selection panel component

### UI/Animation Reference
- Original used neutral gray `MaterialTheme.colors.onBackground.copy(0.15f)` — **REVISED**: Use theme accent color instead (e.g., primaryContainer at 30% opacity)
- Use `animateColorAsState` with a meaningful `label` parameter for debugging
- Use `combinedClickable` for handling onClick, onLongClick, onDoubleClick on different areas
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ItemSelector<T>` class: Manages selection state with `isSelecting`, `selected()`, `onSelect()`, `selectAll()`, `clear()` methods
- `AudioItemCard` component: Already has title, subtitle, imageData layout structure
- `SongsSelectorPanel` dialog: Already integrated with select all/deselect all/playlist actions

### Established Patterns
- Use lambda parameters `() -> T` for reactive values (following existing AudioItemCard pattern)
- Use `rememberCoroutineScope()` + `LaunchedEffect` for async operations
- Use `combinedClickable` for multiple clickable areas with different behaviors

### Integration Points
- `SongsVM` has `selector: ItemSelector<LAudio>` property — already initialized
- `SongsScreen.kt` already passes `selector` to `SongsScreenContent` as parameter
- `SongsSelectorPanel` already integrated and functional
</code_context>

<specifics>
## Specific Ideas

### Selection Background Implementation Pattern (from SongCard.kt)
```kotlin
// Use theme accent color (NOT neutral gray) for selection
val selectionColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
val bgColor by animateColorAsState(
    targetValue = if (isSelected()) selectionColor
    else Color.Transparent,
    label = "selection_bg"
)

Row(
    modifier = Modifier
        .clip(RoundedCornerShape(2.dp))
        .background(color = bgColor)
        // ... combinedClickable ...
) { ... }
```

### Image Long Press Pattern (from SongCard.kt)
```kotlin
SongCardImage(
    onLongClick = { onEnterSelect() }  // Long press on image enters selection
)

// In AudioItemCard, this would be on the AsyncImage's combinedClickable
```

### Selection Mode Click Behavior (from SongsScreenContent.kt)
```kotlin
onClick = {
    if (isSelecting()) {
        onSelect(it)  // Toggle selection
    } else {
        MediaControl.playWithList(...)  // Play
    }
}
```
</specifics>

<deferred>
## Deferred Ideas

### Phase 1 Out of Scope
- **favouriteIds display** — Add heart/star indicator for favorited songs (depends on favorite service migration)
- **prefixContent display** — Show sort extra info (track number, etc.) per item (depends on SortResult extras usage)
- **New batch operations** — Adding delete, share, or other operations (would be its own phase)

### Future Phases
- Add favorite toggle in selection actions
- Add sort extra display per song item
- Desktop-specific selection behavior (checkbox instead of long-press)
</deferred>

---

*Phase: 01-complete-selection-migration*
*Context gathered: 2026-04-02*
