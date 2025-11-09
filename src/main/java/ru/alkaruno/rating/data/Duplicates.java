package ru.alkaruno.rating.data;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class Duplicates {
    Map<String, String> teams;
    Map<String, String> cities;
}
