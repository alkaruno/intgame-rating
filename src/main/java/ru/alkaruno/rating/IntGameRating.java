package ru.alkaruno.rating;

import lombok.SneakyThrows;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import ru.alkaruno.rating.data.Result;
import ru.alkaruno.rating.data.Team;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public class IntGameRating {

    public static final int GAMES_COUNT = 8;
    public static final int BEST_GAMES_COUNT = 5;

    private static final Pattern pattern = Pattern.compile("tournament-\\d+-table\\.xlsx");

    @SneakyThrows
    public void run() {

        var data = new HashMap<String, Team>();

        int gameIndex = 0;
        for (String filename : getFilenames()) {
            ReadableWorkbook wb = new ReadableWorkbook(new FileInputStream(filename));
            var sheet = wb.getFirstSheet();
            for (Row row : sheet.read()) {
                var name = row.getCellText(1).trim().replace("\"", "");
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
                var result = new Result(Integer.parseInt(row.getCellText(12)), new BigDecimal(points));
                data.computeIfAbsent(name, s -> new Team(name, row.getCellText(2).trim())).getResults().set(gameIndex, result);
            }
            gameIndex++;
        }

        for (Map.Entry<String, Team> entry : data.entrySet()) {
            var team = entry.getValue();
            var list = new ArrayList<>(team.getResults().stream().map(result -> result != null ? result.getPoints() : BigDecimal.ZERO).toList());
            team.setBestGames(list);
            team.getBestGames().sort(Collections.reverseOrder());
            team.setBestGames(team.getBestGames().subList(0, BEST_GAMES_COUNT));
            team.setSum(team.getBestGames().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        if (data.isEmpty()) {
            return;
        }

        var list = new ArrayList<>(data.values());
        list.sort((o1, o2) -> Double.compare(o2.getSum().doubleValue(), o1.getSum().doubleValue()));

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

        // write to Excel

        try (OutputStream os = new FileOutputStream("rating.xlsx"); Workbook wb = new Workbook(os, "IntGame Rating", "1.0")) {
            Worksheet ws = wb.newWorksheet("Лист 1");
            int index = 0;
            for (Team team : list) {
                ws.value(index, 0, team.getPlace());
                ws.value(index, 1, team.getName());
                ws.value(index, 2, team.getSum());
                for (int game = 0; game < GAMES_COUNT; game++) {
                    var result = team.getResults().get(game);
                    ws.value(index, 2 + game * 2, result != null ? String.valueOf(result.getCorrectAnswers()) : "");
                    ws.value(index, 2 + game * 2 + 1, result != null ? String.valueOf(result.getPoints()) : "");
                }
                ws.value(index, 2 + 16, "");
                ws.value(index, 2 + 17, team.getSum());
                index++;
            }
        }

        // write to console

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