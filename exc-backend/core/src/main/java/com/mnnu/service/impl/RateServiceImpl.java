package com.mnnu.service.impl;

import com.mnnu.dto.AllRatesDTO;
import com.mnnu.dto.ExchangeRateDTO;
import com.mnnu.entity.ExchangeRateEntity;
import com.mnnu.mapper.ExchangeRateMapper;
import com.mnnu.service.RateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RateServiceImpl implements RateService {

    private static final String RATE_API_URL = "https://api.exchangerate-api.com/v4/latest/USD";

    @Autowired
    private ExchangeRateMapper exchangeRateMapper;

    @Autowired
    private RestTemplate restTemplate;

    private Map<String, ExchangeRateDTO> rateCache = new HashMap<>();
    private long lastUpdateTime = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;

    @Override
    public ExchangeRateDTO getCurrentRate(String fromCurrency, String toCurrency) {
        String pair = fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();

        ExchangeRateDTO cached = rateCache.get(pair);
        if (cached != null && (System.currentTimeMillis() - lastUpdateTime) < CACHE_DURATION_MS) {
            return cached;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(RATE_API_URL, Map.class);
            Map<String, Object> rates = (Map) response.getBody().get("rates");

            BigDecimal fromRate = getRateFromMap(rates, fromCurrency);
            BigDecimal toRate = getRateFromMap(rates, toCurrency);

            BigDecimal exchangeRate = toRate.divide(fromRate, 8, BigDecimal.ROUND_HALF_UP);

            ExchangeRateDTO dto = new ExchangeRateDTO();
            dto.setPair(pair);
            dto.setRate(exchangeRate);
            dto.setTimestamp(LocalDateTime.now());

            rateCache.put(pair, dto);
            lastUpdateTime = System.currentTimeMillis();

            saveToDatabase(pair, exchangeRate);

            log.info("Fetched rate for {}: {}", pair, exchangeRate);
            return dto;

        } catch (Exception e) {
            log.error("Failed to fetch rate from API, using cached or default rate", e);
            return getCachedOrDefaultRate(pair);
        }
    }

    @Override
    public AllRatesDTO getAllRates() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(RATE_API_URL, Map.class);
            Map<String, Object> rates = (Map) response.getBody().get("rates");

            AllRatesDTO allRates = new AllRatesDTO();

            BigDecimal cnyRate = getRateFromMap(rates, "CNY");
            BigDecimal gbpRate = getRateFromMap(rates, "GBP");
            BigDecimal usdtRate = BigDecimal.ONE;

            allRates.setUsdToCny(cnyRate);
            allRates.setUsdToGbp(gbpRate);
            allRates.setCnyToUsd(BigDecimal.ONE.divide(cnyRate, 8, BigDecimal.ROUND_HALF_UP));
            allRates.setGbpToUsd(BigDecimal.ONE.divide(gbpRate, 8, BigDecimal.ROUND_HALF_UP));
            allRates.setTimestamp(LocalDateTime.now());

            updateCache(allRates);
            saveAllRatesToDatabase(allRates);

            log.info("Fetched all rates: CNY={}, GBP={}, USDT={}", cnyRate, gbpRate, usdtRate);
            return allRates;

        } catch (Exception e) {
            log.error("Failed to fetch all rates from API, using cached data", e);
            return getCachedAllRates();
        }
    }

    @Override
    public void updateRates() {
        log.info("Updating exchange rates from external API...");
        getAllRates();
    }

    @Override
    public BigDecimal calculateExchangeAmount(BigDecimal amount, String fromCurrency, String toCurrency) {
        ExchangeRateDTO rate = getCurrentRate(fromCurrency, toCurrency);
        return amount.multiply(rate.getRate());
    }

    @Override
    public ExchangeRateDTO getCachedRate(String pair) {
        return rateCache.getOrDefault(pair, getCachedOrDefaultRate(pair));
    }

    private BigDecimal getRateFromMap(Map<String, Object> rates, String currency) {
        Object rateObj = rates.get(currency.toUpperCase());
        if (rateObj == null) {
            log.warn("Rate not found for currency: {}", currency);
            return BigDecimal.ONE;
        }
        return new BigDecimal(rateObj.toString());
    }

    private ExchangeRateDTO getCachedOrDefaultRate(String pair) {
        ExchangeRateDTO cached = rateCache.get(pair);
        if (cached != null) {
            log.debug("Using cached rate for {}", pair);
            return cached;
        }

        ExchangeRateDTO dto = new ExchangeRateDTO();
        dto.setPair(pair);

        if (pair.contains("CNY")) {
            dto.setRate(new BigDecimal("7.23"));
        } else if (pair.contains("GBP")) {
            dto.setRate(new BigDecimal("0.79"));
        } else {
            dto.setRate(BigDecimal.ONE);
        }

        dto.setTimestamp(LocalDateTime.now());
        log.warn("Using default rate for {}: {}", pair, dto.getRate());
        return dto;
    }

    private AllRatesDTO getCachedAllRates() {
        AllRatesDTO allRates = new AllRatesDTO();

        if (!rateCache.isEmpty()) {
            ExchangeRateDTO cnyUsd = rateCache.get("CNY_USD");
            ExchangeRateDTO gbpUsd = rateCache.get("GBP_USD");

            if (cnyUsd != null) {
                allRates.setUsdToCny(cnyUsd.getRate());
                allRates.setCnyToUsd(BigDecimal.ONE.divide(cnyUsd.getRate(), 8, BigDecimal.ROUND_HALF_UP));
            } else {
                allRates.setUsdToCny(new BigDecimal("7.23"));
                allRates.setCnyToUsd(new BigDecimal("0.1383"));
            }

            if (gbpUsd != null) {
                allRates.setUsdToGbp(gbpUsd.getRate());
                allRates.setGbpToUsd(BigDecimal.ONE.divide(gbpUsd.getRate(), 8, BigDecimal.ROUND_HALF_UP));
            } else {
                allRates.setUsdToGbp(new BigDecimal("0.79"));
                allRates.setGbpToUsd(new BigDecimal("1.2658"));
            }

        } else {
            allRates.setUsdToCny(new BigDecimal("7.23"));
            allRates.setUsdToGbp(new BigDecimal("0.79"));
            allRates.setCnyToUsd(new BigDecimal("0.1383"));
            allRates.setGbpToUsd(new BigDecimal("1.2658"));
        }

        allRates.setTimestamp(LocalDateTime.now());
        log.warn("Using cached/default rates");
        return allRates;
    }

    private void updateCache(AllRatesDTO allRates) {
        ExchangeRateDTO cnyUsd = new ExchangeRateDTO();
        cnyUsd.setPair("CNY_USD");
        cnyUsd.setRate(allRates.getCnyToUsd());
        cnyUsd.setTimestamp(allRates.getTimestamp());
        rateCache.put("CNY_USD", cnyUsd);

        ExchangeRateDTO gbpUsd = new ExchangeRateDTO();
        gbpUsd.setPair("GBP_USD");
        gbpUsd.setRate(allRates.getGbpToUsd());
        gbpUsd.setTimestamp(allRates.getTimestamp());
        rateCache.put("GBP_USD", gbpUsd);
    }

    private void saveToDatabase(String pair, BigDecimal rate) {
        try {
            ExchangeRateEntity entity = new ExchangeRateEntity();
            entity.setPair(pair);
            entity.setRate(rate);
            entity.setUpdateTime(LocalDateTime.now());
            exchangeRateMapper.insert(entity);
            log.debug("Saved rate to database: {} = {}", pair, rate);
        } catch (Exception e) {
            log.warn("Failed to save rate to database: {}", e.getMessage());
        }
    }

    private void saveAllRatesToDatabase(AllRatesDTO allRates) {
        saveToDatabase("USD_CNY", allRates.getUsdToCny());
        saveToDatabase("USD_GBP", allRates.getUsdToGbp());
        saveToDatabase("CNY_USD", allRates.getCnyToUsd());
        saveToDatabase("GBP_USD", allRates.getGbpToUsd());
    }
}
