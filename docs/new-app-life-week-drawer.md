# Life week drawer for a new app

## Problem this solves

On a phone, a persistent selected-week card such as "Week 2,139 · age 40" can consume several rows of the life grid.
The person loses the overview while inspecting a week, especially with larger text or a short screen.
Keep the selected week's identity and a way back to its details without permanently reserving the full card height.

## Required interaction

- Place the selected-week panel above the bottom navigation and keep it visible while the grid scrolls.
- Rest in a compact panel about one touch target tall.
Show the lifetime week number and age, plus a clearly labelled expand control.
- Tapping anywhere on the compact header, including the week label and expand control, reveals the selected week's date range, status, any age milestone, chapters, and a short note or commitment preview.
Tapping the header again retracts those details.
Only tapping the expanded details opens that week's full page.
- Selecting a square in the grid briefly expands the panel so the new selection is understandable.
After about 4.5 seconds, retract only the details with a gentle vertical size and opacity transition of about 240 ms.
Selecting another square restarts that interval.
- A manual expand or collapse cancels the automatic retraction.
While expanded, provide previous and next week controls and a direct route to the full week.
- Keep the week identity and expand control stable during the transition so the panel reads as one drawer.
Do not move the bottom navigation or cover the grid with an overlay.

## Acceptance checks

- At 366dp width with 145% text scaling, the resting panel occupies one compact row and reveals more grid than the full panel.
- After a grid selection, the correct week details appear, then retract without changing the selected week.
- The panel can be reopened and closed by touch and screen reader, and week navigation and opening the full week still work.
- Long week details truncate within the panel rather than forcing the compact header taller.
- Follow the platform's reduced-motion setting.
