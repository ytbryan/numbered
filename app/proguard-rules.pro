# R8 shrinks and obfuscates release builds. Room, Navigation, Compose, WorkManager, Glance, and
# kotlinx.serialization ship their own consumer rules, which keep what they reach by reflection:
# the generated database, workers, widget callbacks, and serializers. Backup field names come from
# the serializers, not class names, so obfuscation never changes the export format.
