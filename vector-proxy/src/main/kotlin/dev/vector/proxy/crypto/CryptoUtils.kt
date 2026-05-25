package dev.vector.proxy.crypto

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.KeyPair
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    fun generateKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(1024)
        return gen.generateKeyPair()
    }

    fun decryptRsa(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(data)
    }

    fun createCipher(mode: Int, secret: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CFB8/NoPadding")
        cipher.init(mode, SecretKeySpec(secret, "AES"), IvParameterSpec(secret))
        return cipher
    }

    // Minecraft's server hash: SHA-1 of (serverId || sharedSecret || publicKey),
    // interpreted as a signed big-endian integer and formatted as hex.
    // The result can be negative (leading '-').
    fun serverHash(serverId: String, sharedSecret: ByteArray, publicKey: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(serverId.toByteArray(Charsets.US_ASCII))
        md.update(sharedSecret)
        md.update(publicKey)
        return BigInteger(md.digest()).toString(16)
    }
}
