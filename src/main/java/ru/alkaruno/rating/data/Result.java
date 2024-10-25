package ru.alkaruno.rating.data;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class Result {
    private Integer correctAnswers;
    private BigDecimal points;
}
