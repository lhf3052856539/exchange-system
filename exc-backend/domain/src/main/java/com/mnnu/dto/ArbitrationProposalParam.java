package com.mnnu.dto;

import lombok.Data;

@Data
public class ArbitrationProposalParam {

    private String tradeId;
    private String accusedParty;
    private String victimParty;
    private String compensationAmount;
    private String reason;
}
