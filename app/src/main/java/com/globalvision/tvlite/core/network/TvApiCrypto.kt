package com.globalvision.tvlite.core.network

import android.util.Base64
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey as JdkRsaPublicKey
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object TvApiCrypto {
    private val rsaPublicKey: JdkRsaPublicKey by lazy {
        loadPublicKey(TvApiConfig.PUBLIC_KEY_PEM)
    }

    fun signPack(pack: String): String {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(TvApiConfig.SIGN_KEY.toByteArray(), "HmacMD5"))
        return mac.doFinal(pack.toByteArray()).toHex()
    }

    fun encryptPack(message: String): String {
        val maxChunkSize = (rsaPublicKey.modulus.bitLength() / 8) - 11
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)

        val input = message.toByteArray()
        val output = java.io.ByteArrayOutputStream()
        var offset = 0
        while (offset < input.size) {
            val end = minOf(offset + maxChunkSize, input.size)
            output.write(cipher.doFinal(input.copyOfRange(offset, end)))
            offset = end
        }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
            .trimEnd('=')
    }

    fun decryptResponse(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed

        val encryptedText = trimmed.trim('"')
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(TvApiConfig.AES_KEY.toByteArray(), "AES"),
            IvParameterSpec(TvApiConfig.AES_IV.toByteArray()),
        )
        val normalized = encryptedText
            .replace('-', '+')
            .replace('_', '/')
            .padEnd(((encryptedText.length + 3) / 4) * 4, '=')
        val decoded = Base64.decode(
            normalized,
            Base64.DEFAULT,
        )
        return cipher.doFinal(decoded).toString(Charsets.UTF_8)
    }

    private fun loadPublicKey(pem: String): JdkRsaPublicKey {
        val body = pem
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "")
        val bytes = Base64.decode(body, Base64.DEFAULT)
        val asn1 = RSAPublicKey.getInstance(ASN1Primitive.fromByteArray(bytes))
        val spec = RSAPublicKeySpec(asn1.modulus, asn1.publicExponent)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(spec) as JdkRsaPublicKey
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte) }
}
