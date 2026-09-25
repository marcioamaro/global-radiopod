package com.marcioamaro.mediapod.util

import org.junit.Assert.*
import org.junit.Test

class BackupPasswordTest {
    @Test fun newBackupIsEncryptedAndRestoresWithoutPassword() {
        val json = "{\"version\":30,\"streamUrl\":\"https://private.example/live\"}"
        val encrypted = BackupCryptoHelper.encryptBackupPayload(json)
        assertFalse(String(encrypted, Charsets.UTF_8).contains("private.example"))
        assertEquals(json, BackupCryptoHelper.decryptBackupPayload(encrypted))
        assertFalse(BackupCryptoHelper.requiresPassword(encrypted))
    }

    @Test fun personalPasswordAuthenticatesAndWrongPasswordCannotDecrypt() {
        val key = "correct-password".toCharArray()
        val first = BackupCryptoHelper.encryptBackupPayload("{\"version\":29}", key)
        val second = BackupCryptoHelper.encryptBackupPayload("{\"version\":29}", key)
        assertFalse(first.contentEquals(second))
        assertEquals("{\"version\":29}", BackupCryptoHelper.decryptBackupPayload(first, key))
        assertTrue(runCatching { BackupCryptoHelper.decryptBackupPayload(first, "wrong-password".toCharArray()) }.isFailure)
        first[first.lastIndex] = (first.last().toInt() xor 1).toByte()
        assertTrue(runCatching { BackupCryptoHelper.decryptBackupPayload(first, key) }.isFailure)
    }

    @Test fun legacyBackupRemainsReadableWithoutPersonalPassword() {
        val data = javaClass.classLoader!!.getResourceAsStream("legacy-backup.enc")!!.use { it.readBytes() }
        assertEquals("{\"version\":28,\"app\":\"MediaPod\"}", BackupCryptoHelper.decryptBackupPayload(data))
    }
}
