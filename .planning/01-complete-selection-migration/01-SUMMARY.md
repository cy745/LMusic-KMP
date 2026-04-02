---
phase: 01-complete-selection-migration
plan: "01"
subsystem: ui
tags: [compose, selection, kmp, item-selector, animation]

# Dependency graph
requires: []
provides:
  - AudioItemCard with selection parameters (isSelecting, isSelected, onEnterSelect, onSelect, onPlay, onNavigateToDetail)
  - SongsScreenContent integration passing selector lambdas to AudioItemCard
  - Animated selection background (primaryContainer at 30% opacity)
  - Long-press-to-select on image, click-to-toggle-select on row
affects: [Phase 2+ batch operations, Phase 3+ favorite toggle]

# Tech tracking
tech-stack:
  added: [androidx.compose.animation.animateColorAsState, androidx.compose.foundation.combinedClickable]
  patterns: [lambda-based selection state, animated background transitions, combinedClickable for dual-action handlers]

key-files:
  created: []
  modified:
    - lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt
    - lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt

key-decisions:
  - "D-04: Use MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) for selection background (theme accent, not neutral gray)"
  - "D-05: Use animateColorAsState for smooth background color transition"
  - "D-06: Use RoundedCornerShape(2.dp) for selection background clip"
  - "D-08: AsyncImage onLongClick calls onEnterSelect() — image long press enters selection mode"
  - "D-09: Row onClick checks isSelecting() then calls onSelect() in selection mode"
  - "D-10: Row onClick calls onPlay() in normal mode"
  - "D-11: Row onLongClick calls onNavigateToDetail() in normal mode, onEnterSelect() in selection mode"
  - "D-12: combinedClickable used on both Row and AsyncImage for separate behaviors"
  - "D-14: onSelect passed as onClick handler in selection mode"
  - "D-15: onPlay wraps PlayerAction.UpdateList for play behavior"

patterns-established:
  - "Lambda parameters for reactive selection state (isSelecting: () -> Boolean, isSelected: () -> Boolean)"
  - "Internal click handling in AudioItemCard (no external combinedClickable modifier needed)"
  - "Animated selection background with animateColorAsState and theme accent color"

requirements-completed: [REQ-01, REQ-02, REQ-03, REQ-04, REQ-05, REQ-06, REQ-07]

# Metrics
duration: 5min
completed: 2026-04-02
---

# Phase 01 Plan 01: Complete Selection Migration Summary

**AudioItemCard extended with selection parameters and animated background; SongsScreenContent wired to pass selector lambdas and play/detail handlers**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-02T08:24:35Z
- **Completed:** 2026-04-02T08:29:53Z
- **Tasks:** 2/2
- **Files modified:** 2

## Accomplishments
- AudioItemCard accepts 6 new selection-related parameters: isSelecting, isSelected, onEnterSelect, onSelect, onPlay, onNavigateToDetail
- Animated selection background using primaryContainer at 30% opacity with animateColorAsState
- combinedClickable on both Row (play/select + detail/enter-select) and AsyncImage (enter selection on long-press)
- SongsScreenContent removed external combinedClickable, passes all handler lambdas to AudioItemCard
- Compilation verified successful via `:lhome:jvmMainClasses`

## Task Commits

Each task was committed atomically:

1. **Task 1.1: Add selection parameters and visual feedback to AudioItemCard** - `aa0bcf2` (feat)
2. **Task 1.2: Update SongsScreenContent to integrate selection parameters** - `04aacf4` (feat)
3. **Auto-fix: Add missing getValue import** - `7aca16d` (fix)

**Plan metadata commit:** `docs(01-complete-selection-migration): complete 01-complete-selection-migration plan`

## Files Created/Modified

- `lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt` - Extended with selection parameters, animated background, combinedClickable handlers
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt` - Passes selector lambdas and play/detail handlers to AudioItemCard, removed external combinedClickable

## Decisions Made

- Used MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) for selection background (theme accent, not neutral gray)
- Used animateColorAsState for smooth background transitions
- Used RoundedCornerShape(2.dp) for selection background clip
- AsyncImage long-press enters selection mode; Row click toggles selection in selection mode, plays in normal mode
- Row long-press navigates to detail in normal mode, continues selection in selection mode
- Removed combinedClickable from SongsScreen modifier chain (now handled internally by AudioItemCard)

## Deviations from Plan

None - plan executed exactly as written.

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing getValue import for animateColorAsState by delegate**
- **Found during:** Task 1.1 (Add selection parameters and visual feedback to AudioItemCard)
- **Issue:** Compilation error: "Property delegate must have a 'getValue(Nothing?, KProperty0<*>)' method" on line 39
- **Fix:** Added `import androidx.compose.runtime.getValue` to enable `by` delegate syntax with animateColorAsState
- **Files modified:** `lhome/src/commonMain/kotlin/com/lalilu/lhome/component/AudioItemCard.kt`
- **Verification:** `./gradlew :lhome:jvmMainClasses` completed with BUILD SUCCESSFUL
- **Committed in:** `7aca16d` (fix commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Auto-fix was necessary for compilation. No scope creep.

## Issues Encountered
- None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- REQ-01 through REQ-05 are fully implemented and verified
- REQ-06 (selection mode entry button) already existed in SongsScreen.kt
- REQ-07 (selection panel actions) already existed and remains functional
- Ready for Phase 2: batch operations (add to favorites, add to playlist)

## Self-Check: PASSED

- [x] 01-SUMMARY.md created
- [x] AudioItemCard.kt modified (aa0bcf2, 7aca16d)
- [x] SongsScreen.kt modified (04aacf4)
- [x] All 4 commits found in git log
- [x] STATE.md updated with phase complete
- [x] ROADMAP.md updated with 100% completion
- [x] REQUIREMENTS.md updated with all 7 requirements completed
- [x] Compilation verified via `:lhome:jvmMainClasses` BUILD SUCCESSFUL

---
*Phase: 01-complete-selection-migration*
*Completed: 2026-04-02*
