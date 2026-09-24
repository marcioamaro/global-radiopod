package com.example.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utilitário de Criptografia Segura AES-256-GCM para Backup de Preferências do MediaPod.
 * Novos backups usam senha pessoal; o segredo legado existe apenas para leitura de arquivos antigos.
 */
object BackupCryptoHelper {

    // 8-byte Magic Header: 'M', 'P', 'O', 'D', 'E', 'N', 'C', 0x01
    val MAGIC_HEADER = byteArrayOf(
        'M'.code.toByte(),
        'P'.code.toByte(),
        'O'.code.toByte(),
        'D'.code.toByte(),
        'E'.code.toByte(),
        'N'.code.toByte(),
        'C'.code.toByte(),
        0x01
    )

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12 // GCM standard IV size
    private const val TAG_LENGTH_BIT = 128
    private const val ITERATION_COUNT = 10_000
    private const val KEY_LENGTH_BIT = 256

    // Segredo mestre exclusivo do aplicativo para derivação simétrica robusta
    private val APP_SECRET = "MediaPod_RetroIpod_SecureBackup_AES256_GCM_2026_Key_V1".toCharArray()

    /**
     * Criptografa o JSON de backup gerando o payload binário:
     * [8B MAGIC] + [16B SALT] + [12B IV] + [CIPHERTEXT + 16B GCM AUTH TAG]
     */
    fun encryptBackupPayload(plainJson: String, password: CharArray): ByteArray {
        require(password.size >= 8) { "Use uma senha com pelo menos 8 caracteres" }
        val header = MAGIC_HEADER.copyOf().apply { this[lastIndex] = 0x02 }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_SIZE).apply { random.nextBytes(this) }

        val keySpec = PBEKeySpec(password, salt, 210_000, KEY_LENGTH_BIT)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        // Cabeçalho mágico incluído no cálculo da tag de autenticação
        cipher.updateAAD(header)

        val inputBytes = plainJson.toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(inputBytes)

        val output = ByteArray(MAGIC_HEADER.size + SALT_SIZE + IV_SIZE + encryptedBytes.size)
        System.arraycopy(header, 0, output, 0, header.size)
        System.arraycopy(salt, 0, output, MAGIC_HEADER.size, SALT_SIZE)
        System.arraycopy(iv, 0, output, MAGIC_HEADER.size + SALT_SIZE, IV_SIZE)
        System.arraycopy(encryptedBytes, 0, output, MAGIC_HEADER.size + SALT_SIZE + IV_SIZE, encryptedBytes.size)

        return output
    }

    /**
     * Descriptografa o payload binário validando o cabeçalho mágico e a integridade da tag GCM.
     * Retorna a String JSON ou lança SecurityException se o arquivo for texto claro, corrompido ou adulterado.
     */
    @Throws(Exception::class)
    fun decryptBackupPayload(encryptedPayload: ByteArray, password: CharArray? = null): String {
        val minSize = MAGIC_HEADER.size + SALT_SIZE + IV_SIZE + (TAG_LENGTH_BIT / 8)
        if (encryptedPayload.size < minSize) {
            throw SecurityException("Arquivo de backup inválido ou incompatível")
        }

        // Verifica o cabeçalho mágico
        for (i in 0 until MAGIC_HEADER.lastIndex) {
            if (encryptedPayload[i] != MAGIC_HEADER[i]) {
                // Arquivo não possui o cabeçalho criptografado do MediaPod (ex: JSON texto claro ou arquivo corrompido)
                throw SecurityException("Arquivo de backup inválido ou incompatível")
            }
        }

        val version = encryptedPayload[MAGIC_HEADER.lastIndex].toInt()
        require(version in 1..2) { "Versão de backup não suportada" }
        val header = encryptedPayload.copyOfRange(0, MAGIC_HEADER.size)
        val secret = if (version == 1) APP_SECRET else requireNotNull(password) { "Informe a senha do backup" }
        require(version == 1 || secret.isNotEmpty()) { "Informe a senha do backup" }
        val salt = ByteArray(SALT_SIZE)
        System.arraycopy(encryptedPayload, MAGIC_HEADER.size, salt, 0, SALT_SIZE)

        val iv = ByteArray(IV_SIZE)
        System.arraycopy(encryptedPayload, MAGIC_HEADER.size + SALT_SIZE, iv, 0, IV_SIZE)

        val cipherOffset = MAGIC_HEADER.size + SALT_SIZE + IV_SIZE
        val cipherLength = encryptedPayload.size - cipherOffset

        val keySpec = PBEKeySpec(secret, salt, if (version == 1) ITERATION_COUNT else 210_000, KEY_LENGTH_BIT)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        cipher.updateAAD(header)

        val decryptedBytes = cipher.doFinal(encryptedPayload, cipherOffset, cipherLength)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
