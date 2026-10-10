package net.sallylabs.voicechat.mixin;


import de.maxhenkel.voicechat.net.SecretPacket;
import de.maxhenkel.voicechat.voice.client.ClientManager;

import net.sallylabs.voicechat.VoiceChatMultiProtocol;
import net.sallylabs.voicechat.protocol.legacy.LegacyKeys;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientManager.class, remap = false)
public class ClientManagerMixin {
    @Inject(method = "authenticate", at = @At("HEAD"))
    private void multiProtocol$accept(SecretPacket packet, CallbackInfo ci) {
        VoiceChatMultiProtocol.accepted();
    }

    @Inject(method = "onJoinWorld", at = @At("HEAD"))
    private void multiProtocol$prepare(CallbackInfo ci) {
        VoiceChatMultiProtocol.prepareJoin();
    }

    @Inject(method = "onJoinWorld", at = @At("RETURN"))
    private void multiProtocol$begin(CallbackInfo ci) {
        VoiceChatMultiProtocol.begin();
    }

    @Inject(method = "onDisconnect", at = @At("RETURN"))
    private void multiProtocol$reset(CallbackInfo ci) {
        VoiceChatMultiProtocol.NEGOTIATION.reset();
        LegacyKeys.clear();
    }
}
