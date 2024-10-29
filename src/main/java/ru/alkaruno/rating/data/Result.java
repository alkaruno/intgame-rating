package ru.alkaruno.rating.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class Result {
    private Integer correctAnswers;
    private BigDecimal points;
    private String place;
}
