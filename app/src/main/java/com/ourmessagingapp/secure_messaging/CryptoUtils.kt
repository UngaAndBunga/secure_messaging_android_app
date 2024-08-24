package com.ourmessagingapp.secure_messaging

import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {

    init {
        AeadConfig.register()
    }

    private const val SALT = "SuperDuper13SecretExtra$#@!PasswordMilko123-=" // **WARNING: This is a placeholder, use a strong random salt.**

    @Throws(GeneralSecurityException::class)
    fun deriveKey(password: String): Aead {
        val keySpec = PBEKeySpec(password.toCharArray(), SALT.toByteArray(), 10000, 256)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = keyFactory.generateSecret(keySpec).encoded
        return AesGcmJce(secretKey)
    }

    fun encrypt(aead: Aead, plaintext: String, associatedData: ByteArray = ByteArray(0)): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(), associatedData)
        return Base64.getEncoder().encodeToString(ciphertext)
    }

    fun decrypt(aead: Aead, ciphertext: String, associatedData: ByteArray = ByteArray(0)): String {
        try {
            val decodedCiphertext = Base64.getDecoder().decode(ciphertext)
            val plaintext = aead.decrypt(decodedCiphertext, associatedData) // Corrected order of parameters
            return String(plaintext, Charsets.UTF_8)
        }catch (e: IllegalArgumentException){
            return ciphertext
        }catch (e: AEADBadTagException){
            return "Error decrypting message your passwords have to be one and the same!"
        }
    }
}
