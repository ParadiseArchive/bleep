package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;

/** Client-only actions called from common code. Only ever run on the client, so this class never loads on a server. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openInfo(BleepEntity bleep) {
        McVersionClient.setScreen(new BleepScreen(bleep));
    }
}
