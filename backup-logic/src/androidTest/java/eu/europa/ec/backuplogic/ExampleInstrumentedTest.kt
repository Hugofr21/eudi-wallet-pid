package eu.europa.ec.backuplogic

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {


    @Test
    fun bip39OrderMattersTest() {
        val mnemonicStrTest1 = listOf(
            "abandon","ability","able","about","above","absent",
            "absorb","abstract","absurd","abuse","access","accident"
        ).joinToString("\n")

        val mnemonicStrTest2 = listOf(
            "abandon","abstract","able","about","above","absent",
            "absorb","ability","absurd","abuse","access","accident"
        ).joinToString("\n")


        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val key1 = deriveKey(mnemonicStrTest1, salt)
        val key2 = deriveKey(mnemonicStrTest2, salt)
        val key1Again = deriveKey(mnemonicStrTest1, salt)

        println("Key1 == Key2? ${key1.contentEquals(key2)}")
        println("Key1 == Key1Again? ${key1.contentEquals(key1Again)}")

        assertFalse("Keys must differ with reversed order", key1.contentEquals(key2))
        assertArrayEquals("Repeated call generates same key", key1, key1Again)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            1_000_000,
            256
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}