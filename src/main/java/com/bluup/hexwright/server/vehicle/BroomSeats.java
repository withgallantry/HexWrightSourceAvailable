package com.bluup.hexwright.server.vehicle;

import java.util.List;
import java.util.Map;

public final class BroomSeats {

    public record Seat(double sideways, double vertical, double forward) {
    }

    private static final Map<BroomVariant, List<Seat>> PASSENGER_SEATS = Map.of(
        BroomVariant.GLEAMGLIDE, List.of(
            new Seat(0.0, 0.140781, -0.947031)),
        BroomVariant.NIGHTSWEEP, List.of(
            new Seat(0.0, 0.324626, -0.662409)),
        BroomVariant.STARBEAM, List.of(
            new Seat(0.0, 0.356501, -0.638502)),
        BroomVariant.TWIN_WHISK, List.of(
            new Seat(-0.677344, -0.199219, -0.911172),
            new Seat(0.677344, -0.199219, -0.911172))
    );

    private BroomSeats() {
    }

    public static List<Seat> passengerSeats(BroomVariant variant) {
        return PASSENGER_SEATS.getOrDefault(variant, List.of());
    }

    public static int capacity(BroomVariant variant) {
        return 1 + passengerSeats(variant).size();
    }

    public static Seat seat(BroomVariant variant, int seatIndex) {
        List<Seat> seats = passengerSeats(variant);
        int passengerIndex = seatIndex - 1;
        return passengerIndex >= 0 && passengerIndex < seats.size() ? seats.get(passengerIndex) : null;
    }
}
