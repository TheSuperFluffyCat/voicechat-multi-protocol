package net.sallylabs.voicechat.protocol.crypto;

import de.maxhenkel.voicechat.voice.common.Secret;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

public final class LegacySecret extends Secret {
    private static final SecureRandom RANDOM = new SecureRandom();

    public LegacySecret(byte[] bytes) {
        super(bytes);
        if (bytes.length != 16)
            throw new IllegalArgumentException("Protocol 18 needs a 16-byte secret");
    }

    @Override
    public byte[] encrypt(byte[] data)
            throws NoSuchPaddingException,
                    NoSuchAlgorithmException,
                    InvalidAlgorithmParameterException,
                    InvalidKeyException,
                    IllegalBlockSizeException,
                    BadPaddingException {
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data);
        byte[] payload = Arrays.copyOf(iv, iv.length + encrypted.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return payload;
    }

    @Override
    public byte[] decrypt(byte[] payload)
            throws NoSuchPaddingException,
                    NoSuchAlgorithmException,
                    InvalidAlgorithmParameterException,
                    InvalidKeyException,
                    IllegalBlockSizeException,
                    BadPaddingException {
        if (payload.length < 32 || (payload.length - 16) % 16 != 0) {
            throw new IllegalBlockSizeException("Invalid protocol 18 payload size");
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, getKeySpec(), new IvParameterSpec(payload, 0, 16));
        return cipher.doFinal(payload, 16, payload.length - 16);
    }
}
