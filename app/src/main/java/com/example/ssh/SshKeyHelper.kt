package com.example.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.util.Base64

data class GeneratedKeyPairResult(
    val title: String,
    val publicKeyOpenSsh: String,
    val privateKeyPem: String,
    val fingerprint: String,
    val keyType: String
)

object SshKeyHelper {

    fun isValidPrivateKey(key: String): Boolean {
        val trimmed = key.trim()
        return (trimmed.startsWith("-----BEGIN") && trimmed.contains("PRIVATE KEY-----")) ||
                trimmed.contains("PRIVATE KEY")
    }

    fun generateKeyPair(keyType: String = "RSA", comment: String = "cloudssh@android"): GeneratedKeyPairResult {
        val jsch = JSch()
        val kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)

        val privBaos = ByteArrayOutputStream()
        kp.writePrivateKey(privBaos)
        val privateKeyPem = privBaos.toString(Charsets.UTF_8.name())

        val pubBaos = ByteArrayOutputStream()
        kp.writePublicKey(pubBaos, comment)
        val publicKeyOpenSsh = pubBaos.toString(Charsets.UTF_8.name()).trim()

        val fingerprint = try {
            kp.fingerPrint ?: computeFingerprint(publicKeyOpenSsh)
        } catch (e: Exception) {
            computeFingerprint(publicKeyOpenSsh)
        }

        kp.dispose()

        return GeneratedKeyPairResult(
            title = "Generated RSA (2048-bit) Key",
            publicKeyOpenSsh = publicKeyOpenSsh,
            privateKeyPem = privateKeyPem,
            fingerprint = fingerprint,
            keyType = "RSA"
        )
    }

    fun sanitizePrivateKey(rawKey: String): String {
        val normalized = rawKey.replace("\r\n", "\n").replace("\r", "\n").trim()
        return if (!normalized.endsWith("\n")) "$normalized\n" else normalized
    }

    fun tryConvertPkcs8ToPkcs1(pem: String): String? {
        return try {
            val clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.getDecoder().decode(clean)
            val kf = java.security.KeyFactory.getInstance("RSA")
            val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(keyBytes)) as? java.security.interfaces.RSAPrivateCrtKey
                ?: return null

            val rsaPriv = org.bouncycastle.asn1.pkcs.RSAPrivateKey(
                privKey.modulus,
                privKey.publicExponent,
                privKey.privateExponent,
                privKey.primeP,
                privKey.primeQ,
                privKey.primeExponentP,
                privKey.primeExponentQ,
                privKey.crtCoefficient
            )
            val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(rsaPriv.encoded)
            "-----BEGIN RSA PRIVATE KEY-----\n$encoded\n-----END RSA PRIVATE KEY-----\n"
        } catch (e: Exception) {
            null
        }
    }

    fun computeFingerprint(openSshPubKey: String): String {
        return try {
            val parts = openSshPubKey.trim().split(" ")
            if (parts.size >= 2) {
                val keyBytes = Base64.getDecoder().decode(parts[1])
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes)
                "SHA256:" + Base64.getEncoder().encodeToString(digest).trimEnd('=')
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}


