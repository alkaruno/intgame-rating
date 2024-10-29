package ru.alkaruno.rating;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntGameRatingTest {

    private final IntGameRating rating = new IntGameRating();

    @ParameterizedTest
    @CsvSource(value = {"1,80", "2,78", "3,76", "4,74", "5,73", "6,72", "7,71", "77,1", "78,0", "80,0"})
    public void testGetPoints(int place, int points) {
        assertEquals(points, rating.getPoints(place));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "25 24 24 23 23 23 22 20 20,1 2-3 2-3 4-6 4-6 4-6 7 8-9 8-9",
        "25.5 25.5 25.5 25 22,1-3 1-3 1-3 4 5"
    })
    public void testGetPlaces(String pointsStr, String placesStr) {
        var points = Arrays.stream(StringUtils.split(pointsStr, " ")).map(BigDecimal::new).toList();
        var places = List.of(StringUtils.split(placesStr, " "));
        Assertions.assertEquals(places, rating.getPlaces(points));
    }

}