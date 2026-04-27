package com.mnnu.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GraphqlClient {

    private static final String GRAPHQL_ENDPOINT = "https://api.studio.thegraph.com/query/1747938/exchange-system-2/v0.0.2";
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public JsonNode query(String graphqlQuery) {
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("query", graphqlQuery);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPHQL_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            } else {
                log.error("GraphQL query failed with status: {}", response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("GraphQL query error", e);
            return null;
        }
    }

    /**
     * 查询非多签钱包相关的所有事件（第一批）
     * 包含：用户、交易、DAO、空投等事件
     */
    public JsonNode getGeneralEvents(long lastTimestamp, int first) {
        String query = String.format(
                "{ " +
                        "userBlacklisteds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id user blockNumber blockTimestamp transactionHash } " +
                        "tradeCreates(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id chainTradeId tradeId partyA partyB amount blockNumber blockTimestamp transactionHash } " +
                        "tradeCompleteds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId blockNumber blockTimestamp transactionHash } " +
                        "tradeDisputeds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId disputedParty blockNumber blockTimestamp transactionHash } " +
                        "tradeCancelleds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId blockNumber blockTimestamp transactionHash } " +
                        "tradeExpireds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId blockNumber blockTimestamp transactionHash } " +
                        "tradeResolveds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId blockNumber blockTimestamp transactionHash } " +
                        "proposalCreateds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId proposer description blockNumber blockTimestamp transactionHash } " +
                        "voteCasts(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId voter support weight blockNumber blockTimestamp transactionHash } " +
                        "proposalQueueds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId eta blockNumber blockTimestamp transactionHash } " +
                        "proposalExecuteds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId blockNumber blockTimestamp transactionHash } " +
                        "proposalCanceleds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId blockNumber blockTimestamp transactionHash } " +
                        "partyAConfirmeds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId party txHash blockNumber blockTimestamp transactionHash } " +
                        "partyBConfirmeds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId party txHash blockNumber blockTimestamp transactionHash } " +
                        "feeCollecteds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id tradeId feePayerA feePayerB feeAmount blockNumber blockTimestamp transactionHash } " +
                        "compensationPaids(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id victim amount blockNumber blockTimestamp transactionHash } " +
                        "userUpgradeds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id user newType blockNumber blockTimestamp transactionHash } " +
                        "airdropClaimeds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id claimant amount blockNumber blockTimestamp transactionHash } " +
                        "}",
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first
        );
        return query(query);
    }

    /**
     * 查询多签钱包相关的事件（第二批）
     * 包含：仲裁提案、投票、委员会成员变更等事件
     */
    public JsonNode getMultiSigEvents(long lastTimestamp, int first) {
        String query = String.format(
                "{ " +
                        "multiSigWalletProposalCreateds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId tradeId accusedParty blockNumber blockTimestamp transactionHash } " +
                        "multiSigWalletProposalExecuteds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId accusedParty victimParty blockNumber blockTimestamp transactionHash } " +
                        "proposalRejecteds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId blockNumber blockTimestamp transactionHash } " +
                        "proposalExpireds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId blockNumber blockTimestamp transactionHash } " +
                        "multiSigWalletVoteCasts(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id proposalId voter support blockNumber blockTimestamp transactionHash } " +
                        "committeeMemberAddeds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id member blockNumber blockTimestamp transactionHash } " +
                        "committeeMemberRemoveds(where: {blockTimestamp_gt: \"%d\"}, orderBy: blockTimestamp, orderDirection: asc, first: %d) { id member blockNumber blockTimestamp transactionHash } " +
                        "}",
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first, lastTimestamp, first, lastTimestamp, first,
                lastTimestamp, first
        );
        return query(query);
    }

}
