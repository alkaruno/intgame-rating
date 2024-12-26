package ru.alkaruno.rating.data;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class GamePoints {
    private int game;
    private BigDecimal points;
}
