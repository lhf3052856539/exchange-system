package com.mnnu.service;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.PageDTO;
import com.mnnu.dto.ProposalDTO;


import java.math.BigInteger;

/**
 * DAO 服务接口
 */
public interface DaoService {

    /**
     * 创建提案
     * @param proposalDTO 提案信息
     * @return 交易哈希
     */

    String createProposal(CreateProposalDTO proposalDTO, @CurrentUser String currentAddress);

    /**
     * 投票
     * @param proposalId 提案 ID
     * @param support 是否支持
     * @return 交易哈希
     */
    String vote(BigInteger proposalId, boolean support);

    /**
     * 将提案加入公示期队列
     * @param proposalId 提案 ID
     * @return 交易哈希
     */
    String queueProposal(BigInteger proposalId);

    /**
     * 执行提案
     * @param proposalId 提案 ID
     * @return 交易哈希
     */
    String executeProposal(BigInteger proposalId,BigInteger eta);

    /**
     * 取消提案
     * @param proposalId 提案 ID
     * @return 交易哈希
     */
    String cancelProposal(BigInteger proposalId);

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
}
