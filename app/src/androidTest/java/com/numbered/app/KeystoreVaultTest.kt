package com.numbered.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.numbered.app.security.KeystoreVault
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Android Keystore exists only on a device, so the passphrase kept for automatic backups is tested here. */
@RunWith(AndroidJUnit4::class)
class KeystoreVaultTest {
    private val vault = KeystoreVault()

    @Test fun aSealedPassphraseOpensAgain() {
        val passphrase = "correct horse battery staple ✓ 妈妈"
        val sealed = vault.seal(passphrase)
        assertFalse(sealed.contains("horse"))
        assertEquals(passphrase, vault.open(sealed))
        // A fresh vault finds the same key, as after the app restarts.
        assertEquals(passphrase, KeystoreVault().open(sealed))
    }

    @Test fun everySealingIsDifferent() {
        assertNotEquals(vault.seal("same words"), vault.seal("same words"))
    }

    @Test fun alteredOrForeignTextNeverOpens() {
        val sealed = Base64.getDecoder().decode(vault.seal("correct horse"))
        sealed[sealed.size - 1] = (sealed.last().toInt() xor 1).toByte()
        assertNull(vault.open(Base64.getEncoder().encodeToString(sealed)))
        assertNull(vault.open(Base64.getEncoder().encodeToString(ByteArray(40))))
        assertNull(vault.open("not base64 at all"))
        assertNull(vault.open(""))
    }
}
