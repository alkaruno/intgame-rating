package ru.alkaruno.rating;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntGameRatingTest {

    private final IntGameRating rating = new IntGameRating();

    @ParameterizedTest
    @CsvSource(value = {"1,80", "2,78", "3,76", "4,74", "5,73", "6,72", "7,71", "77,1", "78,0", "80,0"})
    public void testGetPoints(int place, int points) {
        assertEquals(points, rating.getPoints(place));
    }

}