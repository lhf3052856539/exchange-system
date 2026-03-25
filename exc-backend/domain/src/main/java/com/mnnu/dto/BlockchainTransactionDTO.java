package com.mnnu.dto;
/**
 * 区块链交易DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
public class BlockchainTransactionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String txHash;
    private String from;
    private String to;
    private BigInteger value;
    private BigInteger gasPrice;
    private BigInteger gasUsed;
    private String blockNumber;
    private String status;
    private LocalDateTime timestamp;
}
