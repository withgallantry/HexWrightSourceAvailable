package com.bluup.hexwright.client.cantillation;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.bluup.hexwright.common.cantillation.Cantillation;
import com.bluup.hexwright.server.cantillation.CantillationCast;
import com.bluup.hexwright.server.cantillation.CantillationSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.IntStream;

public final class ClientCantillations {

    private static final int QUEUE = 12;

    private static final float NOTE_VOLUME = 0.8f;
    private static final float SPELL_VOLUME = 1.0f;
    private static final float FIFTH_VOLUME = 0.55f;
    private static final float META_VOLUME = 0.6f;
    private static final float ESCAPED_VOLUME = 0.3f;
    private static final float STROKE_VOLUME = 0.2f;

    private static final int[] MAJOR_PENTATONIC = {0, 2, 4, 7, 9};
    private static final int[] MINOR_PENTATONIC = {0, 3, 5, 7, 10};
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] LYDIAN = {0, 2, 4, 6, 7, 9, 11};
    private static final int[] MIXOLYDIAN = {0, 2, 4, 5, 7, 9, 10};
    private static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10};
    private static final int[] AEOLIAN = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] WHOLE_TONE = {0, 2, 4, 6, 8, 10};
    private static final int[] PHRYGIAN_DOMINANT = {0, 1, 4, 5, 7, 8, 10};

    private static final Map<Cantillation, Voice> VOICES = new EnumMap<>(Cantillation.class);

    static {
        VOICES.put(Cantillation.CHIMES, new Voice(MAJOR_PENTATONIC, -10, 9, 2));
        VOICES.put(Cantillation.GLASS, new Voice(LYDIAN, -5, 12, 3));
        VOICES.put(Cantillation.REEDS, new Voice(MINOR_PENTATONIC, -7, 10, 3));
        VOICES.put(Cantillation.TINES, new Voice(MAJOR, -8, 12, 2));
        VOICES.put(Cantillation.TIMBER, new Voice(DORIAN, -12, 7, 2));
        VOICES.put(Cantillation.BELLS, new Voice(MIXOLYDIAN, -5, 12, 3));
        VOICES.put(Cantillation.STRINGS, new Voice(AEOLIAN, -12, 12, 2));
        VOICES.put(Cantillation.MUSIC_BOX, new Voice(MAJOR_PENTATONIC, -12, 7, 2));
        VOICES.put(Cantillation.SINGING_BOWL, new Voice(MINOR_PENTATONIC, -5, 12, 4));
        VOICES.put(Cantillation.CHOIR, new Voice(DORIAN, -7, 9, 4));
        VOICES.put(Cantillation.SPHERES, new Voice(WHOLE_TONE, -8, 12, 3));
        VOICES.put(Cantillation.HOLLOW, new Voice(PHRYGIAN_DOMINANT, -12, 5, 3));
    }

    private static final Map<Integer, Singer> SINGERS = new HashMap<>();

    private static ClientLevel boundLevel;

    private static @Nullable Cantillation worn;

    private ClientCantillations() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                SINGERS.clear();
            }
            if (client.level == null || client.isPaused()) {
                return;
            }
            Iterator<Map.Entry<Integer, Singer>> it = SINGERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Singer> entry = it.next();
                Singer singer = entry.getValue();
                if (--singer.wait > 0) {
                    continue;
                }
                Queued next = singer.queue.poll();
                if (next == null) {
                    it.remove();
                    continue;
                }
                singer.wait = sing(client.level, entry.getKey(), singer.pos, next);
            }
        });
    }

    public static void add(CantillationCast cast) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level != boundLevel) {
            boundLevel = level;
            SINGERS.clear();
        }
        Singer singer = SINGERS.computeIfAbsent(cast.casterId(), id -> new Singer());
        singer.pos = cast.pos();
        for (CantillationCast.Note note : cast.notes()) {
            if (singer.queue.size() >= QUEUE) {
                break;
            }
            singer.queue.add(new Queued(cast.cantillation(), note));
        }
        if (singer.wait <= 0 && !singer.queue.isEmpty()) {
            singer.wait = sing(level, cast.casterId(), singer.pos, singer.queue.poll());
        }
    }

    public static void wear(@Nullable Cantillation cantillation) {
        worn = cantillation;
    }

    public static SoundInstance stroke(@Nullable HexPattern drawn, SoundInstance hexSound) {
        Cantillation cantillation = worn;
        if (cantillation == null) {
            return hexSound;
        }
        Voice voice = VOICES.get(cantillation);
        int key = drawn != null ? Cantillation.keyOf(drawn) : 0;
        return new SimpleSoundInstance(CantillationSounds.stroke(cantillation).getLocation(), hexSound.getSource(),
            STROKE_VOLUME, pitch(voice.degrees[key % voice.degrees.length]), SoundInstance.createUnseededRandom(),
            false, 0, hexSound.getAttenuation(), hexSound.getX(), hexSound.getY(), hexSound.getZ(),
            hexSound.isRelative());
    }

    private static int sing(ClientLevel level, int casterId, Vec3 pos, Queued queued) {
        Voice voice = VOICES.get(queued.cantillation());
        SoundEvent sound = CantillationSounds.get(queued.cantillation());
        Entity caster = level.getEntity(casterId);
        int semitone = voice.degrees[queued.note().key() % voice.degrees.length];
        switch (queued.note().kind()) {
            case NOTE -> play(level, sound, caster, pos, semitone, NOTE_VOLUME);
            case SPELL -> {
                play(level, sound, caster, pos, semitone, SPELL_VOLUME);
                play(level, sound, caster, pos, semitone + 7 <= 12 ? semitone + 7 : semitone - 5, FIFTH_VOLUME);
            }
            case META -> play(level, sound, caster, pos, voice.degrees[0], META_VOLUME);
            case ESCAPED -> play(level, sound, caster, pos, semitone, ESCAPED_VOLUME);
        }
        return voice.stepTicks;
    }

    private static void play(ClientLevel level, SoundEvent sound, Entity caster, Vec3 pos, int semitone, float volume) {
        long seed = level.random.nextLong();
        Minecraft.getInstance().getSoundManager().play(caster != null
            ? new EntityBoundSoundInstance(sound, SoundSource.PLAYERS, volume, pitch(semitone), caster, seed)
            : new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume, pitch(semitone), RandomSource.create(seed),
                pos.x, pos.y, pos.z));
    }

    private static float pitch(int semitone) {
        return (float) Math.pow(2.0, semitone / 12.0);
    }

    private record Queued(Cantillation cantillation, CantillationCast.Note note) {
    }

    private static final class Singer {
        private final ArrayDeque<Queued> queue = new ArrayDeque<>();
        private Vec3 pos = Vec3.ZERO;
        private int wait;
    }

    private static final class Voice {
        private final int[] degrees;
        private final int stepTicks;

        Voice(int[] scale, int low, int high, int stepTicks) {
            this.degrees = IntStream.rangeClosed(low, high)
                .filter(semitone -> contains(scale, Math.floorMod(semitone, 12)))
                .toArray();
            this.stepTicks = stepTicks;
        }

        private static boolean contains(int[] scale, int pitchClass) {
            for (int degree : scale) {
                if (degree == pitchClass) {
                    return true;
                }
            }
            return false;
        }
    }
}
