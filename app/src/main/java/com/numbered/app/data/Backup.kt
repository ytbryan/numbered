package com.numbered.app.data

import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.domain.MAX_COMMITMENTS_PER_WEEK
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Everything Numbered stores, as one consistent copy. */
data class Snapshot(
    val profile: Profile,
    val commitments: List<Commitment>,
    val someday: List<SomedayItem>,
    val reviews: List<WeekReview>,
    val chapters: List<Chapter> = emptyList(),
    /** When the copy was taken, for files read back in. */
    val savedAt: Long = 0,
)

sealed interface BackupRead {
    data class Ok(val snapshot: Snapshot) : BackupRead

    /** Not a file Numbered wrote. */
    data object NotABackup : BackupRead

    /** Written by a newer version of Numbered, so this one cannot read it safely. */
    data object TooNew : BackupRead

    /** A Numbered file that is incomplete or breaks the app's rules. */
    data object Damaged : BackupRead

    /** A file protected with a passphrase. [BackupFormat.unlock] reads it with the right one. */
    class Locked internal constructor(internal val text: String) : BackupRead

    /** The passphrase does not open this file. */
    data object WrongPassphrase : BackupRead
}

/**
 * The export file: plain JSON that a person can keep, read, and bring to a new phone.
 *
 * The file has its own types instead of serializing the Room entities, so the database can change
 * without breaking files people already keep. Dates are ISO 8601 and times are epoch milliseconds.
 * Any change to the shape of the file bumps [VERSION], and every older version stays readable.
 *
 * A file protected with a passphrase keeps the same header and records how it was sealed, with
 * the plain file encrypted in `data`. See [BackupCipher].
 */
object BackupFormat {
    const val NAME = "numbered"
    /** 2 added chapters; 3 adds stable carry-over links. */
    const val VERSION = 3

    /** Larger than decades of weekly use, and small enough to refuse a wrongly picked video. */
    const val MAX_BYTES = 16 * 1024 * 1024

    private val SIGNATURE = Regex("\"format\"\\s*:\\s*\"$NAME\"")
    private const val SIGNATURE_WINDOW = 200

    private val writer = Json {
        prettyPrint = true
        explicitNulls = false
    }
    private val header = Json { ignoreUnknownKeys = true }
    private val reader = Json

    /** Writes [snapshot], sealed with [passphrase] when one is given. */
    fun encode(snapshot: Snapshot, passphrase: String? = null): String {
        val plain = encodePlain(snapshot)
        if (passphrase == null) return plain
        val sealed = BackupCipher.seal(plain.toByteArray(Charsets.UTF_8), passphrase)
        val base64 = Base64.getEncoder()
        return writer.encodeToString(
            SealedJson.serializer(),
            SealedJson(
                format = NAME,
                version = VERSION,
                encryption = EncryptionJson(
                    kdf = BackupCipher.KDF,
                    iterations = sealed.iterations,
                    salt = base64.encodeToString(sealed.salt),
                    cipher = BackupCipher.CIPHER,
                    nonce = base64.encodeToString(sealed.nonce),
                ),
                data = base64.encodeToString(sealed.ciphertext),
            ),
        )
    }

    private fun encodePlain(snapshot: Snapshot): String = writer.encodeToString(
        BackupJson.serializer(),
        BackupJson(
            format = NAME,
            version = VERSION,
            savedAt = snapshot.savedAt,
            profile = snapshot.profile.let {
                ProfileJson(it.birthDate.toString(), it.horizonYears, it.firstDayOfWeek.key, it.gentle, it.startedOn.toString())
            },
            commitments = snapshot.commitments.map {
                CommitmentJson(
                    id = it.id,
                    weekStart = it.weekStart.toString(),
                    title = it.title,
                    status = it.status.key,
                    createdAt = it.createdAt,
                    resolvedAt = it.resolvedAt,
                    carriedFrom = it.carriedFrom?.toString(),
                    carriedFromId = it.carriedFromId,
                )
            },
            someday = snapshot.someday.map { SomedayJson(it.id, it.title, it.createdAt, it.keptAt, it.letGoAt) },
            weekNotes = snapshot.reviews.map { WeekNoteJson(it.weekStart.toString(), it.note, it.closedAt) },
            chapters = snapshot.chapters.map {
                ChapterJson(it.id, it.title, it.startWeek.toString(), it.endWeek?.toString(), it.createdAt)
            },
        ),
    )

