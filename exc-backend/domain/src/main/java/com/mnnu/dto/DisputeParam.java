package com.mnnu.dto;
/**
 * 争议参数
 */
import lombok.Data;

@Data
public class DisputeParam {
    private String tradeId;
    private String reason;
    private String evidence;             // 交易哈希等证据
}
