package ru.alkaruno.rating.data;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static ru.alkaruno.rating.IntGameRating.GAMES_COUNT;

@Data
public class Team {

    private final String name;
    private final String city;
    private String place;
    private BigDecimal totalPoints;
    private BigDecimal totalCorrectAnswers;
    private List<Result> results = new ArrayList<>(GAMES_COUNT);
    private List<GamePoints> bestGames = new ArrayList<>(GAMES_COUNT);

    public Team(String name, String city) {
        this.name = name;
        this.city = city;
        for (int i = 0; i < GAMES_COUNT; i++) {
            results.add(null);
        }
    }

}
