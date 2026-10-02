# Numbered

A weekly planner where the week is the unit, not the task.
Choose up to three commitments a week, close each week with one line, and watch your life fill in on a grid of weeks.
Named after Psalm 90:12.

## Screens

- **This week**: up to three commitments, a prompt to close any unclosed past week, next week, and stale Someday items.
- **Life**: every week of your life, 52 to a row, coloured by what you finished. Tap or drag to inspect a week.
- **Someday**: ideas without dates. Items untouched for 12 weeks ask whether they still deserve a square.
- **Close week**: decide what happens to each unfinished commitment (done, carry, Someday, or let go) and write one line.

## Rules worth knowing

- A week holds at most three open or done commitments. Every write that could break this runs in one transaction in `NumberedRepository`.
- Letting go and returning to Someday are decisions, so they do not count against a week.
- Weeks are keyed by their start date, and the first day of the week is fixed at setup, so editing the birth date never moves saved weeks.
- Everything is local (Room, `numbered.db`) and included in Android device backups. There is no account.
- Settings can export everything to a JSON file, optionally protected with a passphrase, and import it again. Setup offers to restore one on a new phone.
Importing replaces all data in one transaction, after showing what the file holds.
- The export format (`BackupFormat`) has its own types and version, separate from the database, so files people keep stay readable.
Change its shape only by bumping `BackupFormat.VERSION`, and keep every older version readable.

## Changing the database

A person's weeks span decades, so the database is never rebuilt destructively.
To change an entity, bump `NumberedDatabase.VERSION`, add the migration to `MIGRATIONS`, commit the new schema in `app/schemas`, pin its hash in `MigrationTest`, and add a test that opens the previous version's data.
`MigrationTest` fails if a released schema changes without a version bump.

## Build and test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Lint treats warnings as errors, and deliberate exceptions live in `app/lint.xml`.

Release builds are minified and unsigned unless these Gradle properties are set, for example in `~/.gradle/gradle.properties` or as `ORG_GRADLE_PROJECT_<name>` environment variables: `numberedKeystore`, `numberedKeystorePassword`, `numberedKeyAlias`, and `numberedKeyPassword`.
Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every release.

`ScreenCaptureTest` drives the real app on seeded data with Robolectric and writes every screen to `app/build/screens/`, mostly at 366dp wide and 145% text.

The toolchain stays on AGP 8.13 and compileSdk 36, like Doerlist.
Compose 1.12, Lifecycle 2.11, and Navigation 2.10 need compileSdk 37 and AGP 9.1, so move both projects together.
