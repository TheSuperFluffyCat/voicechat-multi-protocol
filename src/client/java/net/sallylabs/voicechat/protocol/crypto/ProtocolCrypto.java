package net.sallylabs.voicechat.protocol.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ProtocolCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();

    private ProtocolCrypto() {}

    public static byte[] transform(int protocol, byte[] key, byte[] input, boolean encrypt)
            throws GeneralSecurityException {
        boolean legacy = protocol == 18;
        int ivSize = legacy ? 16 : 12;
        if (!encrypt && input.length < ivSize + 16)
            throw new GeneralSecurityException("Truncated Voice Chat ciphertext");
        byte[] iv = encrypt ? new byte[ivSize] : Arrays.copyOf(input, ivSize);
        if (encrypt) RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(legacy ? "AES/CBC/PKCS5Padding" : "AES/GCM/NoPadding");
        cipher.init(
                encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                legacy ? new IvParameterSpec(iv) : new GCMParameterSpec(128, iv));
        byte[] result =
                encrypt
                        ? cipher.doFinal(input)
                        : cipher.doFinal(input, ivSize, input.length - ivSize);
        if (!encrypt) return result;
        byte[] payload = Arrays.copyOf(iv, ivSize + result.length);
        System.arraycopy(result, 0, payload, ivSize, result.length);
        return payload;
    }
}
