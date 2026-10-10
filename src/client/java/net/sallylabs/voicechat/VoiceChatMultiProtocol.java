package net.sallylabs.voicechat;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.intercompatibility.ClientCompatibilityManager;
import de.maxhenkel.voicechat.intercompatibility.CommonCompatibilityManager;
import de.maxhenkel.voicechat.net.ClientServerNetManager;
import de.maxhenkel.voicechat.net.NetManager;
import de.maxhenkel.voicechat.net.PlayerStatePacket;
import de.maxhenkel.voicechat.net.PlayerStatesPacket;
import de.maxhenkel.voicechat.net.RequestSecretPacket;

import net.fabricmc.api.ClientModInitializer;
import net.sallylabs.voicechat.compat.fabric.FabricCompatibility;
import net.sallylabs.voicechat.protocol.ProtocolNegotiation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoiceChatMultiProtocol implements ClientModInitializer {
    public static final ProtocolNegotiation NEGOTIATION = new ProtocolNegotiation();
    public static volatile int wireProtocol = 20;
    public static boolean enabled;
    private static int nativeProtocol = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger("ViaVoice");

    public static void begin() {
        if (!enabled) return;
        NEGOTIATION.begin(nativeProtocol);
        wireProtocol = nativeProtocol;
        LOGGER.info("Starting Voice Chat negotiation with native protocol {}", nativeProtocol);
    }

    public static void prepareJoin() {
        if (enabled) wireProtocol = nativeProtocol;
    }

    public static void accepted() {
        if (enabled && !NEGOTIATION.accepted()) {
            NEGOTIATION.accept();
            LOGGER.info(
                    "Server accepted Voice Chat protocol {}; starting UDP authentication",
                    wireProtocol);
        }
    }

    @Override
    public void onInitializeClient() {
        nativeProtocol = Voicechat.COMPATIBILITY_VERSION;
        wireProtocol = nativeProtocol;
        if (nativeProtocol < 18 || nativeProtocol > 20) {
            LOGGER.warn(
                    "Voice Chat protocol {} has no adapter; leaving native Voice Chat active",
                    nativeProtocol);
            return;
        }
        try {
            NetManager net = CommonCompatibilityManager.INSTANCE.getNetManager();
            FabricCompatibility.registerAlias(
                    nativeProtocol == 18 ? "state" : "player_state",
                    PlayerStatePacket.class,
                    net.playerStateChannel);
            FabricCompatibility.registerAlias(
                    nativeProtocol == 18 ? "states" : "player_states",
                    PlayerStatesPacket.class,
                    net.playerStatesChannel);
            FabricCompatibility.registerEvent(
                    "net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents",
                    "GAME",
                    args -> {
                        if (!(Boolean) args[1]
                                && args[0].toString()
                                        .contains("message.voicechat.incompatible_version")) {
                            LOGGER.info("Server rejected Voice Chat protocol {}", wireProtocol);
                            NEGOTIATION.reject();
                        }
                    });
            ClientCompatibilityManager.INSTANCE.onClientTick(
                    () -> {
                        if (!enabled) return;
                        int protocol = NEGOTIATION.tick();
                        if (protocol < 0) return;
                        wireProtocol = protocol;
                        LOGGER.info("Requesting Voice Chat handshake with protocol {}", protocol);
                        ClientServerNetManager.sendToServer(new RequestSecretPacket(protocol));
                    });
            enabled = true;
            LOGGER.info(
                    "Voice Chat adapters ready: native protocol {}, Minecraft-independent runtime"
                            + " bridge",
                    nativeProtocol);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LOGGER.warn(
                    "Voice Chat adapters could not initialize; leaving native Voice Chat active",
                    failure);
        }
    }
}
