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
import ru.alkaruno.rating.data.Config;
import ru.alkaruno.rating.data.GamePoints;
import ru.alkaruno.rating.data.Result;
import ru.alkaruno.rating.data.Team;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IntGameRating {

    public static final int GAMES_COUNT = 8;
    private static final int BEST_GAMES_COUNT = 5;
    private static final int TOP_PLACE_COUNT = 15;

    private static final Pattern TABLE_PATTERN = Pattern.compile("tournament-\\d+-table\\.xlsx");
    private static final Pattern CITY_PATTERN =
        Pattern.compile("(?:г[. ]+)?(.+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String ONLINE = "Online";

    private static final List<String> IGNORED_TEAMS = List.of();
    private final Config config = getConfig();

    @SneakyThrows
    public void run() {

        Map<String, Team> data = new HashMap<>();

        forEach(getFilenames(), (filename, gameIndex) -> {
            List<Pair<Team, Result>> gameResults = new ArrayList<>();
            for (Row row : getSheetRows(filename)) {
                String name = row.getCellText(1).trim().replace("\"", "");
                if ("Название".equals(name) || IGNORED_TEAMS.contains(name)) {
                    continue;
                }

                if (row.getCellText(8).trim().equals(ONLINE)) {
                    continue;
                }

                int correctAnswers = Integer.parseInt(row.getCellText(row.getCellCount() == 50 ? 12 : 14));
                if (correctAnswers == 0) {
                    continue;
                }

                String city = getCity(row.getCellText(2).trim());
                String fullName = "%s (%s)".formatted(name, city);

                if (config.getIgnore().contains(fullName)) {
                    continue;
                }

                fullName = config.getDuplicates().getOrDefault(fullName, fullName);

                String[] arr = StringUtils.split(fullName, "()");
                name = arr[0].trim();
                city = arr.length > 1 ? arr[1].trim() : "";

                gameResults.add(Pair.of(new Team(name, city), new Result(correctAnswers)));
            }

            List<String> places = getPlaces(gameResults.stream().map(pair -> new BigDecimal(pair.getRight().getCorrectAnswers())).toList());
            int index = 0;
            for (String place : places) {
                Pair<Team, Result> pair = gameResults.get(index++);
                Team team = pair.getLeft();
                Result gameResult = pair.getRight();
                double points;
                if (place.contains("-")) {
                    String[] arr = place.split("-");
                    points = (getPoints(Integer.parseInt(arr[0])) + getPoints(Integer.parseInt(arr[1]))) / 2.0;
                } else {
                    points = getPoints(Integer.parseInt(place));
                }
                gameResult.setPlace(place).setPoints(new BigDecimal(points));

                String fullName = "%s (%s)".formatted(team.getName(), team.getCity()).toLowerCase();
                Result result = new Result(gameResult.getCorrectAnswers(), new BigDecimal(points), place);

                Team t = data.computeIfAbsent(fullName, s -> new Team(team.getName(), team.getCity()));
                t.getResults().set(gameIndex, result);
                t.setTotalCorrectAnswers(t.getTotalCorrectAnswers() + result.getCorrectAnswers());
            }

            writeGameResult(gameIndex + 1, gameResults);
        });

        for (Map.Entry<String, Team> entry : data.entrySet()) {
            Team team = entry.getValue();
            IntStream.range(0, team.getResults().size()).forEach(index -> {
                Result result = team.getResults().get(index);
                team.getBestGames().add(new GamePoints(index, result != null ? result.getPoints() : BigDecimal.ZERO));
            });
            team.getBestGames().sort(Comparator.comparing(GamePoints::getPoints).reversed());
            team.setBestGames(team.getBestGames().subList(0, BEST_GAMES_COUNT));
            team.setTotalPoints(team.getBestGames().stream().map(GamePoints::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        List<Team> teams = new ArrayList<>(data.values());
        teams.sort(Comparator
            .comparing(Team::getTotalPoints).reversed()
            .thenComparing(Comparator.comparing(Team::getTotalCorrectAnswers).reversed())
        );

        List<String> places = getPlaces(teams.stream().map(Team::getTotalPoints).toList());
        forEach(places, (place, i) -> teams.get(i).setPlace(place));

        writeCommonRating(teams);
        writeToConsole(teams);
    }

    private void writeCommonRating(List<Team> teams) throws IOException {
        try (OutputStream os = new FileOutputStream("rating.xlsx"); Workbook wb = new Workbook(os, "IntGame Rating", "1.0")) {
            Worksheet ws = wb.newWorksheet("Рейтинг команд");

            List<String> header = getHeader();
            for (int i = 0, len = header.size(); i < len; i++) {
                ws.value(0, i, header.get(i));
            }

            int index = 1;
            for (Team team : teams) {
                if (isTopPlace(team.getPlace())) {
                    ws.range(index, 0, index, 2).style().fillColor(Color.YELLOW).set();
                }
                Set<Integer> indexes = team.getBestGames().stream().map(GamePoints::getGame).collect(Collectors.toSet());
                ws.value(index, 0, team.getPlace());
                ws.value(index, 1, team.getName());
                ws.value(index, 2, team.getCity());
                for (int game = 0; game < GAMES_COUNT; game++) {
                    Result result = team.getResults().get(game);
                    if (result != null) {
                        int col = 3 + game * 3;
                        ws.value(index, col, result.getPlace());
                        ws.value(index, col + 1, String.valueOf(result.getCorrectAnswers()));
                        ws.value(index, col + 2, String.valueOf(result.getPoints()));
                        if (!indexes.contains(game)) {
                            ws.range(index, col, index, col + 2).style().fillColor(Color.GRAY1).set();
                        }
                    }
                }
                ws.value(index, 3 + 3 * GAMES_COUNT, team.getTotalCorrectAnswers());
                ws.value(index, 3 + 3 * GAMES_COUNT + 1, team.getTotalPoints());
                index++;
            }

            ws.freezePane(0, 1);
        }
    }

    @SneakyThrows
    private void writeGameResult(int gameNumber, List<Pair<Team, Result>> gameResults) {
        try (OutputStream os = new FileOutputStream("game-%d.xlsx".formatted(gameNumber));
             Workbook wb = new Workbook(os, "IntGame Rating", "1.0")) {
            Worksheet ws = wb.newWorksheet("Лист 1");
            int index = 0;
            for (Pair<Team, Result> gameResult : gameResults) {
                Team team = gameResult.getLeft();
                Result result = gameResult.getRight();
                ws.value(index, 0, result.getPlace());
                ws.value(index, 1, team.getName());
                ws.value(index, 2, team.getCity());
                ws.value(index, 3, result.getCorrectAnswers());
                ws.value(index, 4, result.getPoints());
                index++;
            }
        }
    }

    private void writeToConsole(List<Team> teams) {
        AsciiTable asciiTable = new AsciiTable();
        teams.forEach(team -> asciiTable.addRow(
            team.getPlace(),
            team.getName(),
            team.getCity(),
            team.getTotalPoints(),
            team.getTotalCorrectAnswers()
        ));
        System.out.println(asciiTable.render());
    }

    public static <T> void forEach(Collection<T> collection, BiConsumer<T, Integer> consumer) {
        AtomicInteger index = new AtomicInteger(0);
        collection.forEach(item -> consumer.accept(item, index.getAndIncrement()));
    }

    @SneakyThrows
    private List<String> getFilenames() {
        try (Stream<Path> files = Files.list(Paths.get("."))) {
            return files
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(filename -> TABLE_PATTERN.matcher(filename).matches())
                .sorted()
                .toList();
        }
    }

    @SneakyThrows
    private List<Row> getSheetRows(String filename) {
        ReadableWorkbook wb = new ReadableWorkbook(new FileInputStream(filename));
        return wb.getFirstSheet().read();
    }

    String getCity(String value) {
        if (value.contains("Кулебаки")) {
            return "Кулебаки";
        }
        if (config.getCities().containsKey(value)) {
            return config.getCities().get(value);
        }
        Matcher m = CITY_PATTERN.matcher(value);
        if (m.matches()) {
            return m.group(1);
        }
        return value;
    }

    int getPoints(int place) {
        return place < 5 ? 82 - 2 * place : Math.max(0, 78 - place);
    }

    List<String> getPlaces(List<BigDecimal> points) {
        assert points != null && !points.isEmpty();
        List<String> result = new ArrayList<>(points.size());

        int start = 0;
        BigDecimal blockValue = points.getFirst();
        int size = points.size();

        for (int index = 1; index <= size; index++) {
            BigDecimal value = index < size ? points.get(index) : BigDecimal.valueOf(-1);
            if (!value.equals(blockValue)) {
                if (index - start == 1) {
                    result.add("%d".formatted(index));
                } else {
                    String place = "%d-%d".formatted(start + 1, index);
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
            return Integer.parseInt(place.split("-")[0]) <= TOP_PLACE_COUNT;
        }
        return Integer.parseInt(place) <= TOP_PLACE_COUNT;
    }

    private static List<String> getHeader() {
        List<String> cols = new ArrayList<>(Arrays.asList("М", "Команда", "Город"));
        for (int game = 1; game <= GAMES_COUNT; game++) {
            cols.addAll(Arrays.asList(game + " т М", game + " т ПО", game + " т БАЛЛЫ"));
        }
        cols.addAll(Arrays.asList("ПО", "БАЛЛЫ"));
        return cols;
    }

    @SneakyThrows
    private Config getConfig() {
        return new YAMLMapper().readValue(new File("src/main/resources/config.yml"), Config.class);
    }

    public static void main(String[] args) {
        new IntGameRating().run();
    }

}
