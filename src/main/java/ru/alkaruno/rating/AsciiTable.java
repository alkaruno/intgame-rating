package ru.alkaruno.rating;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AsciiTable {

    private final List<List<String>> rows = new ArrayList<>();
    private int[] widths;

    public void addRow(Object... columns) {
        if (widths == null) {
            widths = new int[columns.length];
        }
        if (columns.length != widths.length) {
            throw new IllegalArgumentException("Illegal column size");
        }
        var list = new ArrayList<String>(columns.length);
        for (int i = 0; i < columns.length; i++) {
            var s = String.valueOf(columns[i]);
            widths[i] = Math.max(widths[i], s.length());
            list.add(s);
        }
        rows.add(list);
    }

    public String render() {
        String pattern = Arrays.stream(widths)
            .mapToObj("%%-%ds"::formatted)
            .collect(Collectors.joining(" | ", "| ", " |%n"));

        var sb = new StringBuilder();
        rows.forEach(row -> sb.append(pattern.formatted(row.toArray())));
        return sb.toString();
    }

}
