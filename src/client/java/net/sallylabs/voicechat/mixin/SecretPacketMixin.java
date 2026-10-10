package net.sallylabs.voicechat.mixin;

import de.maxhenkel.voicechat.net.SecretPacket;
import de.maxhenkel.voicechat.voice.common.Secret;

import io.netty.buffer.ByteBuf;

import net.sallylabs.voicechat.VoiceChatMultiProtocol;
import net.sallylabs.voicechat.protocol.crypto.ProtocolSecrets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SecretPacket.class, remap = false)
public class SecretPacketMixin {
    @Redirect(
            method = "fromBytes",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lde/maxhenkel/voicechat/voice/common/Secret;fromBytes(Lio/netty/buffer/ByteBuf;)Lde/maxhenkel/voicechat/voice/common/Secret;"))
    private Secret multiProtocol$decodeSecret(ByteBuf buf) {
        if (!VoiceChatMultiProtocol.enabled) return Secret.fromBytes(buf);
        return ProtocolSecrets.decode(buf, VoiceChatMultiProtocol.wireProtocol);
    }
}
