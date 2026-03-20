package com.thebestlogin.util;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class HashingUtil {
    public static final int LEGACY_HASH_VERSION = 1;
    public static final int CURRENT_HASH_VERSION = 2;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_BITS = 256;
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private HashingUtil() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }

    public static String derivePasswordHash(int hashVersion, String nickname, String salt, String password) {
        return switch (hashVersion) {
            case LEGACY_HASH_VERSION -> sha256Hex(password);
            case CURRENT_HASH_VERSION -> pbkdf2Hex(password, normalizeNickname(nickname) + ':' + salt);
            default -> throw new IllegalArgumentException("Unsupported password hash version: " + hashVersion);
        };
    }

    public static String deriveStoredClientHash(String serverId, String passwordHash) {
        return sha256Hex(serverId + passwordHash);
    }

    public static String deriveProof(String serverId, String nickname, String storedHash, String challenge) {
        return sha256Hex(serverId + normalizeNickname(nickname) + storedHash + challenge);
    }

    public static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HEX.formatHex(buffer);
    }

    public static String normalizeNickname(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            boolean digit = current >= '0' && current <= '9';
            boolean lowerHex = current >= 'a' && current <= 'f';
            if (!digit && !lowerHex) {
                return false;
            }
        }
        return true;
    }

    public static KeyPair generateTransportKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing RSA support", exception);
        }
    }

    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static PublicKey decodePublicKey(String publicKeyBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to decode public key", exception);
        }
    }

    public static PrivateKey decodePrivateKey(String privateKeyBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(privateKeyBase64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to decode private key", exception);
        }
    }

    public static String encryptForServer(String publicKeyBase64, String value) {
        try {
            Cipher cipher = createTransportCipher();
            cipher.init(Cipher.ENCRYPT_MODE, decodePublicKey(publicKeyBase64), OAEP_SHA256);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt payload", exception);
        }
    }

    public static String decryptFromClient(PrivateKey privateKey, String value) {
        try {
            Cipher cipher = createTransportCipher();
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
            byte[] decoded = Base64.getDecoder().decode(value);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to decrypt payload", exception);
        }
    }

    private static String pbkdf2Hex(String password, String salt) {
        char[] passwordChars = password.toCharArray();
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(passwordChars, salt.getBytes(StandardCharsets.UTF_8), PBKDF2_ITERATIONS, PBKDF2_BITS);
            return HEX.formatHex(factory.generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Missing PBKDF2WithHmacSHA256 support", exception);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static Cipher createTransportCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    }
}