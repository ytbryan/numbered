# Numbered

A weekly planner where the week is the unit, not the task.
Choose up to three commitments a week, close each week with one line, and watch your life fill in on a grid of weeks.
Named after Psalm 90:12.

The iOS app lives in [`number_ios`](number_ios/README.md).
It shares these rules and the export file, so a copy moves between the two.

## Screens

- **This week**: up to three commitments, a prompt to close any unclosed past week, next week, and stale Someday items.
- **Life**: every week of your life, 52 to a row, coloured by what you finished. Tap or drag to inspect a week.
- **Someday**: ideas without dates. Items untouched for 12 weeks ask whether they still deserve a square.
- **Close week**: decide what happens to each unfinished commitment (done, carry, Someday, or let go) and write one line.
Unfinished reflections are saved on this phone as you type, including in Catch up.
The note field’s prompt icon offers three optional reflection questions, with a chosen question shown below the field.
Questions can be changed or hidden and are kept separate from saved notes and drafts.
- **App lock**: an optional setting requiring Android biometric or screen-lock authentication to open the app, including shared ideas and widget links.
Enabling and disabling it require authentication.
Leaving the app locks it again; rotation preserves an authenticated session.
Widget details and screenshots/recent-app previews are hidden whenever App lock is on.
Its preference stays on this phone and is excluded from exports, device backups, and recovery restores.
App lock controls access to the app; exported files and Android database backups keep their existing protections.
- **Catch up**: after time away, close every open past week at once.
- **Your lines**: every weekly line, grouped by year with what each year held, and searchable.
- **Chapters**: name a stretch of life, like a move or a job. Chapters mark the grid and the weeks they cover.
- **Reminders**: optional nudges to plan the week and to close it, set in Settings.
- **Widget**: this week on the home screen. Tick commitments off, add one, or close the week near its end.

Life offers both a whole-life overview and larger week squares for one calendar year, with year navigation and a shortcut back to this week.
The selected-week drawer has a [reusable interaction specification](docs/new-app-life-week-drawer.md) for new apps.
This week names today and highlights it in a seven-day strip ordered by your chosen week start.
Settings can add a circular or week-by-week bar view of calendar-year progress above This week.
Years with 53 calendar weeks show all 53 marks.
Weeks near birthdays and at the six-month midpoint show age-relative labels.
Lifetime week numbers remain on Life and week detail for orientation.
Life also shows today and the current calendar week within its year.
Lifetime totals sit below the grid.
A short explanation and help button sit above it; the help sheet holds the guidance, colour key, and age-horizon note.
Someday searches across waiting and let-go ideas, with oldest, newest, and recently kept sorting.
Share text or a link from another Android app to edit it and save it to Someday, then return to that app.
Shared ideas can be saved before setup.
Search on This week finds commitments, weekly notes, all Someday ideas, and chapters, with links to open each result.
Moving a commitment to next week or back to Someday, and scheduling a Someday idea, offer Undo.
Undo restores the original entry and removes the entry created by the move, provided neither has changed and the original week still has room.
Carry-over history opens from the commitment menu and follows its weeks even after a rename.
Older entries are linked only when their original title and carry time identify one source.
Missing or ambiguous earlier entries are shown explicitly.
Database version 3 adds these links without replacing saved data.
Backup format version 3 includes them and still reads versions 1 and 2.
Year in review opens from Your lines, with a year picker and a preview of completed commitments, notes, and overlapping chapters.
Weekly notes appear first and open their original week; completed commitments are grouped into expandable months.
Save the preview as a UTF-8 text file or share it using Android’s share sheet.
Reviews use the same week-based years as Your lines and include recorded data through the current week.
Saved and shared summaries contain the selected year’s previewed content without birth dates, drafts, or Someday items.

Settings keeps short descriptions beside controls, with fuller explanations in help sheets and the name’s origin under About Numbered.
Onboarding introduces the three steps briefly.
The empty Chapters section offers a New chapter action for the selected week.
Catch up marks reflection fields as optional even while typing.

## Rules worth knowing

- A week holds at most three open or done commitments. Every write that could break this runs in one transaction in `NumberedRepository`.
- Letting go and returning to Someday are decisions, so they do not count against a week.
- Weeks are keyed by their start date, and the first day of the week is fixed at setup, so editing the birth date never moves saved weeks.
- Everything is local (Room, `numbered.db`) and included in Android device backups. There is no account.
- Settings can export everything to a JSON file, optionally protected with a passphrase, and import it again. Setup offers to restore one on a new phone.
Importing replaces all data in one transaction, after showing what the file holds.
Before replacing existing data, the app saves one private recovery copy on this phone.
Settings shows the last successful export date and can restore the data saved before the last import.
Recovery copies and unfinished reflection drafts stay on this phone and are not included in exports or device backups.
- The export format (`BackupFormat`) has its own types and version, separate from the database, so files people keep stay readable.
Change its shape only by bumping `BackupFormat.VERSION`, and keep every older version readable.

## Changing the database

A person's weeks span decades, so the database is never rebuilt destructively.
To change an entity, bump `NumberedDatabase.VERSION`, add the migration to `MIGRATIONS`, commit the new schema in `app/schemas`, pin its hash in `MigrationTest`, and add a test that opens the previous version's data.
`MigrationTest` fails if a released schema changes without a version bump, and CI fails if the regenerated schema is not committed.

## Build and test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug assembleRelease
```

CI (`.github/workflows/ci.yml`) runs the second line on every push to `main` and every pull request, and uploads the screens and reports.
Lint treats warnings as errors, and deliberate exceptions live in `app/lint.xml`.

Release builds are minified and unsigned unless these Gradle properties are set, for example in `~/.gradle/gradle.properties` or as `ORG_GRADLE_PROJECT_<name>` environment variables: `numberedKeystore`, `numberedKeystorePassword`, `numberedKeyAlias`, and `numberedKeyPassword`.
Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every release.

`ScreenCaptureTest` drives the real app on seeded data with Robolectric and writes every screen to `app/build/screens/`, mostly at 366dp wide and 145% text.

The toolchain stays on AGP 8.13 and compileSdk 36, like Doerlist.
Compose 1.12, Lifecycle 2.11, and Navigation 2.10 need compileSdk 37 and AGP 9.1, so move both projects together.
