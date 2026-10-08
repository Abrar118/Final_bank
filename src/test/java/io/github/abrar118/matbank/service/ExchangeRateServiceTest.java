package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.demo.MutableClock;
import io.github.abrar118.matbank.service.ExchangeRateService.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateServiceTest {

    /** Trimmed copy of a real open.er-api.com response. */
    static final String LIVE = """
            {
              "result": "success",
              "provider": "https://www.exchangerate-api.com",
              "time_last_update_unix": 1791331201,
              "base_code": "BDT",
              "rates": {"BDT": 1, "USD": 0.008, "EUR": 0.0068, "GBP": 0.0059, "INR": 0.70}
            }
            """;

    @TempDir
    Path dir;

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void usesLiveRatesAndConverts() {
        var service = new ExchangeRateService(() -> LIVE, dir.resolve("rates.json"), clock);
        var table = service.rates();

        assertThat(table.source()).isEqualTo(Source.LIVE);
        assertThat(table.takaPer("USD")).isEqualByComparingTo("125");
        assertThat(table.convert(new BigDecimal("100"), "USD", "BDT")).isEqualByComparingTo("12500.00");
        assertThat(table.convert(new BigDecimal("12500"), "BDT", "EUR")).isEqualByComparingTo("85.00");
        assertThat(table.convert(new BigDecimal("10"), "GBP", "USD")).isEqualByComparingTo("13.56");
    }

    @Test
    void cachesForSixHours() {
        AtomicInteger calls = new AtomicInteger();
        var service = new ExchangeRateService(() -> {
            calls.incrementAndGet();
            return LIVE;
        }, dir.resolve("rates.json"), clock);

        service.rates();
        clock.advance(Duration.ofHours(5));
        service.rates();
        assertThat(calls).hasValue(1);

        clock.advance(Duration.ofHours(2));
        service.rates();
        assertThat(calls).hasValue(2);
    }

    @Test
    void fallsBackToBundledRatesWhenOfflineWithNoCache() {
        var service = new ExchangeRateService(() -> {
            throw new IOException("offline");
        }, dir.resolve("rates.json"), clock);

        var table = service.rates();
        assertThat(table.source()).isEqualTo(Source.OFFLINE);
        assertThat(table.takaPer("USD")).isBetween(new BigDecimal("80"), new BigDecimal("200"));
        assertThat(ExchangeRateService.CURRENCIES).allSatisfy(c -> assertThat(table.supports(c.code())).isTrue());
    }

    @Test
    void fallsBackToTheLastSavedRatesWhenTheFeedBreaks() {
        new ExchangeRateService(() -> LIVE, dir.resolve("rates.json"), clock).rates();
        clock.advance(Duration.ofDays(2));

        var service = new ExchangeRateService(() -> "<html>maintenance</html>", dir.resolve("rates.json"), clock);
        assertThat(service.rates().source()).isEqualTo(Source.CACHED);
    }

    @Test
    void rejectsResponsesForTheWrongBaseCurrency() {
        var service = new ExchangeRateService(() -> LIVE.replace("\"BDT\",", "\"USD\","), dir.resolve("r.json"), clock);
        assertThat(service.rates().source()).isEqualTo(Source.OFFLINE);
    }

    @Test
    void unknownCurrencyIsAValidationError() {
        var table = new ExchangeRateService(() -> LIVE, null, clock).rates();
        assertThatThrownBy(() -> table.convert(BigDecimal.ONE, "XYZ", "BDT")).isInstanceOf(BankException.class);
    }
}
