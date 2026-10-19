/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.paper.protocol;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionEncryptionTest {

    private static final byte[] KEY_BYTES = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");
    private static final SecretKey KEY = new SecretKeySpec(KEY_BYTES, "AES");

    // --- stand-ins for the server's connection class under different runtime mappings -------------------------

    /** Mojang-mapped shape (Paper 26.1+). */
    static class MojangConnection {
        Cipher decrypt, encrypt;

        public void setEncryptionKey(Cipher decryptingCipher, Cipher encryptingCipher) {
            this.decrypt = decryptingCipher;
            this.encrypt = encryptingCipher;
        }
    }

    /** Obfuscated/Spigot-mapped shape: same parameter types, unrelated method name. */
    static class ObfuscatedConnection {
        Cipher decrypt, encrypt;

        void a(Cipher first, Cipher second) {
            this.decrypt = first;
            this.encrypt = second;
        }

        // unrelated methods that must not be mistaken for the setter
        void b(Cipher onlyOne) {
        }

        Cipher c(Cipher first, Cipher second) {
            return first;
        }

        static void d(Cipher first, Cipher second) {
        }
    }

    /** The setter lives on a superclass. */
    static class InheritedConnection extends ObfuscatedConnection {
    }

    /** Pre-1.16.4 shape. */
    static class LegacyConnection {
        SecretKey key;

        public void setupEncryption(SecretKey secretKey) {
            this.key = secretKey;
        }
    }

    /** Unsupported shape. */
    static class UnsupportedConnection {
        public void somethingElse(String value) {
        }
    }

    // --- tests ------------------------------------------------------------------------------------------------

    @Test
    void cipherMatchesAnIndependentAesCfb8ImplementationWhereKeyIsAlsoTheIv() throws Exception {
        // Expected value produced with: openssl enc -aes-128-cfb8 -K <key> -iv <key>
        var expected = HexFormat.of().parseHex("42ea5ed4daf864ed3af7848ecd8cebda91594a5e98fc8db2");
        var plaintext = "Hello, JTLogin protocol!".getBytes(StandardCharsets.UTF_8);

        var encrypted = ConnectionEncryption.newCipher(Cipher.ENCRYPT_MODE, KEY).doFinal(plaintext);

        assertArrayEquals(expected, encrypted);
        assertArrayEquals(plaintext, ConnectionEncryption.newCipher(Cipher.DECRYPT_MODE, KEY).doFinal(expected));
    }

    @Test
    void cfb8IsAStreamCipherSoPacketsCanBeProcessedInArbitraryChunks() throws Exception {
        var expected = HexFormat.of().parseHex("42ea5ed4daf864ed3af7848ecd8cebda91594a5e98fc8db2");
        var plaintext = "Hello, JTLogin protocol!".getBytes(StandardCharsets.UTF_8);
        var cipher = ConnectionEncryption.newCipher(Cipher.ENCRYPT_MODE, KEY);

        var chunked = new byte[plaintext.length];
        int offset = 0;
        for (int size : new int[]{1, 5, 7, 11}) {
            offset += cipher.update(plaintext, offset, size, chunked, offset);
        }
        assertEquals(plaintext.length, offset);
        assertArrayEquals(expected, chunked);
    }

    @Test
    void resolvesMojangMappedSetter() throws Exception {
        var connection = new MojangConnection();
        ConnectionEncryption.resolve(MojangConnection.class).enable(connection, KEY);
        assertRoundTrip(connection.decrypt, connection.encrypt);
    }

    @Test
    void resolvesObfuscatedSetterByParameterTypesNotByName() throws Exception {
        var connection = new ObfuscatedConnection();
        ConnectionEncryption.resolve(ObfuscatedConnection.class).enable(connection, KEY);
        assertRoundTrip(connection.decrypt, connection.encrypt);
    }

    @Test
    void resolvesSetterDeclaredOnASuperclass() throws Exception {
        var connection = new InheritedConnection();
        ConnectionEncryption.resolve(InheritedConnection.class).enable(connection, KEY);
        assertRoundTrip(connection.decrypt, connection.encrypt);
    }

    @Test
    void decryptingCipherIsPassedFirstLikeTheServerExpects() throws Exception {
        var connection = new MojangConnection();
        ConnectionEncryption.resolve(MojangConnection.class).enable(connection, KEY);
        assertEquals(Cipher.getInstance("AES/CFB8/NoPadding").getAlgorithm(), connection.decrypt.getAlgorithm());

        var plaintext = "abc".getBytes(StandardCharsets.UTF_8);
        var ciphertext = connection.encrypt.doFinal(plaintext);
        // a fresh decrypting cipher must be able to read what the "encrypt" slot produced, and vice versa
        assertArrayEquals(plaintext, ConnectionEncryption.newCipher(Cipher.DECRYPT_MODE, KEY).doFinal(ciphertext));
        assertArrayEquals(plaintext, connection.decrypt.doFinal(ConnectionEncryption.newCipher(Cipher.ENCRYPT_MODE, KEY).doFinal(plaintext)));
    }

    @Test
    void fallsBackToTheLegacySecretKeySetter() throws Exception {
        var connection = new LegacyConnection();
        ConnectionEncryption.resolve(LegacyConnection.class).enable(connection, KEY);
        assertSame(KEY, connection.key);
    }

    @Test
    void failsLoudlyOnAnUnsupportedShape() {
        var e = assertThrows(NoSuchMethodException.class, () -> ConnectionEncryption.resolve(UnsupportedConnection.class));
        assertTrue(e.getMessage().contains("UnsupportedConnection"));
        assertTrue(e.getMessage().contains("not supported"));
    }

    private static void assertRoundTrip(Cipher decrypt, Cipher encrypt) throws Exception {
        assertNotNull(decrypt);
        assertNotNull(encrypt);
        var plaintext = "round trip".getBytes(StandardCharsets.UTF_8);
        var ciphertext = encrypt.doFinal(plaintext);
        assertFalse(java.util.Arrays.equals(plaintext, ciphertext));
        assertArrayEquals(plaintext, ConnectionEncryption.newCipher(Cipher.DECRYPT_MODE, KEY).doFinal(ciphertext));
        assertArrayEquals(plaintext, decrypt.doFinal(ConnectionEncryption.newCipher(Cipher.ENCRYPT_MODE, KEY).doFinal(plaintext)));
    }
}
