package net.sallylabs.voicechat.mixin.legacy;

import io.netty.buffer.ByteBuf;

import net.sallylabs.voicechat.VoiceChatMultiProtocol;
import net.sallylabs.voicechat.protocol.legacy.LegacyKeys;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(targets = "de.maxhenkel.voicechat.voice.common.AuthenticatePacket", remap = false)
public class LegacyAuthenticateMixin {
    @Shadow private UUID playerUUID;
    @Shadow private UUID secret;

    @Inject(method = "toBytes", at = @At("HEAD"), cancellable = true)
    private void multiProtocol$encode(@Coerce Object output, CallbackInfo ci) {
        if (!VoiceChatMultiProtocol.enabled || VoiceChatMultiProtocol.wireProtocol != 19) return;
        ByteBuf buffer = (ByteBuf) output;
        buffer.writeLong(playerUUID.getMostSignificantBits())
                .writeLong(playerUUID.getLeastSignificantBits());
        buffer.writeBytes(LegacyKeys.get(secret));
        ci.cancel();
    }
}
