package ru.alkaruno.rating;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dhatim.fastexcel.Color;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import ru.alkaruno.rating.data.Duplicates;
import ru.alkaruno.rating.data.GamePoints;
import ru.alkaruno.rating.data.Result;
import ru.alkaruno.rating.data.Team;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntGameRating {

    public static final int GAMES_COUNT = 8;
    public static final int BEST_GAMES_COUNT = 5;

    private static final Pattern pattern = Pattern.compile("tournament-\\d+-table\\.xlsx");
    static final Pattern cityPattern =
        Pattern.compile("(г\\.? )?(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final List<String> ignoredTeams = List.of("Тестовая команда1");

    @SneakyThrows
    public void run() {

        var data = new HashMap<String, Team>();
        var duplicates = getTeamDuplicates();

        int gameIndex = 0;
        for (String filename : getFilenames()) {
            var teamNames = new HashSet<String>();
            var gameResults = new ArrayList<Pair<Team, Result>>();
            ReadableWorkbook wb = new ReadableWorkbook(new FileInputStream(filename));
            var sheet = wb.getFirstSheet();
            for (Row row : sheet.read()) {
                var name = row.getCellText(1).trim().replace("\"", "");
                if ("Название".equals(name) || ignoredTeams.contains(name)) {
                    continue;
                }

                var city = getCity(row.getCellText(2).trim());
                var fullName = "%s (%s)".formatted(name, city);

                int correctAnswers = Integer.parseInt(row.getCellText(row.getCellCount() == 50 ? 12 : 14));

                System.out.print(fullName);
                fullName = duplicates.getOrDefault(fullName, fullName);
                System.out.println(" -> " + fullName);

                var lowerCase = fullName.toLowerCase();
                if (teamNames.contains(lowerCase)) {
                    System.out.printf("WARN: file: %s, duplicate team: %s, points: %s%n", filename, name, correctAnswers);
                    continue;
                }
                teamNames.add(lowerCase);

                var arr = StringUtils.split(fullName, "()");
                name = arr[0].trim();
                city = arr.length > 1 ? arr[1].trim() : "";

                var result = new Result(correctAnswers, null, null);
                gameResults.add(Pair.of(new Team(name, city), result));
            }

            var places = getPlaces(gameResults.stream().map(pair -> new BigDecimal(pair.getRight().getCorrectAnswers())).toList());
            int index = 0;
            for (String place : places) {
                var pair = gameResults.get(index++);
                var team = pair.getLeft();
                var gameResult = pair.getRight();
                var points = 0.0;
                if (place.contains("-")) {
                    var arr = place.split("-");
                    points = (getPoints(Integer.parseInt(arr[0])) + getPoints(Integer.parseInt(arr[1]))) / 2.0;
                } else {
                    points = getPoints(Integer.parseInt(place));
                }
                gameResult.setPlace(place).setPoints(new BigDecimal(points));

                var fullName = "%s (%s)".formatted(team.getName(), team.getCity()).toLowerCase();
                var result = new Result(gameResult.getCorrectAnswers(), new BigDecimal(points), place);

                if (gameIndex == 4 && !data.containsKey(fullName)) {
                    System.out.println("ERROR: Новая команда с 5 игры! " + team.getName() + " (" + team.getCity() + "), ответов: " + team.getSum());
                }

                data.computeIfAbsent(fullName, s -> new Team(team.getName(), team.getCity())).getResults().set(gameIndex, result);
            }

            gameIndex++;
            writeGameResult(gameIndex, gameResults);
        }

        for (Map.Entry<String, Team> entry : data.entrySet()) {
            var team = entry.getValue();
            IntStream.range(0, team.getResults().size()).forEach(index -> {
                var result = team.getResults().get(index);
                team.getBestGames().add(new GamePoints(index, result != null ? result.getPoints() : BigDecimal.ZERO));
            });
            team.getBestGames().sort(Comparator.comparing(GamePoints::getPoints).reversed());
            team.setBestGames(team.getBestGames().subList(0, BEST_GAMES_COUNT));
            team.setSum(team.getBestGames().stream().map(GamePoints::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        if (data.isEmpty()) {
            return;
        }

        var list = new ArrayList<>(data.values());
        list.sort((o1, o2) -> Double.compare(o2.getSum().doubleValue(), o1.getSum().doubleValue()));

        var places = getPlaces(list.stream().map(Team::getSum).toList());
        for (int i = 0; i < places.size(); i++) {
            list.get(i).setPlace(places.get(i));
        }

        // write to Excel

        try (OutputStream os = new FileOutputStream("rating.xlsx"); Workbook wb = new Workbook(os, "IntGame Rating", "1.0")) {
            Worksheet ws = wb.newWorksheet("Лист 1");

            var header = getHeader();
            for (int i = 0, len = header.size(); i < len; i++) {
                ws.value(0, i, header.get(i));
            }

            int index = 1;
            for (Team team : list) {
                if (isTopPlace(team.getPlace())) {
                    ws.range(index, 0, index, 2).style().fillColor(Color.YELLOW).set();
                }
                var indexes = team.getBestGames().stream().map(GamePoints::getGame).collect(Collectors.toSet());
                ws.value(index, 0, team.getPlace());
                ws.value(index, 1, team.getName());
                ws.value(index, 2, team.getCity());
                for (int game = 0; game < GAMES_COUNT; game++) {
                    var result = team.getResults().get(game);
                    int col = 3 + game * 3;
                    if (result != null) {
                        ws.value(index, col, result.getPlace());
                        ws.value(index, col + 1, String.valueOf(result.getCorrectAnswers()));
                        ws.value(index, col + 2, String.valueOf(result.getPoints()));
                        if (!indexes.contains(game)) {
                            ws.range(index, col, index, col + 2).style().fillColor(Color.GRAY1).set();
                        }
                    }
                }
                ws.value(index, 3 + 3 * GAMES_COUNT, team.getResults().stream().mapToInt(r -> r != null ? r.getCorrectAnswers() : 0).sum());
                ws.value(index, 3 + 3 * GAMES_COUNT + 1, team.getSum());
                index++;
            }

            ws.freezePane(0, 1);
        }

        // write to console

        var asciiTable = new AsciiTable();
        for (Team team : list) {
            asciiTable.addRow(team.getPlace(), team.getName(), team.getSum());
        }
        System.out.println(asciiTable.render());

        System.out.println("Done.");

    }

    @SneakyThrows
    private List<String> getFilenames() {
        try (var files = Files.walk(Paths.get("."))) {
            return files.filter(p -> !Files.isDirectory(p))
                .map(path -> path.getFileName().toString())
                .filter(f -> pattern.matcher(f).matches())
                .sorted()
                .toList();
        }
    }

    String getCity(String value) {
        var m = cityPattern.matcher(value);
        if (m.matches()) {
            return m.group(2);
        }
        return value;
    }

    int getPoints(int place) {
        return place < 5 ? 82 - 2 * place : Math.max(0, 78 - place);
    }

    List<String> getPlaces(List<BigDecimal> points) {
        assert points != null && !points.isEmpty();
        var result = new ArrayList<String>(points.size());

        var start = 0;
        var blockValue = points.getFirst();
        var size = points.size();

        for (int index = 1; index <= size; index++) {
            var value = index < size ? points.get(index) : BigDecimal.valueOf(-1);
            if (!value.equals(blockValue)) {
                if (index - start == 1) {
                    result.add("%d".formatted(index));
                } else {
                    var place = "%d-%d".formatted(start + 1, index);
                    for (int j = start; j < index; j++) {
                        result.add(place);
                    }
                }
                start = index;
                blockValue = value;
            }
        }

        return result;
    }

    private boolean isTopPlace(String place) {
        if (place.contains("-")) {
            return Integer.parseInt(place.split("-")[0]) <= 15;
        }
        return Integer.parseInt(place) <= 15;
    }

    private static List<String> getHeader() {
        var cols = new ArrayList<>(Arrays.asList("М", "Команда", "Город"));
        for (int game = 1; game <= GAMES_COUNT; game++) {
            cols.addAll(Arrays.asList(game + " т М", game + " т ПО", game + " т БАЛЛЫ"));
        }
        cols.addAll(Arrays.asList("ПО", "БАЛЛЫ"));
        return cols;
    }

    @SneakyThrows
    private void writeGameResult(int gameNumber, List<Pair<Team, Result>> gameResults) {
        try (OutputStream os = new FileOutputStream("game-%d.xlsx".formatted(gameNumber)); Workbook wb = new Workbook(os, "IntGame Rating", "1.0")) {
            Worksheet ws = wb.newWorksheet("Лист 1");
            int index = 0;
            for (Pair<Team, Result> gameResult : gameResults) {
                var team = gameResult.getLeft();
                var result = gameResult.getRight();
                ws.value(index, 0, result.getPlace());
                ws.value(index, 1, team.getName());
                ws.value(index, 2, team.getCity());
                ws.value(index, 3, result.getCorrectAnswers());
                ws.value(index, 4, result.getPoints());
                index++;
            }
        }
    }

    @SneakyThrows
    private Map<String, String> getTeamDuplicates() {
        return new YAMLMapper().readValue(new File("src/main/resources/duplicates.yaml"), Duplicates.class).getTeams();
    }

    public static void main(String[] args) {
        new IntGameRating().run();
    }

}
