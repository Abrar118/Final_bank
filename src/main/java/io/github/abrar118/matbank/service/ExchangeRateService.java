package io.github.abrar118.matbank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Taka exchange rates for the currency converter. Rates come from the free open.er-api.com feed (no API key),
 * are cached on disk for {@link #MAX_AGE}, and fall back to the last cached copy and then to bundled approximate
 * rates when offline. The 2022 version hard-coded three rates.
 */
public final class ExchangeRateService {

    public static final URI ENDPOINT = URI.create("https://open.er-api.com/v6/latest/BDT");
    public static final String ATTRIBUTION_URL = "https://www.exchangerate-api.com";
    public static final Duration MAX_AGE = Duration.ofHours(6);

    public enum Source {
        LIVE("Live rates"),
        CACHED("Saved rates"),
        OFFLINE("Offline approximate rates");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record CurrencyInfo(String code, String name) {
        @Override
        public String toString() {
            return code + " - " + name;
        }
    }

    public static final List<CurrencyInfo> CURRENCIES = List.of(
            new CurrencyInfo("BDT", "Bangladeshi Taka"),
            new CurrencyInfo("USD", "US Dollar"),
            new CurrencyInfo("EUR", "Euro"),
            new CurrencyInfo("GBP", "British Pound"),
            new CurrencyInfo("INR", "Indian Rupee"),
            new CurrencyInfo("SAR", "Saudi Riyal"),
            new CurrencyInfo("AED", "UAE Dirham"),
            new CurrencyInfo("MYR", "Malaysian Ringgit"),
            new CurrencyInfo("SGD", "Singapore Dollar"),
            new CurrencyInfo("JPY", "Japanese Yen"),
            new CurrencyInfo("CNY", "Chinese Yuan"),
            new CurrencyInfo("CAD", "Canadian Dollar"),
            new CurrencyInfo("AUD", "Australian Dollar"));

    /**
     * Rates relative to the taka.
     *
     * @param perTaka how many units of each currency one taka buys
     */
    public record RateTable(Map<String, BigDecimal> perTaka, Instant asOf, Source source) {

        public boolean supports(String code) {
            return perTaka.containsKey(code);
        }

        /** Taka for one unit of {@code code}, e.g. ~122 for USD. */
        public BigDecimal takaPer(String code) {
            return BigDecimal.ONE.divide(rate(code), MathContext.DECIMAL64);
        }

        public BigDecimal convert(BigDecimal amount, String from, String to) {
            BigDecimal inTaka = amount.divide(rate(from), MathContext.DECIMAL64);
            return inTaka.multiply(rate(to)).setScale(2, RoundingMode.HALF_EVEN);
        }

        private BigDecimal rate(String code) {
            BigDecimal rate = perTaka.get(code);
            if (rate == null || rate.signum() <= 0) {
                throw BankException.validation("No exchange rate for " + code);
            }
            return rate;
        }
    }

    /** Fetches the raw JSON. Swappable so tests never touch the network. */
    @FunctionalInterface
    public interface Fetcher {
        String fetch() throws IOException, InterruptedException;
    }

    private final Fetcher fetcher;
    private final Path cacheFile;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private RateTable memory;

    public ExchangeRateService(Fetcher fetcher, Path cacheFile, Clock clock) {
        this.fetcher = fetcher;
        this.cacheFile = cacheFile;
        this.clock = clock;
    }

    public static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return () -> {
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "MAT-Bank/2.0")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Exchange rate service answered HTTP " + response.statusCode());
            }
            return response.body();
        };
    }

    /** Current rates, from memory, the network, the disk cache or the bundled fallback, in that order. */
    public synchronized RateTable rates() {
        Instant now = clock.instant();
        if (memory != null && memory.source() == Source.LIVE && memory.asOf().plus(MAX_AGE).isAfter(now)) {
            return memory;
        }
        RateTable cached = readCache();
        if (cached != null && cached.asOf().plus(MAX_AGE).isAfter(now)) {
            memory = new RateTable(cached.perTaka(), cached.asOf(), Source.LIVE);
            return memory;
        }
        try {
            String body = fetcher.fetch();
            RateTable live = parse(body, Source.LIVE, now);
            writeCache(body, now);
            memory = live;
            return live;
        } catch (IOException | RuntimeException e) {
            // Offline or the feed changed shape: fall through to saved rates.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        memory = cached != null ? cached : fallback();
        return memory;
    }

    /** Forces the next {@link #rates()} call to try the network again. */
    public synchronized void invalidate() {
        memory = null;
        try {
            Files.deleteIfExists(cacheFile);
        } catch (IOException ignored) {
            // A stale cache only means one extra fetch attempt.
        }
    }

    RateTable parse(String body, Source source, Instant fetchedAt) throws IOException {
        JsonNode root = json.readTree(body);
        if (root.hasNonNull("result") && !"success".equals(root.get("result").asText())) {
            throw new IOException("Exchange rate service error: " + root.path("error-type").asText("unknown"));
        }
        if (!"BDT".equals(root.path("base_code").asText())) {
            throw new IOException("Unexpected base currency " + root.path("base_code").asText());
        }
        JsonNode rates = root.path("rates");
        Map<String, BigDecimal> perTaka = new TreeMap<>();
        for (Map.Entry<String, JsonNode> field : rates.properties()) {
            if (field.getValue().isNumber() && field.getValue().decimalValue().signum() > 0) {
                perTaka.put(field.getKey(), field.getValue().decimalValue());
            }
        }
        perTaka.put("BDT", BigDecimal.ONE);
        if (perTaka.size() < 2) {
            throw new IOException("No rates in response");
        }
        long updated = root.path("time_last_update_unix").asLong(0);
        // Live and cached tables are dated by when we fetched them; the bundled table by when it was compiled.
        Instant asOf = source == Source.OFFLINE && updated > 0 ? Instant.ofEpochSecond(updated) : fetchedAt;
        return new RateTable(Collections.unmodifiableMap(perTaka), asOf, source);
    }

    private RateTable readCache() {
        try {
            if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
                return null;
            }
            Instant savedAt = Files.getLastModifiedTime(cacheFile).toInstant();
            return parse(Files.readString(cacheFile), Source.CACHED, savedAt);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void writeCache(String body, Instant fetchedAt) {
        if (cacheFile == null) {
            return;
        }
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, body, StandardCharsets.UTF_8);
            // The file's timestamp records when we fetched it, on our clock.
            Files.setLastModifiedTime(cacheFile, FileTime.from(fetchedAt));
        } catch (IOException ignored) {
            // Caching is an optimisation.
        }
    }

    RateTable fallback() {
        try (InputStream in = ExchangeRateService.class.getResourceAsStream(
                "/io/github/abrar118/matbank/fx/fallback-rates.json")) {
            if (in == null) {
                throw new IllegalStateException("Bundled exchange rates are missing");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), Source.OFFLINE, clock.instant());
        } catch (IOException e) {
            throw new IllegalStateException("Bundled exchange rates are unreadable", e);
        }
    }
}
