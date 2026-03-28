package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.PageDTO;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.ProposalRecordEntity;
import com.mnnu.exception.BusinessException;
import com.mnnu.mapper.ProposalRecordMapper;
import com.mnnu.service.DaoService;
import com.mnnu.wrapper.DaoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.model.Dao;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
/**
 * DAO 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DaoServiceImpl implements DaoService {

    private final DaoWrapper daoWrapper;

    @Autowired
    private ProposalRecordMapper proposalRecordMapper;
    @Autowired
    private Web3j web3j;


    private static final String[] PROPOSAL_STATE_DESC = {
            "待开始",
            "投票中",
            "投票通过",
            "失败",
            "已入队列",
            "已执行",
            "已取消"
    };

    /**
     * 异步保存提案快照到数据库
     */
    private void asyncSaveProposalSnapshot(CreateProposalDTO dto, String txHash, String proposalId, String proposerAddress) {
        CompletableFuture.runAsync(() -> {
            try {
                ProposalRecordEntity record = new ProposalRecordEntity();
                record.setProposalId(proposalId);
                record.setTxHash(txHash);
                record.setDescription(dto.getDescription());
                record.setProposer(proposerAddress);
                record.setTargetContract(dto.getTargetContract());
                record.setValue(dto.getValue() != null ? dto.getValue() : BigDecimal.ZERO);
                record.setCallData(dto.getCallData());
                record.setStatus(0); // 待开始
                record.setCreatedAt(LocalDateTime.now());

                proposalRecordMapper.insert(record);
                log.info("Proposal saved to DB: id={}, tx={}", proposalId, txHash);
            } catch (Exception e) {
                log.warn("Failed to save proposal to DB", e);
            }
        });
    }

    /**
     * 从交易哈希中解析出提案ID
     */
    private BigInteger getProposalIdFromTx(String txHash) throws Exception {
        // 获取交易收据
        EthGetTransactionReceipt ethReceipt = web3j.ethGetTransactionReceipt(txHash).send();
        if (!ethReceipt.getTransactionReceipt().isPresent()) {
            throw new RuntimeException("获取交易收据失败: " + txHash);
        }

        TransactionReceipt receipt = ethReceipt.getTransactionReceipt().get();
        if (receipt == null) {
            throw new RuntimeException("交易收据未找到: " + txHash);
        }

        log.info("交易收据包含 {} 个日志", receipt.getLogs().size());
        for (int i = 0; i < receipt.getLogs().size(); i++) {
            log.info("日志 {}: {}", i, receipt.getLogs().get(i));
        }

        // 解析ProposalCreated事件 - 使用daoWrapper的公共方法
        List<Dao.ProposalCreatedEventResponse> events = daoWrapper.getProposalCreatedEvents(receipt);
        if (events.isEmpty()) {
            throw new RuntimeException("未找到ProposalCreated事件");
        }

        // 返回第一个事件的提案ID
        return events.get(0).proposalId;
    }
    @Override
    public String createProposal(CreateProposalDTO proposalDTO, @CurrentUser String currentAddress) {
        try {
            log.info("Creating proposal: {}", proposalDTO.getDescription());

            byte[] callDataBytes = hexStringToByteArray(proposalDTO.getCallData());

            // 调用链上创建提案
            String txHash = daoWrapper.createProposal(
                    proposalDTO.getTargetContract(),
                    proposalDTO.getValue().toBigInteger(),
                    callDataBytes,
                    proposalDTO.getDescription()
            );

            // 解析事件获取 proposalId（或使用临时ID）
            // 这里假设你有方式从 txHash 获取 proposalId
            BigInteger proposalId = getProposalIdFromTx(txHash); // 需实现此方法

            // 补充：异步持久化，传入提议者地址
            asyncSaveProposalSnapshot(proposalDTO, txHash, proposalId.toString(), currentAddress);

            return txHash;
        } catch (Exception e) {
            log.error("Failed to create proposal", e);
            throw new BusinessException("创建提案失败：" + e.getMessage());
        }
    }


    /**
     * 将十六进制字符串转换为字节数组
     */
    private byte[] hexStringToByteArray(String s) {
        if (s == null || s.isEmpty() || "0x".equals(s)) {
            return new byte[0]; // 返回空数组而不是 null
        }

        // 移除 0x 前缀
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }

        if (s.isEmpty()) {
            return new byte[0];
        }

        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    @Override
    public String vote(BigInteger proposalId, boolean support) {
        try {
            log.info("Voting for proposal {}: {}", proposalId, support);
            return daoWrapper.vote(proposalId, support);
        } catch (Exception e) {
            log.error("Failed to vote for proposal {}", proposalId, e);
            throw new BusinessException("投票失败：" + e.getMessage());
        }
    }

    @Override
    public String queueProposal(BigInteger proposalId) {
        try {
            log.info("Queueing proposal {}", proposalId);
            String txHash = daoWrapper.queueProposal(proposalId);

            // ✅ 异步更新数据库中的 eta 和状态
            CompletableFuture.runAsync(() -> {
                try {
                    // 等待几秒让链上确认
                    Thread.sleep(3000);

                    // 从链上获取最新的提案信息
                    DaoWrapper.ProposalInfo proposalInfo = daoWrapper.getProposal(proposalId);

                    if (proposalInfo.eta != null && proposalInfo.eta.compareTo(BigInteger.ZERO) > 0) {
                        // 查询数据库记录
                        LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                        wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                        ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

                        if (dbRecord != null) {
                            // 更新 eta 和状态
                            dbRecord.setEta(proposalInfo.eta.longValue());
                            dbRecord.setStatus(1); // 公示中
                            dbRecord.setUpdatedAt(LocalDateTime.now());

                            proposalRecordMapper.updateById(dbRecord);
                            log.info("✅ Updated proposal {} with eta: {}", proposalId, proposalInfo.eta);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to update proposal eta after queue: {}", e.getMessage());
                }
            });

            return txHash;
        } catch (Exception e) {
            log.error("Failed to queue proposal {}", proposalId, e);
            throw new BusinessException("加入公示期失败：" + e.getMessage());
        }
    }

    @Override
    public String executeProposal(BigInteger proposalId, BigInteger eta) {
        try {
            log.info("Executing proposal {} with eta {}", proposalId, eta);

            // ✅ 先从链上查询最新的提案信息，获取正确的 eta
            DaoWrapper.ProposalInfo proposalInfo = daoWrapper.getProposal(proposalId);

            if (proposalInfo.eta == null || proposalInfo.eta.compareTo(BigInteger.ZERO) <= 0) {
                throw new BusinessException("提案未设置公示期结束时间 (eta)，请先加入公示期");
            }

            // ✅ 检查当前时间和 eta
            BigInteger currentTime = BigInteger.valueOf(System.currentTimeMillis() / 1000);
            log.info("Current time: {}, Proposal eta: {}", currentTime, proposalInfo.eta);

            if (currentTime.compareTo(proposalInfo.eta) < 0) {
                long remaining = proposalInfo.eta.longValue() - currentTime.longValue();
                long hours = remaining / 3600;
                long minutes = (remaining % 3600) / 60;
                long seconds = remaining % 60;
                throw new BusinessException(String.format(
                        "公示期还未结束！剩余时间：%d 小时%d分钟%d秒",
                        hours, minutes, seconds
                ));
            }

            // ✅ 检查提案状态
            BigInteger state = daoWrapper.getProposalState(proposalId);
            log.info("📋 Proposal {} state: {}", proposalId, state);
            if (state.compareTo(BigInteger.valueOf(4)) != 0) {
                throw new BusinessException("提案状态不正确，当前状态：" + state + "，需要状态：4（已入队列）");
            }

            // ✅ 记录目标合约信息
            log.info("🎯 Target contract: {}", proposalInfo.targetContract);
            log.info("💰 Value: {}", proposalInfo.value);
            log.info("📝 CallData: 0x{}", bytesToHex(proposalInfo.callData));

            // ✅ 执行提案
            String txHash = daoWrapper.executeProposal(proposalId);

            // ✅ 异步更新数据库状态
            CompletableFuture.runAsync(() -> {
                try {
                    // 等待几秒让链上确认
                    Thread.sleep(3000);

                    // 查询数据库记录
                    LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                    ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

                    if (dbRecord != null) {
                        // 更新状态为已执行
                        dbRecord.setStatus(5); // 已执行
                        dbRecord.setUpdatedAt(LocalDateTime.now());

                        proposalRecordMapper.updateById(dbRecord);
                        log.info("✅ Updated proposal {} status to executed", proposalId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to update proposal status after execute: {}", e.getMessage());
                }
            });

            return txHash;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute proposal {}", proposalId, e);
            throw new BusinessException("执行提案失败：" + e.getMessage());
        }
    }

    // 添加辅助方法
    private String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    @Override
    public String cancelProposal(BigInteger proposalId) {
        try {
            log.info("Cancelling proposal {}", proposalId);
            return daoWrapper.cancelProposal(proposalId);
        } catch (Exception e) {
            log.error("Failed to cancel proposal {}", proposalId, e);
            throw new BusinessException("取消提案失败：" + e.getMessage());
        }
    }

    @Override
    public ProposalDTO getProposal(BigInteger proposalId, String address) {
        try {
            DaoWrapper.ProposalInfo proposalInfo = daoWrapper.getProposal(proposalId);

            // ✅ 尝试从数据库获取 proposer 和创建时间
            ProposalRecordEntity dbRecord = null;
            try {
                LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                dbRecord = proposalRecordMapper.selectOne(wrapper);
            } catch (Exception e) {
                log.warn("Failed to query proposal from database: {}", e.getMessage());
            }

            ProposalDTO dto = new ProposalDTO();
            dto.setProposalId(proposalId);
            dto.setDescription(proposalInfo.description);
            dto.setDeadline(proposalInfo.deadline);
            dto.setYesVotes(proposalInfo.yesVotes);
            dto.setNoVotes(proposalInfo.noVotes);
            dto.setExecuted(proposalInfo.executed);
            dto.setQueued(proposalInfo.queued);
            dto.setTargetContract(proposalInfo.targetContract);
            dto.setValue(proposalInfo.value);
            dto.setEta(proposalInfo.eta);
            dto.setSnapshotBlock(proposalInfo.snapshotBlock);

            // ✅ 设置 proposer（从数据库）
            if (dbRecord != null && dbRecord.getProposer() != null) {
                dto.setProposer(dbRecord.getProposer());
            } else {
                dto.setProposer(proposalInfo.proposer);
            }

            // ✅ 计算 startTime = deadline - votingPeriod
            try {
                BigInteger votingPeriod = getVotingPeriod();
                if (votingPeriod != null && proposalInfo.deadline != null) {
                    dto.setStartTime(proposalInfo.deadline.subtract(votingPeriod));
                }
            } catch (Exception e) {
                log.warn("Failed to calculate startTime: {}", e.getMessage());
            }

            int state = daoWrapper.getProposalState(proposalId).intValue();
            dto.setState(state);
            dto.setStateDesc(PROPOSAL_STATE_DESC[state]);

            if (address != null && !address.isEmpty()) {
                dto.setHasVoted(daoWrapper.hasVoted(proposalId, address));
            } else {
                dto.setHasVoted(false);
            }

            return dto;
        } catch (Exception e) {
            log.error("Failed to get proposal {}", proposalId, e);
            throw new BusinessException("获取提案失败：" + e.getMessage());
        }
    }


    @Override
    public PageDTO<ProposalDTO> getAllProposals(Integer pageNum, Integer pageSize, String address) {
        try {
            BigInteger count = daoWrapper.getProposalCount();
            int total = count.intValue();

            int fromIndex = (pageNum - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);

            List<ProposalDTO> rows = new ArrayList<>();

            if (fromIndex < total) {
                for (int i = fromIndex; i < toIndex; i++) {
                    try {
                        ProposalDTO dto = getProposal(BigInteger.valueOf(i), address);
                        rows.add(dto);
                    } catch (Exception e) {
                        log.warn("Failed to load proposal {}, skipping", i, e);
                    }
                }
            }

            PageDTO<ProposalDTO> pageDTO = new PageDTO<>();
            pageDTO.setPageIndex((long) pageNum);
            pageDTO.setPageSize((long) pageSize);
            pageDTO.setTotal((long) total);
            pageDTO.setPages((long) ((total + pageSize - 1) / pageSize));
            pageDTO.setRows(rows);

            return pageDTO;
        } catch (Exception e) {
            log.error("Failed to get all proposals", e);
            throw new BusinessException("获取提案列表失败：" + e.getMessage());
        }
    }

    @Override
    public Integer getProposalState(BigInteger proposalId) {
        try {
            return daoWrapper.getProposalState(proposalId).intValue();
        } catch (Exception e) {
            log.error("Failed to get proposal state {}", proposalId, e);
            throw new BusinessException("获取提案状态失败：" + e.getMessage());
        }
    }

    @Override
    public Boolean hasVoted(BigInteger proposalId, String voter) {
        try {
            return daoWrapper.hasVoted(proposalId, voter);
        } catch (Exception e) {
            log.error("Failed to check voting status for proposal {}", proposalId, e);
            throw new BusinessException("查询投票状态失败：" + e.getMessage());
        }
    }

    @Override
    public BigInteger getVotingPeriod() {
        try {
            return daoWrapper.getVotingPeriod();
        } catch (Exception e) {
            log.error("Failed to get voting period", e);
            throw new BusinessException("获取投票周期失败：" + e.getMessage());
        }
    }

    @Override
    public String setVotingPeriod(BigInteger newPeriod) {
        try {
            log.info("Setting voting period to {}", newPeriod);
            return daoWrapper.setVotingPeriod(newPeriod);
        } catch (Exception e) {
            log.error("Failed to set voting period", e);
            throw new BusinessException("设置投票周期失败：" + e.getMessage());
        }
    }

    @Override
    public BigInteger getProposalCount() {
        try {
            return daoWrapper.getProposalCount();
        } catch (Exception e) {
            log.error("Failed to get proposal count", e);
            throw new BusinessException("获取提案数量失败：" + e.getMessage());
        }
    }
}
