package ru.alkaruno.rating.data;

import lombok.Data;
import ru.alkaruno.rating.IntGameRating;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class Team {

    private final String name;
    private final String city;
    private BigDecimal sum;
    private String place;
    private List<Result> results = new ArrayList<>(IntGameRating.GAMES_COUNT);
    private List<BigDecimal> bestGames;

    public Team(String name, String city) {
        this.name = name;
        this.city = city;
        for (int i = 0; i < IntGameRating.GAMES_COUNT; i++) {
            results.add(null);
        }
    }

}