    fun decode(text: String): BackupRead {
        val head = runCatching { header.decodeFromString(HeaderJson.serializer(), text) }.getOrNull()
            // A cut-off copy no longer parses, but its opening still says what it was.
            ?: return if (SIGNATURE.containsMatchIn(text.take(SIGNATURE_WINDOW))) BackupRead.Damaged else BackupRead.NotABackup
        if (head.format != NAME || head.version == null) return BackupRead.NotABackup
        if (head.version > VERSION) return BackupRead.TooNew
        if (head.encryption != null) return BackupRead.Locked(text)
        val snapshot = runCatching { reader.decodeFromString(BackupJson.serializer(), text).toSnapshot() }.getOrNull()
        return if (snapshot != null && snapshot.followsTheRules()) BackupRead.Ok(snapshot) else BackupRead.Damaged
    }

    /** Opens a protected file. Slow on purpose, so call it off the main thread. */
    fun unlock(locked: BackupRead.Locked, passphrase: String): BackupRead {
        if (passphrase.isEmpty()) return BackupRead.WrongPassphrase
        val sealed = runCatching {
            val file = reader.decodeFromString(SealedJson.serializer(), locked.text)
            val base64 = Base64.getDecoder()
            file.encryption.takeIf {
                it.kdf == BackupCipher.KDF && it.cipher == BackupCipher.CIPHER && it.iterations in 1..BackupCipher.MAX_ITERATIONS
            }?.let { Sealed(base64.decode(it.salt), base64.decode(it.nonce), it.iterations, base64.decode(file.data)) }
        }.getOrNull() ?: return BackupRead.Damaged
        val plain = BackupCipher.open(sealed, passphrase) ?: return BackupRead.WrongPassphrase
        // The sealed file holds a plain one, never another sealed layer.
        return when (val inner = decode(plain.decodeToString())) {
            is BackupRead.Ok, BackupRead.TooNew -> inner
            else -> BackupRead.Damaged
        }
    }

    private fun BackupJson.toSnapshot() = Snapshot(
        profile = Profile(
            birthDate = LocalDate.parse(profile.birthDate),
            horizonYears = profile.horizonYears,
            firstDayOfWeek = dayOf(profile.firstDayOfWeek),
            gentle = profile.gentle,
            startedOn = LocalDate.parse(profile.startedOn),
        ),
        commitments = commitments.map {
            Commitment(
                id = it.id,
                weekStart = LocalDate.parse(it.weekStart),
                title = it.title,
                status = statusOf(it.status),
                createdAt = it.createdAt,
                resolvedAt = it.resolvedAt,
                carriedFrom = it.carriedFrom?.let(LocalDate::parse),
                carriedFromId = it.carriedFromId,
            )
        },
        someday = someday.map { SomedayItem(it.id, it.title, it.createdAt, it.keptAt, it.letGoAt) },
        reviews = weekNotes.map { WeekReview(LocalDate.parse(it.weekStart), it.note, it.closedAt) },
        chapters = chapters.map {
            Chapter(it.id, it.title, LocalDate.parse(it.startWeek), it.endWeek?.let(LocalDate::parse), it.createdAt)
        },
        savedAt = savedAt,
    )

