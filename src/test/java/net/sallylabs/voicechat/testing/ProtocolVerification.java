package net.sallylabs.voicechat.testing;

import de.maxhenkel.voicechat.voice.common.Secret;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.sallylabs.voicechat.protocol.ProtocolNegotiation;
import net.sallylabs.voicechat.protocol.crypto.ProtocolSecrets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class ProtocolVerification {
    public static void main(String[] args) throws Exception {
        for (int protocol : new int[] {18, 19, 20}) verifyWireFormat(protocol);
        verifyNegotiation();
        System.out.println(
                "Verified protocols 18/19/20: peer encryption, secret framing, negotiation and"
                        + " disconnect reset");
    }

    private static void verifyWireFormat(int protocol) throws Exception {
        byte[] key = new byte[protocol == 19 ? 32 : 16];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeBytes(key).writeInt(24454);
            Secret secret = ProtocolSecrets.decode(buffer, protocol);
            require(
                    buffer.readInt() == 24454 && !buffer.isReadable(),
                    "Secret framing " + protocol);
            require(Arrays.equals(secret.getSecret(), key), "Secret key " + protocol);
            ByteBuf encoded = Unpooled.buffer();
            try {
                secret.toBytes(encoded);
                byte[] actual = new byte[encoded.readableBytes()];
                encoded.readBytes(actual);
                require(
                        Arrays.equals(actual, key),
                        "Authentication secret serialization " + protocol);
            } finally {
                encoded.release();
            }

            for (int length : new int[] {0, 1, 15, 16, 17, 127, 1024}) {
                byte[] plain = new byte[length];
                Arrays.fill(plain, (byte) 0x5A);
                byte[] encrypted = secret.encrypt(plain);
                int ivLength = protocol == 18 ? 16 : 12;
                Cipher peer =
                        Cipher.getInstance(
                                protocol == 18 ? "AES/CBC/PKCS5Padding" : "AES/GCM/NoPadding");
                byte[] iv = Arrays.copyOf(encrypted, ivLength);
                peer.init(
                        Cipher.DECRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        protocol == 18 ? new IvParameterSpec(iv) : new GCMParameterSpec(128, iv));
                require(
                        Arrays.equals(
                                plain,
                                peer.doFinal(encrypted, ivLength, encrypted.length - ivLength)),
                        "Peer decrypt " + protocol);
                Arrays.fill(iv, (byte) 0x31);
                peer.init(
                        Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        protocol == 18 ? new IvParameterSpec(iv) : new GCMParameterSpec(128, iv));
                byte[] body = peer.doFinal(plain);
                byte[] incoming = Arrays.copyOf(iv, iv.length + body.length);
                System.arraycopy(body, 0, incoming, iv.length, body.length);
                require(Arrays.equals(plain, secret.decrypt(incoming)), "Peer encrypt " + protocol);
            }
        } finally {
            buffer.release();
        }
        ByteBuf truncated = Unpooled.wrappedBuffer(new byte[key.length - 1]);
        try {
            try {
                ProtocolSecrets.decode(truncated, protocol);
                throw new AssertionError("Accepted truncated secret");
            } catch (IndexOutOfBoundsException expected) {
            }
        } finally {
            truncated.release();
        }
    }

    private static void verifyNegotiation() {
        ProtocolNegotiation state = new ProtocolNegotiation();
        state.begin(20);
        for (int i = 0; i < 99; i++) require(state.tick() == -1, "Premature retry");
        require(state.tick() == 20, "Retry native protocol on silence");
        for (int i = 0; i < 300; i++)
            require(state.tick() == -1, "Silence must not trigger incompatible formats");
        state.reject();
        require(state.tick() == 18, "Fallback to 18");
        state.reject();
        for (int i = 0; i < 19; i++) require(state.tick() == -1, "Throttle fallback");
        require(state.tick() == 19, "Fallback to 19");
        state.accept();
        state.reject();
        for (int i = 0; i < 200; i++)
            require(state.tick() == -1, "Stop after secret; ignore late rejection");
        state.reset();
        require(state.tick() == -1 && state.protocol() == 20, "Disconnect reset");
        state.begin(20);
        require(!state.accepted() && state.protocol() == 20, "Server switch reset");
        for (int i = 0; i < 3; i++) {
            state.reject();
            for (int j = 0; j < 20; j++) state.tick();
        }
        for (int i = 0; i < 200; i++) require(state.tick() == -1, "Bounded fallback");
        for (int nativeProtocol : new int[] {18, 19, 20}) {
            state.begin(nativeProtocol);
            require(state.protocol() == nativeProtocol, "Preserve installed client's protocol");
            Set<Integer> attempts = new HashSet<>();
            attempts.add(state.protocol());
            for (int i = 0; i < 2; i++) {
                state.reject();
                int next = -1;
                for (int j = 0; j < 20; j++) {
                    int candidate = state.tick();
                    if (candidate >= 0) next = candidate;
                }
                require(next >= 0 && attempts.add(next), "Distinct native-first fallback");
            }
            require(attempts.equals(Set.of(18, 19, 20)), "All supported adapters considered");
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
