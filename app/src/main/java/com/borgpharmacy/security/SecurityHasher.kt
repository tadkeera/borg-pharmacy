package com.borgpharmacy.security

import java.security.MessageDigest

object SecurityHasher {
    private const val APP_SALT = "borg-pharmacy-medical-representatives-2026"

    fun hashPasscode(passcode: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((APP_SALT + passcode.trim()).toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    fun verify(passcode: String, hash: String): Boolean = hashPasscode(passcode) == hash
}
