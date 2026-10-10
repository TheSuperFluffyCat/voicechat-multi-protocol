package net.sallylabs.voicechat.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class CompatibilityMixinPlugin implements IMixinConfigPlugin {
    private boolean modern;
    private boolean supported;

    @Override
    public void onLoad(String mixinPackage) {
        modern =
                getClass()
                                .getClassLoader()
                                .getResource("de/maxhenkel/voicechat/voice/common/Secret.class")
                        != null;
        try {
            Class<?> constants =
                    Class.forName(
                            "de.maxhenkel.voicechat.BuildConstants",
                            true,
                            getClass().getClassLoader());
            int protocol = constants.getField("COMPATIBILITY_VERSION").getInt(null);
            supported = protocol >= 18 && protocol <= 20;
        } catch (ReflectiveOperationException | LinkageError failure) {
            supported = false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!supported) return false;
        if (mixin.endsWith(".SecretPacketMixin")) return modern;
        if (mixin.endsWith(".LegacySecretPacketMixin")
                || mixin.endsWith(".LegacyAesMixin")
                || mixin.endsWith(".LegacyAuthenticateMixin")) return !modern;
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
