package com.mnnu.controller;


import com.mnnu.apis.RateApi;
import com.mnnu.dto.AllRatesDTO;
import com.mnnu.dto.ExchangeRateDTO;
import com.mnnu.service.RateService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 汇率控制器
 */

@RestController
@RequestMapping("/apis/rate")
@RequiredArgsConstructor
public class RateController implements RateApi {

    private final RateService rateService;


    @GetMapping("/all")
    public JsonVO<AllRatesDTO> getAllRates() {
        return JsonVO.success(rateService.getAllRates());
    }

    @GetMapping("/pair")
    public JsonVO<ExchangeRateDTO> getRate(
            @RequestParam String from,
            @RequestParam String to) {
        return JsonVO.success(rateService.getCurrentRate(from, to));
    }
}
