package io.github.abrar118.matbank.ui.components;

import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.ui.Formats;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** Chart builders with the app's defaults (no animation, compact axis labels, taka formatting). */
public final class Charts {

    private Charts() {
    }

    public record Point(LocalDate date, Money value) {
    }

    /** Area chart over dates, with the x axis labelled "8 Oct". */
    public static AreaChart<Number, Number> trend(String seriesName, List<Point> points) {
        NumberAxis x = new NumberAxis();
        x.setAutoRanging(false);
        if (!points.isEmpty()) {
            x.setLowerBound(points.getFirst().date().toEpochDay());
            x.setUpperBound(points.getLast().date().toEpochDay());
        }
        x.setTickUnit(7);
        x.setMinorTickVisible(false);
        x.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return Formats.SHORT_DATE.format(LocalDate.ofEpochDay(value.longValue()));
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });
        NumberAxis y = amountAxis();
        AreaChart<Number, Number> chart = new AreaChart<>(x, y);
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(seriesName);
        for (Point p : points) {
            series.getData().add(new XYChart.Data<>(p.date().toEpochDay(), p.value().toBigDecimal().doubleValue()));
        }
        chart.getData().add(series);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        style(chart);
        return chart;
    }

    /** Grouped bar chart of amounts, one group per category, one bar per series. */
    public static <T> BarChart<String, Number> bars(List<T> rows, Function<T, String> category,
                                                    List<String> seriesNames, List<Function<T, Money>> values) {
        List<Function<T, Number>> numbers = values.stream()
                .<Function<T, Number>>map(f -> row -> f.apply(row).toBigDecimal().doubleValue()).toList();
        return bars(rows, category, seriesNames, numbers, amountAxis());
    }

    /** Grouped bar chart of plain counts. */
    public static <T> BarChart<String, Number> counts(List<T> rows, Function<T, String> category,
                                                      List<String> seriesNames, List<Function<T, Number>> values) {
        NumberAxis y = new NumberAxis();
        y.setMinorTickVisible(false);
        y.setTickUnit(1);
        return bars(rows, category, seriesNames, values, y);
    }

    private static <T> BarChart<String, Number> bars(List<T> rows, Function<T, String> category,
                                                     List<String> seriesNames, List<Function<T, Number>> values,
                                                     NumberAxis y) {
        CategoryAxis x = new CategoryAxis();
        BarChart<String, Number> chart = new BarChart<>(x, y);
        for (int i = 0; i < seriesNames.size(); i++) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(seriesNames.get(i));
            for (T row : rows) {
                series.getData().add(new XYChart.Data<>(category.apply(row), values.get(i).apply(row)));
            }
            chart.getData().add(series);
        }
        chart.setBarGap(3);
        chart.setCategoryGap(14);
        style(chart);
        return chart;
    }

    private static NumberAxis amountAxis() {
        NumberAxis y = new NumberAxis();
        y.setMinorTickVisible(false);
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                double v = value.doubleValue();
                if (Math.abs(v) >= 100_000) {
                    return String.format("%.1fL", v / 100_000); // lakh, as used in Bangladesh
                }
                if (Math.abs(v) >= 1_000) {
                    return String.format("%.0fk", v / 1_000);
                }
                return String.format("%.0f", v);
            }

            @Override
            public Number fromString(String s) {
                return 0;
            }
        });
        return y;
    }

    private static void style(XYChart<?, ?> chart) {
        chart.setAnimated(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(false);
        chart.setAlternativeRowFillVisible(false);
        chart.getStyleClass().add("app-chart");
        chart.setMinHeight(240);
        chart.setPrefHeight(260);
    }
}
