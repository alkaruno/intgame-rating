package ru.alkaruno.rating;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
public class Team {
    private final String name;
    private final String city;
    private BigDecimal sum;
    private List<BigDecimal> allGames = new ArrayList<>();
    private List<BigDecimal> bestGames;
    private String place;
}