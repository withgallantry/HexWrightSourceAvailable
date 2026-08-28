package com.bluup.hexwright.server.harmonic;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface HarmonicChannelTarget {

    @Nullable String tuningRefusal();

    int harmonic();

    void tune(int harmonic);

    void untune();

    List<String> networkKeys();
}
