package com.ai.engine.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * EncryptionService: AES-256-GCM encryption for sensitive data stored in the database.
 * 
 * GCM (Galois/Counter Mode) provides:
 * - Confidentiality (encrypted data can't be read)
 * - Integrity (detects tampering)
 * 
 * All credential values in tenant_credentials table are encrypted with this service.
 * On every request, TenantCredentialService decrypts them when needed.
 */
@Service
@Slf4j
public class EncryptionService {

    private final SecretKey secretKey;
    private static final int GCM_IV_LENGTH = 12;     // 96-bit IV for GCM
    private static final int GCM_TAG_LENGTH = 128;   // 128-bit authentication tag

    public EncryptionService(@Value("${encryption.key}") String keyString) {
        // Convert the key string (32 chars = 256 bits) to a SecretKey
        byte[] keyBytes = new byte[32];
        byte[] sourceBytes = keyString.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, 0, 32, "AES");
        log.debug("EncryptionService initialized with 256-bit AES key");
    }

    /**
     * Encrypt plaintext using AES-256-GCM.
     * 
     * @param plaintext the unencrypted value
     * @return Base64-encoded ciphertext with embedded IV
     */
    public String encrypt(String plaintext) {
        try {
            // Generate a random 96-bit IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // Initialize cipher in ENCRYPT mode
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Encrypt the plaintext
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext (IV needed for decryption)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            // Return as Base64 for safe storage in DB
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypt Base64-encoded ciphertext using AES-256-GCM.
     * 
     * @param ciphertext Base64-encoded combined IV + encrypted data
     * @return decrypted plaintext
     */
    public String decrypt(String ciphertext) {
        try {
            // Decode from Base64
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV (first 12 bytes) and ciphertext (remainder)
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            // Initialize cipher in DECRYPT mode
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Decrypt and return as string
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }
}
