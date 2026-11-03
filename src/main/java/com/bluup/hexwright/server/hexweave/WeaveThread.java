package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.iota.Iota;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public record WeaveThread(int id, List<ThreadEntry> entries) {

    public WeaveThread {
        entries = List.copyOf(entries);
    }

    public List<Iota> program(ServerLevel level) {
        List<Iota> program = new ArrayList<>(entries.size());
        for (ThreadEntry entry : entries) {
            program.add(entry.iota(level));
        }
        return program;
    }
}
