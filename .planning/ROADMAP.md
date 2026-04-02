# Roadmap

## Phase Overview

| # | Phase | Goal | Requirements | Status |
|---|-------|------|--------------|--------|
| 1 | Complete Selection Migration | Migrate song selection functionality to AudioItemCard | REQ-01 ~ REQ-07 | Complete |

---

## Phase 1: Complete Selection Migration

**Goal:** Migrate song selection functionality from original Android project to current KMP project's AudioItemCard component

**Description:**
Complete the migration of song selection functionality by updating AudioItemCard component to support selection states, animations, and interactions. Integrate with existing ItemSelector and SongsSelectorPanel.

**Requirements:**
- REQ-01: Long press to enter selection mode
- REQ-02: Selection mode click behavior
- REQ-03: Normal mode click behavior
- REQ-04: Selection visual feedback
- REQ-05: Smooth animations
- REQ-06: Selection mode entry button
- REQ-07: Selection panel actions

**Success Criteria:**
1. User can long press song image to enter selection mode and select that song
2. In selection mode, clicking a song card toggles selection state
3. In non-selection mode, clicking a song card plays that song
4. When selected, song card displays semi-transparent gray background
5. Background color has smooth transition animation when selection state changes
6. Clicking 'Select' button enters selection mode
7. Selection panel displays select all, deselect all, add to favorites, add to playlist buttons

**Canonical refs:**
- `build/LMusic-source/component/src/main/java/com/lalilu/component/card/SongCard.kt` — Original SongCard implementation with selection support
- `build/LMusic-source/app/src/main/java/com/lalilu/lmusic/compose/screen/songs/SongsScreen.kt` — Original SongsScreen with selection integration

**UI hint:** no

**Plans:**
- [x] 01-PLAN.md — Add selection parameters and visual feedback to AudioItemCard, integrate with SongsScreenContent (1/1 completed)

---

## Progress Tracking

| Phase | Plans | Status | Completion |
|-------|-------|--------|-------------|
| 1 | 1/1 | Complete | 100% |
