package com.kideyeguard

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseManager {
    private const val SECRET_SALT = "KEG-WIN-2026-EYEGUARD-SALT-V1"
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun computeChecksum(body8: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET_SALT.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(body8.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02X".format(it) }
        return hex.substring(0, 4)
    }

    fun verifyActivationCode(code: String?): Boolean {
        val cleaned = code.orEmpty().trim().uppercase()
        val parts = cleaned.split("-")
        if (parts.size != 4 || parts[0] != "KEG") return false
        val p1 = parts[1]
        val p2 = parts[2]
        val chk = parts[3]
        if (p1.length != 4 || p2.length != 4 || chk.length != 4) return false
        val body = p1 + p2
        if (body.any { it !in ALPHABET }) return false
        val expected = computeChecksum(body).toByteArray(Charsets.UTF_8)
        val actual = chk.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expected, actual)
    }
}
