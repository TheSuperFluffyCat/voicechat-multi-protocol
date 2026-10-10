package net.sallylabs.voicechat.testing;

import de.maxhenkel.voicechat.net.SecretPacket;
import de.maxhenkel.voicechat.voice.client.ClientManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.api.ClientModInitializer;
import net.sallylabs.voicechat.VoiceChatMultiProtocol;
import net.sallylabs.voicechat.compat.fabric.FabricCompatibility;
import net.sallylabs.voicechat.protocol.crypto.ProtocolCrypto;
import net.sallylabs.voicechat.protocol.legacy.LegacyKeys;

import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

public final class FabricProtocolSmokeTest implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            FabricCompatibility.registerEvent(
                    "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents",
                    "CLIENT_STARTED",
                    args -> {
                        try {
                            MixinEnvironment.getCurrentEnvironment().audit();
                            ClientManager.instance();
                            if (!VoiceChatMultiProtocol.enabled)
                                throw new AssertionError("Runtime bridge disabled");
                            boolean legacy =
                                    SecretPacket.class.getMethod("getSecret").getReturnType()
                                            == UUID.class;
                            Method decode =
                                    FabricCompatibility.packetMethod(
                                            SecretPacket.class, "fromBytes");
                            for (int protocol : new int[] {18, 19, 20}) {
                                VoiceChatMultiProtocol.wireProtocol = protocol;
                                byte[] key = new byte[protocol == 19 ? 32 : 16];
                                for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
                                ByteBuf buffer =
                                        (ByteBuf)
                                                decode.getParameterTypes()[0]
                                                        .getConstructor(ByteBuf.class)
                                                        .newInstance(Unpooled.buffer());
                                try {
                                    UUID player = new UUID(42, 123);
                                    buffer.writeBytes(key);
                                    buffer.writeInt(24454);
                                    buffer.writeLong(player.getMostSignificantBits())
                                            .writeLong(player.getLeastSignificantBits());
                                    buffer.writeByte(0);
                                    buffer.writeInt(1024);
                                    buffer.writeDouble(48);
                                    buffer.writeInt(1000);
                                    buffer.writeBoolean(true);
                                    buffer.writeByte(9)
                                            .writeBytes(
                                                    "localhost"
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8));
                                    buffer.writeBoolean(false);
                                    Object packet = decode.invoke(new SecretPacket(), buffer);
                                    Object secret =
                                            SecretPacket.class
                                                    .getMethod("getSecret")
                                                    .invoke(packet);
                                    byte[] actualKey =
                                            legacy
                                                    ? LegacyKeys.get((UUID) secret)
                                                    : (byte[])
                                                            secret.getClass()
                                                                    .getMethod("getSecret")
                                                                    .invoke(secret);
                                    if (buffer.isReadable()
                                            || (int)
                                                            SecretPacket.class
                                                                    .getMethod("getServerPort")
                                                                    .invoke(packet)
                                                    != 24454
                                            || !SecretPacket.class
                                                    .getMethod("getPlayerUUID")
                                                    .invoke(packet)
                                                    .equals(player)
                                            || !Arrays.equals(actualKey, key)
                                            || (!legacy
                                                    && secret.getClass()
                                                                    .getName()
                                                                    .endsWith("LegacySecret")
                                                            != (protocol == 18))) {
                                        throw new AssertionError(
                                                "Fabric secret decoder fixture failed for protocol "
                                                        + protocol);
                                    }
                                    verifyAuthenticationAndCrypto(
                                            legacy,
                                            protocol,
                                            player,
                                            secret,
                                            key,
                                            decode.getParameterTypes()[0]);
                                } finally {
                                    buffer.release();
                                }
                            }
                            VoiceChatMultiProtocol.wireProtocol = 20;
                            if (!legacy) ProtocolVerification.main(new String[0]);
                            System.out.println(
                                    "FABRIC_PROTOCOL_SMOKE_TEST_PASSED: all mixins audited; actual"
                                            + " SecretPacket decoder verified for 18/19/20");
                            System.exit(0);
                        } catch (Throwable failure) {
                            throw new RuntimeException(
                                    "Fabric protocol smoke test failed", failure);
                        }
                    });
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void verifyAuthenticationAndCrypto(
            boolean legacy,
            int protocol,
            UUID player,
            Object secret,
            byte[] key,
            Class<?> bufferClass)
            throws Exception {
        Class<?> auth = Class.forName("de.maxhenkel.voicechat.voice.common.AuthenticatePacket");
        Constructor<?> constructor =
                Arrays.stream(auth.getConstructors())
                        .filter(c -> c.getParameterCount() == 2)
                        .findFirst()
                        .orElseThrow();
        Object packet = constructor.newInstance(player, secret);
        ByteBuf output =
                (ByteBuf) bufferClass.getConstructor(ByteBuf.class).newInstance(Unpooled.buffer());
        try {
            FabricCompatibility.packetMethod(auth, "toBytes").invoke(packet, output);
            if (!new UUID(output.readLong(), output.readLong()).equals(player))
                throw new AssertionError("Auth player UUID");
            byte[] actualKey = new byte[output.readableBytes()];
            output.readBytes(actualKey);
            if (!Arrays.equals(actualKey, key))
                throw new AssertionError("Auth key serialization " + protocol);
        } finally {
            output.release();
        }
        byte[] plaintext = new byte[] {1, 2, 3, 4, 5};
        byte[] encrypted;
        if (legacy)
            encrypted =
                    (byte[])
                            Class.forName("de.maxhenkel.voicechat.voice.common.AES")
                                    .getMethod("encrypt", UUID.class, byte[].class)
                                    .invoke(null, secret, plaintext);
        else
            encrypted =
                    (byte[])
                            secret.getClass()
                                    .getMethod("encrypt", byte[].class)
                                    .invoke(secret, plaintext);
        byte[] decoded = ProtocolCrypto.transform(protocol, key, encrypted, false);
        if (!Arrays.equals(plaintext, decoded))
            throw new AssertionError("Outgoing encryption " + protocol);
        byte[] incoming = ProtocolCrypto.transform(protocol, key, plaintext, true);
        if (legacy)
            decoded =
                    (byte[])
                            Class.forName("de.maxhenkel.voicechat.voice.common.AES")
                                    .getMethod("decrypt", UUID.class, byte[].class)
                                    .invoke(null, secret, incoming);
        else
            decoded =
                    (byte[])
                            secret.getClass()
                                    .getMethod("decrypt", byte[].class)
                                    .invoke(secret, incoming);
        if (!Arrays.equals(plaintext, decoded))
            throw new AssertionError("Incoming encryption " + protocol);
    }
}
