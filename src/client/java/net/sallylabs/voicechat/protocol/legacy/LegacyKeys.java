package net.sallylabs.voicechat.protocol.legacy;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyKeys {
    // Preserve a protocol 19 key separately, keyed by that connection UUID token.
    private static final Map<UUID, byte[]> KEYS = new ConcurrentHashMap<>();

    private LegacyKeys() {}

    public static UUID decode(ByteBuf buffer, int protocol) {
        byte[] key = new byte[protocol == 19 ? 32 : 16];
        buffer.readBytes(key);
        ByteBuffer bytes = ByteBuffer.wrap(key);
        UUID token = new UUID(bytes.getLong(), bytes.getLong());
        KEYS.put(token, key);
        return token;
    }

    public static byte[] get(UUID token) {
        return KEYS.getOrDefault(
                token,
                ByteBuffer.allocate(16)
                        .putLong(token.getMostSignificantBits())
                        .putLong(token.getLeastSignificantBits())
                        .array());
    }

    public static void clear() {
        KEYS.clear();
    }
}
