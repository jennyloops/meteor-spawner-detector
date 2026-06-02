package com.spawnerdetector;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SpawnerDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgBeam = settings.createGroup("Beam");

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Chunks around player to scan.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 12)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Only detect spawners at or below this Y.")
        .defaultValue(60)
        .range(-64, 320)
        .sliderRange(-64, 120)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to scan (ticks).")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> renderChunk = sgRender.add(new BoolSetting.Builder()
        .name("render-chunk")
        .description("Render chunk box where a spawner was found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderBeam = sgRender.add(new BoolSetting.Builder()
        .name("render-beam")
        .description("Render vertical blue beam from spawner to world top.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> beamWidth = sgBeam.add(new IntSetting.Builder()
        .name("beam-width")
        .description("Width of the beam (percentage).")
        .defaultValue(25)
        .min(5)
        .max(100)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Boolean> beamPulse = sgBeam.add(new BoolSetting.Builder()
        .name("pulse-beam")
        .description("Make beam pulse/animate.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> pulseSpeed = sgBeam.add(new IntSetting.Builder()
        .name("pulse-speed")
        .description("Speed of beam pulsing.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<SettingColor> chunkSideColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-side-color")
        .defaultValue(new SettingColor(30, 120, 255, 20))
        .build()
    );

    private final Setting<SettingColor> chunkLineColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-line-color")
        .defaultValue(new SettingColor(30, 120, 255, 180))
        .build()
    );

    private final Setting<SettingColor> beamColor = sgBeam.add(new ColorSetting.Builder()
        .name("beam-color")
        .defaultValue(new SettingColor(30, 120, 255, 120))
        .build()
    );

    private final Map<ChunkPos, Set<BlockPos>> found = new HashMap<>();
    private int ticks = 0;

    public SpawnerDetector() {
        super(Categories.World, "spawner-detector", "Detects underground mob spawners and marks chunk + sky beam.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        found.clear();
        scan();
    }

    @Override
    public void onDeactivate() {
        found.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        ticks++;
        if (ticks >= scanInterval.get()) {
            ticks = 0;
            scan();
        }
    }

    private void scan() {
        if (mc.world == null || mc.player == null) return;
        found.clear();

        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());
        int radius = chunkRadius.get();
        int yCap = maxY.get();

        for (BlockEntity be : mc.world.iterateBlockEntities()) {
            // Only detect MobSpawnerBlockEntity, skip TrialSpawnerBlockEntity
            if (!(be instanceof MobSpawnerBlockEntity)) continue;
            if (be instanceof TrialSpawnerBlockEntity) continue;

            BlockPos pos = be.getPos();
            if (pos.getY() > yCap) continue;

            ChunkPos cp = new ChunkPos(pos);
            if (Math.abs(cp.x - playerChunk.x) > radius || Math.abs(cp.z - playerChunk.z) > radius) continue;

            found.computeIfAbsent(cp, k -> new HashSet<>()).add(pos.toImmutable());
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || found.isEmpty()) return;

        int bottomY = mc.world.getBottomY();
        int topY = mc.world.getTopY();

        for (Map.Entry<ChunkPos, Set<BlockPos>> entry : found.entrySet()) {
            ChunkPos cp = entry.getKey();

            double minX = cp.getStartX();
            double minZ = cp.getStartZ();
            double maxX = cp.getEndX() + 1;
            double maxZ = cp.getEndZ() + 1;

            if (renderChunk.get()) {
                event.renderer.box(
                    minX, bottomY, minZ,
                    maxX, topY, maxZ,
                    chunkSideColor.get(),
                    chunkLineColor.get(),
                    ShapeMode.Both,
                    0
                );
            }

            if (renderBeam.get()) {
                for (BlockPos spawnerPos : entry.getValue()) {
                    double cx = spawnerPos.getX() + 0.5;
                    double cz = spawnerPos.getZ() + 0.5;
                    
                    float widthFactor = beamWidth.get() / 100f;
                    
                    if (beamPulse.get()) {
                        long time = System.currentTimeMillis();
                        float pulse = (float) Math.sin(time / (1000f / pulseSpeed.get())) * 0.3f + 0.7f;
                        widthFactor *= pulse;
                    }
                    
                    double halfWidth = 0.25 * widthFactor;

                    event.renderer.box(
                        cx - halfWidth, spawnerPos.getY(), cz - halfWidth,
                        cx + halfWidth, topY, cz + halfWidth,
                        beamColor.get(),
                        beamColor.get(),
                        ShapeMode.Both,
                        0
                    );
                }
            }
        }
    }
}
