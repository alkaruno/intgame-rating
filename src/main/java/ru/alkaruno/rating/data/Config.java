package ru.alkaruno.rating.data;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
public class Config {
    Map<String, String> duplicates;
    Set<String> ignore;
    Map<String, String> cities;
}
