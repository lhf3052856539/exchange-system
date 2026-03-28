package com.mnnu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 * 空投领取请求 DTO
 */
@Data
public class AirdropClaimRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 领取地址
     */
    private String address;

    /**
     * 领取数量
     */
    private BigInteger amount;

    /**
     * 默克尔证明（十六进制字符串数组）
     */
    private List<String> merkleProof;
}
