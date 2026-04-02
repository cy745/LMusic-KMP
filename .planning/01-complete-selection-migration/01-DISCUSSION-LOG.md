# Phase 01: Complete Selection Migration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-02
**Phase:** 01-complete-selection-migration
**Areas discussed:** Component choice, Visual consistency, Interaction behavior

---

## Component Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| Extend AudioItemCard | Add selection parameters to existing component | ✓ |
| Create new SongCard component | Create separate component like original project | ✗ |

**User's choice:** Extend AudioItemCard
**Notes:** User indicated to add selection functionality to existing AudioItemCard rather than creating a new component.

---

## Visual Design

| Option | Description | Selected |
|--------|-------------|----------|
| Match original exactly | Use same colors and animations as Android project | ✓ |
| Material3 adaptive | Use Material3 color scheme without matching original | ✗ |

**User's choice:** Match original exactly
**Notes:** User specified "保持与原项目完全相同的视觉效果" (keep exactly the same visual effects as original project).

---

## Interaction Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Long press image enters selection | Match original project behavior | ✓ |
| Click anywhere enters selection | Alternative approach | ✗ |

**User's choice:** Long press image to enter selection mode
**Notes:** Based on analysis of original SongsScreenContent.kt, long press on image calls `onEnterSelect`.

---

## Deferred Ideas

- Adding `favouriteIds` display functionality — Out of scope for this phase
- Adding `prefixContent` for sort extras — Out of scope for this phase
- New batch operations (delete, share) — Would be separate phase

---
