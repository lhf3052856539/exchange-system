package com.mnnu.apis;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.vo.JsonVO;
import com.mnnu.dto.PageDTO;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

/**
 * DAO治理 API 接口
 */
public interface DaoApi {

    /**
     * 创建提案
     * @param address 提案人地址
     * @param proposalDTO 提案信息
     * @return 交易哈希
     */
    JsonVO<String> createProposal(
            @CurrentUser String address,
            @RequestBody CreateProposalDTO proposalDTO
    );

    /**
     * 投票
     * @param address 投票人地址
     * @param proposalId 提案 ID
     * @param support 是否支持
     * @return 交易哈希
     */
    JsonVO<String> vote(
            @CurrentUser String address,
            @RequestParam BigInteger proposalId,
            @RequestParam Boolean support
    );

    /**
     * 将提案加入公示期队列
     * @param proposalId 提案 ID
     * @return 交易哈希
     */
    JsonVO<String> queueProposal(@RequestParam BigInteger proposalId);

    /**
     * 执行提案
     * @param proposalId 提案 ID
     * @param eta 公示期执行时间
     * @return 交易哈希
     */
    JsonVO<String> executeProposal(
            @RequestParam BigInteger proposalId,
            @RequestParam BigInteger eta
    );

    /**
     * 取消提案
     * @param proposalId 提案 ID
     * @return 交易哈希
     */
    JsonVO<String> cancelProposal(@RequestParam BigInteger proposalId);

    /**
     * 获取提案详情
     * @param proposalId 提案 ID
     * @param address 用户地址（可选，用于查询是否已投票）
     * @return 提案信息
     */
    JsonVO<ProposalDTO> getProposal(
            @RequestParam BigInteger proposalId,
            @RequestParam(required = false) String address
    );

    /**
     * 获取所有提案列表
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param address 用户地址（可选，用于查询是否已投票）
     * @return 分页提案列表
     */
    JsonVO<PageDTO<ProposalDTO>> getAllProposals(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String address
    );

    /**
     * 获取提案状态
     * @param proposalId 提案 ID
     * @return 提案状态
     */
    JsonVO<Integer> getProposalState(@RequestParam BigInteger proposalId);

    /**
     * 查询是否已投票
     * @param proposalId 提案 ID
     * @param voter 投票者地址
     * @return 是否已投票
     */
    JsonVO<Boolean> hasVoted(
            @RequestParam BigInteger proposalId,
            @RequestParam String voter
    );

    /**
     * 获取投票周期
     * @return 投票周期
     */
    JsonVO<BigInteger> getVotingPeriod();

    /**
     * 设置投票周期（需要通过 DAO 提案）
     * @param newPeriod 新的投票周期
     * @return 交易哈希
     */
    JsonVO<String> setVotingPeriod(@RequestParam BigInteger newPeriod);

    /**
     * 获取提案总数
     * @return 提案总数
     */
    JsonVO<BigInteger> getProposalCount();
}
