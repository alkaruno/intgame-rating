package ru.alkaruno.rating;

import lombok.SneakyThrows;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class IntGameRating {

    private static final Pattern pattern = Pattern.compile("tournament-\\d+-table\\.xlsx");

    @SneakyThrows
    public void run() {

        var data = new HashMap<String, Team>();

        for (String filename : getFilenames()) {
            ReadableWorkbook wb = new ReadableWorkbook(new FileInputStream(filename));
            var sheet = wb.getFirstSheet();
            for (Row row : sheet.read()) {
                var name = row.getCellText(1).trim();
                if ("Название".equals(name)) {
                    continue;
                }
                var points = 0.0;
                var place = row.getCellText(0);
                if (place.contains("-")) {
                    var pair = place.split("-");
                    points = (getPoints(Integer.parseInt(pair[0])) + getPoints(Integer.parseInt(pair[1]))) / 2.0;
                } else {
                    points = getPoints(Integer.parseInt(place));
                }
                data.computeIfAbsent(name, s -> new Team(name, row.getCellText(2).trim())).getAllGames().add(new BigDecimal(points));
            }
        }

        for (Map.Entry<String, Team> entry : data.entrySet()) {
            var team = entry.getValue();
            team.setBestGames(new ArrayList<>(team.getAllGames()));
            team.getBestGames().sort(Collections.reverseOrder());
            team.setBestGames(team.getAllGames().subList(0, Math.min(5, team.getBestGames().size())));
            team.setSum(team.getBestGames().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        var list = new ArrayList<>(data.values());
        list.sort((o1, o2) -> Double.compare(o2.getSum().doubleValue(), o1.getSum().doubleValue()));

        if (!list.isEmpty()) {

            int size = list.size();
            int start = 0;
            BigDecimal blockPoints = list.getFirst().getSum();

            for (int index = 1; index <= size; index++) {
                var sum = index < size ? list.get(index).getSum() : BigDecimal.valueOf(-1);
                if (!sum.equals(blockPoints)) {
                    if (index - start == 1) {
                        list.get(start).setPlace("%d".formatted(start + 1));
                    } else {
                        var place = "%d-%d".formatted(start + 1, index);
                        for (int j = start; j < index; j++) {
                            list.get(j).setPlace(place);
                        }
                    }
                    start = index;
                    blockPoints = sum;
                }
            }
        }

        var asciiTable = new AsciiTable();

        for (Team team : list) {
            asciiTable.addRow(team.getPlace(), team.getName(), team.getSum());
        }

        System.out.println(asciiTable.render());

    }

    @SneakyThrows
    private List<String> getFilenames() {
        try (var files = Files.walk(Paths.get("."))) {
            return files.filter(p -> !Files.isDirectory(p))
                .map(path -> path.getFileName().toString())
                .filter(f -> pattern.matcher(f).matches())
                .toList();
        }
    }

    int getPoints(int place) {
        return place < 5 ? 82 - 2 * place : Math.max(0, 78 - place);
    }

    public static void main(String[] args) {
        new IntGameRating().run();
    }

}