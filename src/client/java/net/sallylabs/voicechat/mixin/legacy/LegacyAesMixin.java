package net.sallylabs.voicechat.mixin.legacy;

import net.sallylabs.voicechat.VoiceChatMultiProtocol;
import net.sallylabs.voicechat.protocol.crypto.ProtocolCrypto;
import net.sallylabs.voicechat.protocol.legacy.LegacyKeys;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.security.GeneralSecurityException;
import java.util.UUID;

@Mixin(targets = "de.maxhenkel.voicechat.voice.common.AES", remap = false)
public class LegacyAesMixin {
    @Inject(method = "encrypt", at = @At("HEAD"), cancellable = true)
    private static void multiProtocol$encrypt(
            UUID secret, byte[] bytes, CallbackInfoReturnable<byte[]> ci)
            throws GeneralSecurityException {
        if (VoiceChatMultiProtocol.enabled && VoiceChatMultiProtocol.wireProtocol != 18)
            ci.setReturnValue(
                    ProtocolCrypto.transform(
                            VoiceChatMultiProtocol.wireProtocol,
                            LegacyKeys.get(secret),
                            bytes,
                            true));
    }

    @Inject(method = "decrypt", at = @At("HEAD"), cancellable = true)
    private static void multiProtocol$decrypt(
            UUID secret, byte[] bytes, CallbackInfoReturnable<byte[]> ci)
            throws GeneralSecurityException {
        if (VoiceChatMultiProtocol.enabled && VoiceChatMultiProtocol.wireProtocol != 18)
            ci.setReturnValue(
                    ProtocolCrypto.transform(
                            VoiceChatMultiProtocol.wireProtocol,
                            LegacyKeys.get(secret),
                            bytes,
                            false));
    }
}
