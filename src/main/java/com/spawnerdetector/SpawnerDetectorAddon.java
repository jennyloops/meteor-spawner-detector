package com.spawnerdetector;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SpawnerDetectorAddon extends MeteorAddon {

    @Override
    public void onInitialize() {
        Modules.get().add(new SpawnerDetector());
    }

    @Override
    public String getPackage() {
        return "com.spawnerdetector";
    }
}
