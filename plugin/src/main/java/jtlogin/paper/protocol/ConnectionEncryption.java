/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.paper.protocol;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Enables packet encryption on the server's network connection after a successful premium login handshake.
 * <p>
 * This is the only place in the Paper platform code that touches Minecraft server internals, and it is
 * deliberately independent of the runtime mappings:
 * <ul>
 *     <li>Paper before 26.1 reflected through Spigot-mapped names (via Paper's reflection remapper), Paper 26.1+
 *     has no remapper and exposes Mojang names. Looking methods up <i>by name</i> therefore breaks across the
 *     1.21.11 / 26.x boundary.</li>
 *     <li>So the setter is located by its <i>parameter types</i> ({@code (Cipher, Cipher)}, or the pre-1.16.4
 *     {@code (SecretKey)}), which are JDK types and do not change with the mappings.</li>
 *     <li>The ciphers are created here with the JDK instead of calling the server's own helper (which was found
 *     through the mapped name {@code MinecraftEncryption}/{@code Crypt} and the obfuscated method {@code a}).
 *     This is exactly what the server's helper does: AES/CFB8, with the key doubling as the IV.</li>
 * </ul>
 * If a future server version changes the shape of the connection class, {@link #resolve(Class)} fails with a clear
 * message instead of silently doing the wrong thing.
 */
public final class ConnectionEncryption {

    private final Method cipherSetter;
    private final Method secretKeySetter;

    private ConnectionEncryption(Method cipherSetter, Method secretKeySetter) {
        this.cipherSetter = cipherSetter;
        this.secretKeySetter = secretKeySetter;
    }

    /**
     * Locates the encryption setter on the connection class (searching superclasses too).
     *
     * @throws NoSuchMethodException if this server version exposes neither known shape
     */
    public static ConnectionEncryption resolve(Class<?> connectionClass) throws NoSuchMethodException {
        Method cipherSetter = null;
        Method secretKeySetter = null;

        for (Class<?> type = connectionClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) continue;
                var parameters = method.getParameterTypes();
                if (cipherSetter == null && parameters.length == 2 && parameters[0] == Cipher.class && parameters[1] == Cipher.class) {
                    cipherSetter = method;
                } else if (secretKeySetter == null && parameters.length == 1 && parameters[0] == SecretKey.class) {
                    secretKeySetter = method;
                }
            }
            if (cipherSetter != null) break; // the current shape wins over the legacy one
        }

        if (cipherSetter == null && secretKeySetter == null) {
            throw new NoSuchMethodException("No encryption setter (Cipher, Cipher) or (SecretKey) found on " + connectionClass.getName()
                    + ". This server version is not supported by this build of JTLogin.");
        }

        if (cipherSetter != null) cipherSetter.setAccessible(true);
        if (secretKeySetter != null) secretKeySetter.setAccessible(true);
        return new ConnectionEncryption(cipherSetter, cipherSetter != null ? null : secretKeySetter);
    }

    /**
     * Starts encryption on the given connection with the shared secret negotiated during the handshake.
     */
    public void enable(Object connection, SecretKey sharedSecret) throws ReflectiveOperationException {
        if (cipherSetter != null) {
            // Same order as the server: (decrypting cipher, encrypting cipher)
            cipherSetter.invoke(connection, newCipher(Cipher.DECRYPT_MODE, sharedSecret), newCipher(Cipher.ENCRYPT_MODE, sharedSecret));
        } else {
            secretKeySetter.invoke(connection, sharedSecret);
        }
    }

    /**
     * The Minecraft protocol cipher: AES/CFB8 where the shared secret is both key and IV.
     */
    public static Cipher newCipher(int mode, SecretKey sharedSecret) {
        try {
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(mode, sharedSecret, new IvParameterSpec(sharedSecret.getEncoded()));
            return cipher;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to create the protocol cipher", e);
        }
    }
}
