package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.PageDTO;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.ProposalRecordEntity;
import com.mnnu.exception.BusinessException;
import com.mnnu.mapper.ProposalRecordMapper;
import com.mnnu.service.BlockchainService;
import com.mnnu.service.DaoService;
import com.mnnu.wrapper.DaoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.model.Dao;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
/**
 * DAO 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DaoServiceImpl implements DaoService {

    private final DaoWrapper daoWrapper;

    private final BlockchainService blockchainService;


    @Autowired
    private ProposalRecordMapper proposalRecordMapper;
    @Autowired
    private Web3j web3j;


    private static final String[] PROPOSAL_STATE_DESC = {
            "待开始",
            "投票中",
            "投票通过",
            "投票失败",
            "已加入队列",
            "已执行",
            "已取消"
    };

    /**
     * 异步保存提案快照到数据库
     */
    private void asyncSaveProposalSnapshot(CreateProposalDTO dto, String txHash, String proposalId, String proposerAddress) {
        CompletableFuture.runAsync(() -> {
            try {
                // 等待几秒让链上确认
                Thread.sleep(3000);

                // 从链上获取最新的提案信息
                DaoWrapper.ProposalInfo proposalInfo = daoWrapper.getProposal(new BigInteger(proposalId));

                ProposalRecordEntity record = new ProposalRecordEntity();
                record.setProposalId(proposalId);
                record.setTxHash(txHash);
                record.setDescription(dto.getDescription());
                record.setProposer(proposerAddress);
                record.setTargetContract(dto.getTargetContract());
                record.setValue(dto.getValue() != null ? dto.getValue() : BigDecimal.ZERO);
                record.setCallData(dto.getCallData());
                record.setStatus(0); // 待开始

                // 同步链上数据到数据库
                record.setYesVotes(new BigDecimal(proposalInfo.yesVotes != null ? proposalInfo.yesVotes : BigInteger.ZERO));
                record.setNoVotes(new BigDecimal(proposalInfo.noVotes != null ? proposalInfo.noVotes : BigInteger.ZERO));
                record.setDeadline(proposalInfo.deadline != null ? proposalInfo.deadline.longValue() : null);
                record.setSnapshotBlock(proposalInfo.snapshotBlock != null ? proposalInfo.snapshotBlock.longValue() : null);

                record.setCreatedAt(LocalDateTime.now());

                proposalRecordMapper.insert(record);
                log.info(" Proposal saved to DB with chain data: id={}, txHash={}, deadline={}, yesVotes={}, noVotes={}",
                        proposalId, txHash, record.getDeadline(),
                        record.getYesVotes(), record.getNoVotes());
            } catch (Exception e) {
                log.warn(" Failed to save proposal to DB", e);
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
    public void createProposal(CreateProposalDTO proposalDTO, @CurrentUser String currentAddress) {
        // 基础参数校验
        if (proposalDTO.getTargetContract() == null || proposalDTO.getTargetContract().equals("0x0000000000000000000000000000000000000000")) {
            throw new BusinessException("Target contract address cannot be zero");
        }
        if (proposalDTO.getDescription() == null || proposalDTO.getDescription().isEmpty()) {
            throw new BusinessException("Description cannot be empty");
        }

        // CallData 格式校验
        try {
            hexStringToByteArray(proposalDTO.getCallData());
        } catch (Exception e) {
            throw new BusinessException("Invalid callData format");
        }

        log.info("DAO proposal pre-check passed for user: {}", currentAddress);
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
    public void vote(BigInteger proposalId, boolean support, String voterAddress) {
        LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
        ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

        if (dbRecord == null) {
            throw new BusinessException("Proposal not found");
        }

        int state = dbRecord.getStatus();
        if (state != 1) {
            throw new BusinessException("Proposal is not active. Current state: " + PROPOSAL_STATE_DESC[state]);
        }

        if (dbRecord.getDeadline() != null) {
            long now = System.currentTimeMillis() / 1000;
            if (now >= dbRecord.getDeadline()) {
                throw new BusinessException("Voting period has ended");
            }
        }

        try {
            Boolean alreadyVoted = daoWrapper.hasVoted(proposalId, voterAddress);
            if (alreadyVoted) {
                throw new BusinessException("Already voted");
            }
            String exthAddress = blockchainService.getExthContractAddress();
            org.web3j.model.EXTH exthContract = org.web3j.model.EXTH.load(
                    exthAddress,
                    web3j,
                    (Credentials) null,
                    null
            );

            BigInteger votingPower = exthContract.getVotes(voterAddress).send();
            if (votingPower.compareTo(BigInteger.ZERO) <= 0) {
                throw new BusinessException("No voting power. Please delegate your EXTH tokens first");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check voting eligibility", e);
        }

        log.info("DAO vote pre-check passed for proposal: {}, voter: {}", proposalId, voterAddress);
    }


    @Override
    public void queueProposal(BigInteger proposalId) {
        LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
        ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

        if (dbRecord == null) {
            throw new BusinessException("Proposal not found");
        }

        int state = dbRecord.getStatus();
        if (state != 2) {
            throw new BusinessException("Proposal must succeed before queuing. Current state: " + PROPOSAL_STATE_DESC[state]);
        }

        if (dbRecord.getDeadline() != null) {
            long now = System.currentTimeMillis() / 1000;
            if (now < dbRecord.getDeadline()) {
                throw new BusinessException("Voting period has not ended yet");
            }
        }

        if (dbRecord.getYesVotes().compareTo(dbRecord.getNoVotes()) <= 0) {
            throw new BusinessException("Proposal did not pass the vote");
        }

        log.info("DAO queue pre-check passed for proposal: {}", proposalId);
    }



    @Override
    public void executeProposal(BigInteger proposalId, BigInteger eta) {
        LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
        ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

        if (dbRecord == null) {
            throw new BusinessException("Proposal not found");
        }

        int state = dbRecord.getStatus();
        if (state != 4) {
            throw new BusinessException("Proposal is not queued. Current state: " + PROPOSAL_STATE_DESC[state]);
        }

        long now = System.currentTimeMillis() / 1000;
        if (dbRecord.getDeadline() != null && now < dbRecord.getDeadline()) {
            long waitSeconds = dbRecord.getDeadline() - now;
            throw new BusinessException("Timelock delay not met. Wait " + waitSeconds + " seconds");
        }

        log.info("DAO execute pre-check passed for proposal: {}", proposalId);
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
    public void cancelProposal(BigInteger proposalId, String callerAddress) {
        LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
        ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

        if (dbRecord == null) {
            throw new BusinessException("Proposal not found");
        }

        int state = dbRecord.getStatus();
        if (state != 0 && state != 1) {
            throw new BusinessException("Can only cancel pending or active proposals. Current state: " + PROPOSAL_STATE_DESC[state]);
        }

        if (!callerAddress.equalsIgnoreCase(dbRecord.getProposer())) {
            throw new BusinessException("Only the proposer can cancel the proposal");
        }

        log.info("DAO cancel pre-check passed for proposal: {}, caller: {}", proposalId, callerAddress);
    }



    @Override
    public ProposalDTO getProposal(BigInteger proposalId, String address) {
        try {
            // 优先从数据库获取基础信息
            ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(
                    new LambdaQueryWrapper<ProposalRecordEntity>().eq(ProposalRecordEntity::getProposalId, proposalId.toString())
            );

            // 从链上获取实时状态和票数
            DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);
            int state = daoWrapper.getProposalState(proposalId).intValue();

            ProposalDTO dto = new ProposalDTO();
            dto.setProposalId(proposalId);
            dto.setDescription(info.description != null ? info.description : (dbRecord != null ? dbRecord.getDescription() : ""));
            dto.setProposer(dbRecord != null ? dbRecord.getProposer() : info.proposer);
            dto.setYesVotes(info.yesVotes);
            dto.setNoVotes(info.noVotes);
            dto.setDeadline(info.deadline);
            dto.setState(state);
            dto.setStateDesc(PROPOSAL_STATE_DESC[state]);

            // 计算公示期剩余时间
            if (state == 4 && info.eta != null) {
                long remaining = info.eta.longValue() - (System.currentTimeMillis() / 1000);
                dto.setEta(info.eta);
                // 可以在 DTO 里加个字段存剩余秒数
            }

            if (address != null) {
                dto.setHasVoted(daoWrapper.hasVoted(proposalId, address));
            }

            return dto;
        } catch (Exception e) {
            log.error("Failed to get proposal details", e);
            throw new BusinessException("获取提案详情失败");
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

    @Override
    public Map<String, Object> getTreasureBalance() {
        try {
            Map<String, Object> balanceMap = new HashMap<>();

            // 获取 Treasure 合约地址
            String treasureAddress = blockchainService.getTreasureContractAddress();
            if (treasureAddress == null || treasureAddress.isEmpty()) {
                throw new BusinessException("Treasure 合约地址未配置");
            }

            // 获取 ETH 余额
            BigDecimal ethBalance = blockchainService.getBalance(treasureAddress, "ETH");
            balanceMap.put("ETH", ethBalance);

            // 获取 USDT 余额
            try {
                String usdtAddress = blockchainService.getUsdtContractAddress();
                if (usdtAddress != null && !usdtAddress.isEmpty()) {
                    BigDecimal usdtBalance = blockchainService.getBalance(treasureAddress, "USDT");
                    balanceMap.put("USDT", usdtBalance);
                }
            } catch (Exception e) {
                log.warn("Failed to get USDT balance: {}", e.getMessage());
                balanceMap.put("USDT", BigDecimal.ZERO);
            }

            // 获取 EXTH 余额
            try {
                String exthAddress = blockchainService.getExthContractAddress();
                if (exthAddress != null && !exthAddress.isEmpty()) {
                    BigDecimal exthBalance = blockchainService.getBalance(treasureAddress, "EXTH");
                    balanceMap.put("EXTH", exthBalance);
                }
            } catch (Exception e) {
                log.warn("Failed to get EXTH balance: {}", e.getMessage());
                balanceMap.put("EXTH", BigDecimal.ZERO);
            }

            // 获取多签钱包地址（如果已配置）
            try {
                String multiSigAddress = blockchainService.getMultiSigWalletAddress();
                if (multiSigAddress != null && !multiSigAddress.isEmpty()) {
                    balanceMap.put("MultiSigWallet", multiSigAddress);
                }
            } catch (Exception e) {
                log.warn("MultiSigWallet not configured: {}", e.getMessage());
            }

            log.info("Treasure balance queried: {}", balanceMap);
            return balanceMap;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get treasure balance", e);
            throw new BusinessException("获取金库余额失败：" + e.getMessage());
        }
    }

    @Override
    public void syncProposalFromChain(String proposalId, String proposer, String txHash) {
        try {
            BigInteger id = new BigInteger(proposalId);
            DaoWrapper.ProposalInfo info = daoWrapper.getProposal(id);

            ProposalRecordEntity record = new ProposalRecordEntity();
            record.setProposalId(proposalId);
            record.setTxHash(txHash);
            record.setProposer(proposer);

            // 从链上获取初始参数
            record.setDescription(info.description);
            record.setTargetContract(info.targetContract);
            record.setValue(new BigDecimal(info.value != null ? info.value : BigInteger.ZERO));
            record.setCallData(bytesToHex(info.callData));

            //  初始状态
            record.setStatus(info.status);
            record.setYesVotes(BigDecimal.ZERO);
            record.setNoVotes(BigDecimal.ZERO);
            record.setDeadline(info.deadline.longValue());
            record.setSnapshotBlock(info.snapshotBlock != null ? info.snapshotBlock.longValue() : null);
            record.setCreatedAt(LocalDateTime.ofEpochSecond(
                    info.deadline.longValue() - daoWrapper.getVotingPeriod().longValue(),
                    0,
                    java.time.ZoneOffset.UTC
            ));

                proposalRecordMapper.updateById(record);
                log.info(" Proposal {} updated from chain", proposalId);
        } catch (Exception e) {
            log.error("Failed to sync proposal from chain: {}", proposalId, e);
        }
    }

    @Override
    public void syncProposalVotesFromChain(BigInteger proposalId) {
        try {
            DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);
            LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
            ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

            dbRecord.setYesVotes(new BigDecimal(info.yesVotes));
            dbRecord.setNoVotes(new BigDecimal(info.noVotes));
            dbRecord.setStatus(info.status);

            proposalRecordMapper.updateById(dbRecord);
            log.info("Votes synced for proposal {}: yes={}, no={}", proposalId, info.yesVotes, info.noVotes);

        } catch (Exception e) {
            log.error("Failed to sync votes for proposal {}", proposalId, e);
        }
    }

}