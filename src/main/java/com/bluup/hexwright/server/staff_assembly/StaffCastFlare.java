package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import com.bluup.hexwright.server.talisman.TalismanCastEnv;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class StaffCastFlare {
    private static final float BRIGHT = 1.0f;

    private static final float DIM = 0.4f;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private StaffCastFlare() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StaffCastFlare::flush);
    }

    public static void onSpellPerformed(CastingEnvironment env) {
        ServerPlayer caster = flaringCaster(env);
        HexwrightDebug.log(HexwrightDebug.CASTING, "[tip flare] spell in {} -> caster {}",
            env.getClass().getName(), caster == null ? "REFUSED" : caster.getGameProfile().getName());
        if (caster != null) {
            pending(caster).spellThisTick = true;
        }
    }

    public static void onHexResolved(ServerPlayer caster) {
        pending(caster).resolvedThisTick = true;
    }

    @Nullable
    private static ServerPlayer flaringCaster(CastingEnvironment env) {
        if (!(env instanceof StaffCastEnv staffEnv)) {
            return null;
        }
        if (env instanceof StaffPowerCastEnv
            || env instanceof TalismanCastEnv
            || env instanceof PocketCasterCastEnv) {
            return null;
        }
        return staffEnv.getCaster();
    }

    private static Pending pending(ServerPlayer caster) {
        return PENDING.computeIfAbsent(caster.getUUID(), key -> new Pending());
    }

    private static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Pending>> entries = PENDING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Pending> entry = entries.next();
            Pending pending = entry.getValue();
            ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
            if (caster == null) {
                entries.remove();
                continue;
            }

            boolean spellInHex = pending.spellThisHex || pending.spellThisTick;
            if (pending.spellThisTick) {
                send(caster, BRIGHT);
            } else if (pending.resolvedThisTick && !spellInHex) {
                send(caster, DIM);
            }

            pending.spellThisHex = !pending.resolvedThisTick && spellInHex;
            pending.spellThisTick = false;
            pending.resolvedThisTick = false;
            if (!pending.spellThisHex) {
                entries.remove();
            }
        }
    }

    private static void send(ServerPlayer caster, float intensity) {
        HexwrightDebug.log(HexwrightDebug.CASTING, "[tip flare] sending {} to {}",
            intensity == BRIGHT ? "BRIGHT" : "DIM (" + intensity + ")",
            caster.getGameProfile().getName());

        FrozenPigment pigment = IXplatAbstractions.INSTANCE.getPigment(caster);
        CompoundTag pigmentTag = pigment != null ? pigment.serializeToNBT() : null;
        HexwrightNetworking.sendStaffTipFlash(caster, intensity, pigmentTag);
    }

    private static final class Pending {
        private boolean spellThisTick;
        private boolean resolvedThisTick;
        private boolean spellThisHex;
    }
}
