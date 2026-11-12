package dev.orbitalmace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class OrbitalStrike {
    // ---- Einstellungen (hier kannst du alles anpassen) ----
    static final int COOLDOWN_TICKS = 20 * 8;   // Abklingzeit zwischen zwei Strikes (8 Sek.)
    static final float NUKE_POWER = 9.0f;       // TNT = 4.0
    static final int NUKE_FALL_TICKS = 40;      // Fallzeit der Nuke
    static final int STAB_MIN = 10, STAB_MAX = 16; // Anzahl Stab-Shots
    static final double STAB_MIN_DIST = 5, STAB_MAX_DIST = 19;
    static final double HEIGHT = 30.0;          // Starthoehe des Strahls
    // -------------------------------------------------------

    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> SHIELD_COUNT = new HashMap<>();
    private static final Map<UUID, Boolean> SHIELD_PREV = new HashMap<>();

    private OrbitalStrike() {}

    public static void trigger(ServerLevel level, ServerPlayer owner, Vec3 center) {
        long now = level.getServer().getTickCount();
        Long until = COOLDOWNS.get(owner.getUUID());
        if (until != null && now < until) {
            owner.displayClientMessage(Component.literal("Orbital Mace: Abklingzeit " + ((until - now) / 20 + 1) + "s"), true);
            return;
        }
        COOLDOWNS.put(owner.getUUID(), now + COOLDOWN_TICKS);
        owner.displayClientMessage(Component.literal("ORBITAL STRIKE INBOUND!"), true);

        RandomSource r = level.getRandom();
        add(new Strike(level, owner.getUUID(), center, 0, NUKE_FALL_TICKS, NUKE_POWER, false, true), owner);

        int n = STAB_MIN + r.nextInt(STAB_MAX - STAB_MIN + 1);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = STAB_MIN_DIST + r.nextDouble() * (STAB_MAX_DIST - STAB_MIN_DIST);
            Vec3 p = center.add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
            int delay = 8 + r.nextInt(90);          // jeder Shot zu anderer Zeit
            int fall = 8 + r.nextInt(10);           // unterschiedliche Fallgeschwindigkeit
            float power = 2.5f + r.nextFloat() * 2.5f; // unterschiedliche Staerke
            boolean fire = r.nextFloat() < 0.3f;    // manche setzen Feuer
            add(new Strike(level, owner.getUUID(), p, delay, fall, power, fire, false), owner);
        }
    }

    private static void add(Strike s, ServerPlayer owner) {
        UUID id = owner.getUUID();
        int c = SHIELD_COUNT.getOrDefault(id, 0);
        if (c == 0) {
            SHIELD_PREV.put(id, owner.isInvulnerable());
            owner.setInvulnerable(true); // Besitzer nimmt keinen Schaden von den eigenen Explosionen
        }
        SHIELD_COUNT.put(id, c + 1);
        STRIKES.add(s);
    }

    private static void release(MinecraftServer server, UUID id) {
        int c = SHIELD_COUNT.getOrDefault(id, 0) - 1;
        if (c > 0) { SHIELD_COUNT.put(id, c); return; }
        SHIELD_COUNT.remove(id);
        Boolean prev = SHIELD_PREV.remove(id);
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null && prev != null) p.setInvulnerable(prev);
    }

    public static void onDisconnect(MinecraftServer server, UUID id) {
        STRIKES.removeIf(s -> s.owner.equals(id));
        SHIELD_COUNT.remove(id);
        Boolean prev = SHIELD_PREV.remove(id);
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null && prev != null) p.setInvulnerable(prev);
    }

    public static void clearAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(SHIELD_PREV.keySet())) onDisconnect(server, id);
        STRIKES.clear();
        COOLDOWNS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (!STRIKES.isEmpty()) {
            Iterator<Strike> it = STRIKES.iterator();
            while (it.hasNext()) {
                Strike s = it.next();
                boolean done;
                try { done = s.tick(); } catch (Throwable t) { t.printStackTrace(); done = true; }
                if (done) { it.remove(); release(server, s.owner); }
            }
        }
        heldEffects(server);
    }

    /** Explosions-/Flammen-Partikel, die um die Mace kreisen, solange man sie in der Hand haelt. */
    private static void heldEffects(MinecraftServer server) {
        long t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.getMainHandItem().is(OrbitalMaceMod.ORBITAL_MACE)) continue;
            if (!(p.level() instanceof ServerLevel sl)) continue;
            Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.55, 0);
            double a0 = t * 0.35;
            for (int k = 0; k < 3; k++) {
                double a = a0 + k * 2.094;
                sl.sendParticles(ParticleTypes.FLAME,
                        c.x + Math.cos(a) * 0.55, c.y + Math.sin(a * 1.5) * 0.25, c.z + Math.sin(a) * 0.55,
                        1, 0, 0, 0, 0.0);
            }
            if (t % 6 == 0) sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.3, c.z, 2, 0.25, 0.2, 0.25, 0.01);
            if (t % 25 == 0) sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0.3, 0.3, 0.3, 0.0);
        }
    }

    private static final class Strike {
        final ServerLevel level;
        final UUID owner;
        final Vec3 pos;
        int delay;
        final int fallTotal;
        int fall;
        final float power;
        final boolean fire, nuke;
        boolean exploded;
        int after;

        Strike(ServerLevel level, UUID owner, Vec3 pos, int delay, int fall, float power, boolean fire, boolean nuke) {
            this.level = level; this.owner = owner; this.pos = pos; this.delay = delay;
            this.fallTotal = fall; this.fall = fall; this.power = power; this.fire = fire; this.nuke = nuke;
        }

        boolean tick() {
            if (delay > 0) {
                if (delay % 3 == 0) ring(nuke ? ParticleTypes.FLAME : ParticleTypes.END_ROD, power * 0.9, nuke ? 36 : 14, pos.y + 0.2);
                delay--;
                return false;
            }
            if (fall > 0) {
                fall--;
                double prog = 1.0 - (double) fall / fallTotal;
                double headY = pos.y + HEIGHT * (1.0 - prog);
                // durchgehende Strahlsaeule vom Himmel zum Ziel
                for (double y = pos.y; y < pos.y + HEIGHT; y += nuke ? 1.0 : 2.0) {
                    level.sendParticles(ParticleTypes.END_ROD, pos.x, y, pos.z, nuke ? 3 : 1, nuke ? 0.3 : 0.05, 0, nuke ? 0.3 : 0.05, 0.0);
                }
                level.sendParticles(ParticleTypes.END_ROD, pos.x, headY, pos.z, nuke ? 14 : 5, nuke ? 0.35 : 0.12, 0.5, nuke ? 0.35 : 0.12, 0.02);
                for (int k = 1; k <= 8; k++) {
                    level.sendParticles(nuke ? ParticleTypes.FLAME : (fire ? ParticleTypes.FLAME : ParticleTypes.SMOKE),
                            pos.x, headY + k * 1.3, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
                if (nuke && fall % 4 == 0) ring(ParticleTypes.END_ROD, power * 0.9, 36, pos.y + 0.2);
                return false;
            }
            if (!exploded) {
                exploded = true;
                level.explode(level.getPlayerByUUID(owner), pos.x, pos.y, pos.z, power, fire, Level.ExplosionInteraction.TNT);
                if (nuke) {
                    for (int i = 0; i < 4; i++) {
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1 + i * 2.5, pos.z, 1, 1.5, 0.5, 1.5, 0.0);
                    }
                    for (double rad : new double[] {2, 5, 8, 11, 14}) {
                        for (int i = 0; i < 48; i++) {
                            double a = i * Math.PI * 2 / 48;
                            level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.3, pos.z, 0,
                                    Math.cos(a), 0.02, Math.sin(a), 0.4 + rad * 0.05);
                        }
                    }
                    after = 30;
                    return false;
                }
                return true;
            }
            after--;
            double rise = (30 - after) * 0.45;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 3 + rise, pos.z, 8, 2.2, 0.8, 2.2, 0.02);
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1, pos.z, 10, 3.5, 0.4, 3.5, 0.03);
            return after <= 0;
        }

        private void ring(ParticleOptions type, double radius, int points, double y) {
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2 / points;
                level.sendParticles(type, pos.x + Math.cos(a) * radius, y, pos.z + Math.sin(a) * radius, 1, 0, 0, 0, 0.0);
            }
        }
    }
}
