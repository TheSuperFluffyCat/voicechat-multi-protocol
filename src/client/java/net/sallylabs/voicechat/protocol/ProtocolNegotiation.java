package net.sallylabs.voicechat.protocol;

import java.util.stream.IntStream;

public final class ProtocolNegotiation {
    private int[] protocols = {20, 18, 19};
    private int nativeProtocol = 20;
    private int index;
    private boolean active;
    private boolean accepted;
    private boolean rejected;
    private int ticks;
    private boolean retried;

    public void begin(int nativeProtocol) {
        this.nativeProtocol = nativeProtocol;
        protocols =
                IntStream.concat(IntStream.of(nativeProtocol), IntStream.of(20, 18, 19))
                        .distinct()
                        .toArray();
        index = 0;
        active = true;
        accepted = rejected = retried = false;
        ticks = 0;
    }

    public void reset() {
        active = false;
    }

    public int protocol() {
        return active ? protocols[index] : nativeProtocol;
    }

    public boolean accepted() {
        return accepted;
    }

    public void accept() {
        if (active) {
            accepted = true;
            rejected = false;
        }
    }

    public void reject() {
        if (active && !accepted) rejected = true;
    }

    /** Returns a protocol to send, or -1. Call on the Minecraft client thread. */
    public int tick() {
        if (!active || accepted) return -1;
        ticks++;
        if (rejected && ticks >= 20) {
            if (index + 1 == protocols.length) {
                active = false;
                return -1;
            }
            index++;
            ticks = 0;
            rejected = retried = false;
            return protocol();
        }
        // Retry the same format since sometimes it's like that dunno it works
        if (!rejected && !retried && ticks >= 100) {
            retried = true;
            return protocol();
        }
        return -1;
    }
}
