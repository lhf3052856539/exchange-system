package com.mnnu.dto;
/**
 * 争议处理参数
 */
import lombok.Data;

@Data
public class ResolveDisputeParam {
    private Long disputeId;
    private Integer status;              // 1-解决 2-驳回 3-拉黑
    private String result;
}
