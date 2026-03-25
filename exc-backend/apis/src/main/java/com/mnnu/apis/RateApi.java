package com.mnnu.apis;

import com.mnnu.dto.AllRatesDTO;
import com.mnnu.dto.ExchangeRateDTO;
import com.mnnu.vo.JsonVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 汇率 API 接口
 */

public interface RateApi {


    JsonVO<AllRatesDTO> getAllRates();


    JsonVO<ExchangeRateDTO> getRate(
            @RequestParam String from,
            @RequestParam String to);
}

