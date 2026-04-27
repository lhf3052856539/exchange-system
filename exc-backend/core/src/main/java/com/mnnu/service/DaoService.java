package com.mnnu.service;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.PageDTO;
import com.mnnu.dto.ProposalDTO;


import java.math.BigInteger;
import java.util.Map;

/**
 * DAO 服务接口
 */
public interface DaoService {

    /**
     * 创建提案
     * @param proposalDTO 提案信息
     * @return 交易哈希
     */

    void createProposal(CreateProposalDTO proposalDTO, @CurrentUser String currentAddress);

    /**
     * 投票前校验
     */
    void vote(BigInteger proposalId, boolean support, String voterAddress);

    /**
     * 将提案加入公示期队列
     * @param proposalId 提案 ID
     */
    void queueProposal(BigInteger proposalId);

    /**
     * 执行提案
     * @param proposalId 提案 ID
     * @return 交易哈希
     */
    void executeProposal(BigInteger proposalId,BigInteger eta);

    /**
     * 取消提案前校验
     */
    void cancelProposal(BigInteger proposalId, String callerAddress);

    /**
     * 获取提案详情
     * @param proposalId 提案 ID
     * @param address 用户地址
     * @return 提案信息
     */
    ProposalDTO getProposal(BigInteger proposalId, String address);

    /**
     * 获取所有提案
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param address 用户地址
     * @return 分页提案列表
     */
    PageDTO<ProposalDTO> getAllProposals(Integer pageNum, Integer pageSize, String address);

    /**
     * 获取提案状态
     * @param proposalId 提案 ID
     * @return 提案状态
     */
    Integer getProposalState(BigInteger proposalId);

    /**
     * 查询是否已投票
     * @param proposalId 提案 ID
     * @param voter 投票者地址
     * @return 是否已投票
     */
    Boolean hasVoted(BigInteger proposalId, String voter);

    /**
     * 获取投票周期
     * @return 投票周期
     */
    BigInteger getVotingPeriod();

    /**
     * 设置投票周期
     * @param newPeriod 新的投票周期
     * @return 交易哈希
     */
    String setVotingPeriod(BigInteger newPeriod);

    /**
     * 获取提案总数
     * @return 提案总数
     */
    BigInteger getProposalCount();

    /**
     * 获取金库余额信息
     * @return 包含各种代币余额的 Map
     */
    Map<String, Object> getTreasureBalance();

    /**
     * 同步提案创建信息
     */
    boolean syncProposalFromChain(String proposalId, String proposer, String txHash);

    /**
     * 同步投票信息
     */
    boolean syncProposalVotesFromChain(BigInteger proposalId);
}
