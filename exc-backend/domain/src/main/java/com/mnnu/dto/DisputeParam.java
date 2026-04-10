package com.mnnu.dto;
/**
 * 争议参数
 */
import lombok.Data;

@Data
public class DisputeParam {
    private String tradeId;
    private String initiator;
    private String accused;
    private String reason;
    private String evidence;
    private String compensationAmount;
}
