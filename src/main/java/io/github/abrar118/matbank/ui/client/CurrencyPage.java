package io.github.abrar118.matbank.ui.client;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.service.ExchangeRateService;
import io.github.abrar118.matbank.service.ExchangeRateService.CurrencyInfo;
import io.github.abrar118.matbank.service.ExchangeRateService.RateTable;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Currency converter and taka rate board. In 2022 these were three hard-coded numbers. */
public final class CurrencyPage implements Page {

    private static final DecimalFormat AMOUNT = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat RATE = new DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US));

    private final AppContext ctx;
    private final TextField amount = new TextField("100");
    private final ComboBox<CurrencyInfo> from = new ComboBox<>();
    private final ComboBox<CurrencyInfo> to = new ComboBox<>();
    private final Label result = Ui.label("", "convert-result");
    private final Label rateLine = Ui.muted("");
    private final VBox board = new VBox(8);
    private final Label source = Ui.chip("Loading", null);
    private final Label updated = Ui.muted("");
    private RateTable rates;

    public CurrencyPage(Workspace workspace) {
        this.ctx = workspace.context();
    }

    @Override
    public Node view() {
        amount.setTextFormatter(new TextFormatter<>(c -> c.getControlNewText().matches("[0-9,]*(\\.[0-9]{0,4})?") ? c : null));
        from.getItems().setAll(ExchangeRateService.CURRENCIES);
        to.getItems().setAll(ExchangeRateService.CURRENCIES);
        from.setValue(ExchangeRateService.CURRENCIES.get(1));
        to.setValue(ExchangeRateService.CURRENCIES.getFirst());
        from.setMaxWidth(Double.MAX_VALUE);
        to.setMaxWidth(Double.MAX_VALUE);
        amount.textProperty().addListener((obs, o, n) -> convert());
        from.valueProperty().addListener((obs, o, n) -> convert());
        to.valueProperty().addListener((obs, o, n) -> convert());

        Button swap = Ui.iconButton(Material2OutlinedMZ.SWAP_HORIZ, "Swap currencies");
        swap.setOnAction(e -> {
            CurrencyInfo a = from.getValue();
            from.setValue(to.getValue());
            to.setValue(a);
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(Ui.field("Amount", amount), 0, 0, 3, 1);
        VBox fromField = Ui.field("From", from);
        VBox toField = Ui.field("To", to);
        grid.add(fromField, 0, 1);
        grid.add(swap, 1, 1);
        grid.add(toField, 2, 1);
        GridPane.setHgrow(fromField, Priority.ALWAYS);
        GridPane.setHgrow(toField, Priority.ALWAYS);
        swap.setTranslateY(12);

        VBox converter = Ui.card("Converter", null, grid, result, rateLine);
        converter.setPrefWidth(480);
        converter.setMinWidth(420);

        Button refresh = Ui.iconButton(Material2OutlinedMZ.REFRESH, "Refresh rates");
        refresh.setOnAction(e -> {
            ctx.exchangeRates().invalidate();
            load(refresh);
        });
        source.setMinWidth(Region.USE_PREF_SIZE);
        updated.setWrapText(true);
        rateLine.setWrapText(true);
        VBox status = new VBox(6, source, updated);
        VBox rateCard = Ui.card("Taka rates", refresh, status, board,
                Ui.link("Rates by ExchangeRate-API", ExchangeRateService.ATTRIBUTION_URL));
        HBox.setHgrow(rateCard, Priority.ALWAYS);

        load(refresh);
        return Ui.page(new VBox(20, new VBox(4, Ui.pageTitle("Currency converter"),
                Ui.muted("Live exchange rates against the Bangladeshi taka, with an offline fallback.")),
                DepositPage.topAligned(new HBox(20, converter, rateCard))));
    }

    private void load(Button refresh) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        board.getChildren().setAll(spinner);
        Async.run(refresh, () -> ctx.exchangeRates().rates(), table -> {
            rates = table;
            source.setText(table.source().label());
            source.getStyleClass().removeAll(Styles.SUCCESS, Styles.WARNING, Styles.ACCENT);
            source.getStyleClass().add(switch (table.source()) {
                case LIVE -> Styles.SUCCESS;
                case CACHED -> Styles.ACCENT;
                case OFFLINE -> Styles.WARNING;
            });
            updated.setText(table.source() == ExchangeRateService.Source.OFFLINE
                    ? "Approximate rates from " + Formats.date(table.asOf(), ctx.clock().getZone())
                    + ". Connect to the internet for live rates."
                    : "Updated " + Formats.dateTime(table.asOf(), ctx.clock().getZone()));
            renderBoard();
            convert();
        }, message -> board.getChildren().setAll(Ui.label(message, Styles.DANGER)));
    }

    private void renderBoard() {
        board.getChildren().clear();
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(8);
        int row = 0;
        for (CurrencyInfo c : ExchangeRateService.CURRENCIES) {
            if (c.code().equals("BDT") || !rates.supports(c.code())) {
                continue;
            }
            grid.add(Ui.chip(c.code(), null), 0, row);
            grid.add(Ui.label(c.name()), 1, row);
            Label value = Ui.label(AMOUNT.format(rates.takaPer(c.code()).setScale(2, RoundingMode.HALF_EVEN)) + " BDT",
                    Styles.TEXT_BOLD);
            grid.add(value, 2, row);
            row++;
        }
        board.getChildren().add(grid);
    }

    private void convert() {
        if (rates == null || from.getValue() == null || to.getValue() == null) {
            return;
        }
        try {
            BigDecimal value = new BigDecimal(amount.getText().replace(",", ""));
            BigDecimal converted = rates.convert(value, from.getValue().code(), to.getValue().code());
            result.setText(AMOUNT.format(converted) + " " + to.getValue().code());
            BigDecimal unit = rates.unitRate(from.getValue().code(), to.getValue().code());
            rateLine.setText("1 " + from.getValue().code() + " = " + RATE.format(unit) + " " + to.getValue().code()
                    + "  ·  mid-market rate, before any bank charges");
        } catch (RuntimeException e) {
            result.setText("-");
            rateLine.setText("Enter an amount to convert.");
        }
    }
}
