package com.mnnu.dto;

import lombok.Data;

@Data
public class ArbitrationProposalParam {

    private String chainTradeId;
    private String accusedParty;
    private String victimParty;
    private String compensationAmount;
    private String reason;
}
