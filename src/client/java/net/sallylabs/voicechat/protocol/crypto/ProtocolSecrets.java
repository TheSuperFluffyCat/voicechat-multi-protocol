package net.sallylabs.voicechat.protocol.crypto;

import de.maxhenkel.voicechat.voice.common.Secret;

import io.netty.buffer.ByteBuf;

public final class ProtocolSecrets {
    private ProtocolSecrets() {}

    public static Secret decode(ByteBuf buffer, int protocol) {
        if (protocol != 18 && protocol != 19 && protocol != 20) {
            throw new IllegalArgumentException("Unsupported Voice Chat protocol: " + protocol);
        }
        byte[] bytes = new byte[protocol == 19 ? 32 : 16];
        buffer.readBytes(bytes);
        return protocol == 18 ? new LegacySecret(bytes) : Secret.fromBytes(bytes);
    }
}
