package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;

/** Client-only actions called from common code. Only ever run on the client, so this class never loads on a server. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openInfo(BleepEntity bleep) {
        McVersionClient.setScreen(new BleepScreen(bleep));
    }

    public static void openBleepedia(io.hookdev.bleep.network.BleepediaData data) {
        McVersionClient.setScreen(new BleepediaScreen(data));
    }

    public static void openCrafts(io.hookdev.bleep.network.CraftData data) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && level.getEntity(data.entityId()) instanceof io.hookdev.bleep.entity.BleepEntity bleep) McVersionClient.setScreen(new CraftScreen(bleep, data));
    }

    public static void forgetDisplay(int id) {
        DisplayTextures.forget(id);
    }
}
