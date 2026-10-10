package net.sallylabs.voicechat.mixin.legacy;

import de.maxhenkel.voicechat.config.ServerConfig;

import io.netty.buffer.ByteBuf;

import net.sallylabs.voicechat.VoiceChatMultiProtocol;
import net.sallylabs.voicechat.protocol.legacy.LegacyKeys;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(targets = "de.maxhenkel.voicechat.net.SecretPacket", remap = false)
public class LegacySecretPacketMixin {
    @Shadow private UUID secret;
    @Shadow private int serverPort;
    @Shadow private UUID playerUUID;
    @Shadow private ServerConfig.Codec codec;
    @Shadow private int mtuSize;
    @Shadow private double voiceChatDistance;
    @Shadow private int keepAlive;
    @Shadow private boolean groupsEnabled;
    @Shadow private String voiceHost;
    @Shadow private boolean allowRecording;

    @Inject(method = "fromBytes", at = @At("HEAD"), cancellable = true)
    private void multiProtocol$decode(@Coerce Object input, CallbackInfoReturnable<Object> ci) {
        if (!VoiceChatMultiProtocol.enabled) return;
        ByteBuf buffer = (ByteBuf) input;
        secret = LegacyKeys.decode(buffer, VoiceChatMultiProtocol.wireProtocol);
        serverPort = buffer.readInt();
        playerUUID = new UUID(buffer.readLong(), buffer.readLong());
        codec = ServerConfig.Codec.values()[buffer.readByte()];
        mtuSize = buffer.readInt();
        voiceChatDistance = buffer.readDouble();
        keepAlive = buffer.readInt();
        groupsEnabled = buffer.readBoolean();
        int size = 0;
        for (int shift = 0; ; shift += 7) {
            if (shift >= 35) throw new IllegalArgumentException("Invalid voice host length");
            int value = buffer.readUnsignedByte();
            size |= (value & 127) << shift;
            if ((value & 128) == 0) break;
        }
        if (size < 0 || size > 32767 * 3 || size > buffer.readableBytes())
            throw new IllegalArgumentException("Invalid voice host size");
        voiceHost = buffer.readCharSequence(size, StandardCharsets.UTF_8).toString();
        if (voiceHost.length() > 32767) throw new IllegalArgumentException("Voice host too long");
        allowRecording = buffer.readBoolean();
        ci.setReturnValue(this);
    }
}
