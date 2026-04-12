package com.alvinliu.dbmcp.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

final class ConnectionCrypto {

    static final String ENC_PREFIX = "enc:v1:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private ConnectionCrypto() {}

    static String encrypt(String plain) throws Exception {
        byte[] key = connectionKey();
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return ENC_PREFIX + Base64.getEncoder().encodeToString(payload);
    }

    static String decrypt(String value) throws Exception {
        String raw = value.substring(ENC_PREFIX.length());
        byte[] payload = Base64.getDecoder().decode(raw);
        if (payload.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted value is too short");
        }

        byte[] key = connectionKey();
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
        byte[] ciphertext = new byte[payload.length - GCM_IV_LENGTH];
        System.arraycopy(payload, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }

    static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }

    private static byte[] connectionKey() {
        String seed = "T3JhY2xlTmV3OTchQCMkJQ==";
        return Base64.getDecoder().decode(seed);
    }
}