    /** The same rules the app keeps while it runs, so an imported copy cannot break a screen. */
    private fun Snapshot.followsTheRules(): Boolean {
        val firstDay = profile.firstDayOfWeek
        fun LocalDate.startsWeek() = dayOfWeek == firstDay
        val occupiedPerWeek = commitments
            .filter { it.status == CommitmentStatus.Open || it.status == CommitmentStatus.Done }
            .groupingBy { it.weekStart }
            .eachCount()
        val byId = commitments.associateBy { it.id }
        val validLinks = commitments.all { entry ->
            val parentId = entry.carriedFromId ?: return@all true
            val parent = byId[parentId]
            parentId > 0 && parentId != entry.id && entry.carriedFrom?.let { it < entry.weekStart } == true &&
                (parent == null || (parent.status == CommitmentStatus.Carried && parent.weekStart == entry.carriedFrom))
        } && commitments.mapNotNull { it.carriedFromId }.let { it.toSet().size == it.size }
        return validLinks && profile.horizonYears in LifeCalendar.HORIZON_CHOICES &&
            !profile.birthDate.isAfter(profile.startedOn) &&
            commitments.all { it.weekStart.startsWeek() && it.carriedFrom?.startsWeek() != false && it.title.isCleanTitle() } &&
            commitments.map { it.id }.let { ids -> ids.all { it > 0 } && ids.toSet().size == ids.size } &&
            occupiedPerWeek.values.all { it <= MAX_COMMITMENTS_PER_WEEK } &&
            someday.all { it.title.isCleanTitle() } &&
            someday.map { it.id }.let { ids -> ids.all { it > 0 } && ids.toSet().size == ids.size } &&
            reviews.all { it.weekStart.startsWeek() } &&
            reviews.map { it.weekStart }.let { weeks -> weeks.toSet().size == weeks.size } &&
            chapters.all { chapter ->
                chapter.title.isCleanTitle() && chapter.startWeek.startsWeek() &&
                    chapter.endWeek.let { it == null || (it.startsWeek() && it >= chapter.startWeek) }
            } &&
            chapters.map { it.id }.let { ids -> ids.all { it > 0 } && ids.toSet().size == ids.size }
    }

    private fun String.isCleanTitle() = cleanTitle() == this

    // Explicit names keep the file stable if the Kotlin enums are ever renamed.
    private val DayOfWeek.key: String get() = name.lowercase()

    private fun dayOf(key: String): DayOfWeek = DayOfWeek.entries.first { it.key == key }

    private val CommitmentStatus.key: String
        get() = when (this) {
            CommitmentStatus.Open -> "open"
            CommitmentStatus.Done -> "done"
            CommitmentStatus.Carried -> "carried"
            CommitmentStatus.ReturnedToSomeday -> "returned_to_someday"
            CommitmentStatus.LetGo -> "let_go"
        }

    private fun statusOf(key: String): CommitmentStatus = CommitmentStatus.entries.first { it.key == key }
}

@Serializable
private class HeaderJson(val format: String? = null, val version: Int? = null, val encryption: JsonElement? = null)

@Serializable
private class SealedJson(val format: String, val version: Int, val encryption: EncryptionJson, val data: String)

@Serializable
private class EncryptionJson(val kdf: String, val iterations: Int, val salt: String, val cipher: String, val nonce: String)

@Serializable
private class BackupJson(
    val format: String,
    val version: Int,
    val savedAt: Long,
    val profile: ProfileJson,
    val commitments: List<CommitmentJson>,
    val someday: List<SomedayJson>,
    val weekNotes: List<WeekNoteJson>,
    /** Added in version 2, so absent from version 1 files. */
    val chapters: List<ChapterJson> = emptyList(),
)

@Serializable
private class ProfileJson(
    val birthDate: String,
    val horizonYears: Int,
    val firstDayOfWeek: String,
    val gentle: Boolean,
    val startedOn: String,
)

@Serializable
private class CommitmentJson(
    val id: Long,
    val weekStart: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val carriedFrom: String? = null,
    /** Added in version 3; older copies keep their original week-only provenance. */
    val carriedFromId: Long? = null,
)

@Serializable
private class SomedayJson(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val keptAt: Long? = null,
    val letGoAt: Long? = null,
)

@Serializable
private class ChapterJson(
    val id: Long,
    val title: String,
    val startWeek: String,
    val endWeek: String? = null,
    val createdAt: Long,
)

@Serializable
private class WeekNoteJson(val weekStart: String, val note: String, val closedAt: Long)
