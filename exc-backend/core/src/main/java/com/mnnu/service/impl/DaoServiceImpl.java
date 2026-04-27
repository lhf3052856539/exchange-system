package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mnnu.config.CurrentUser;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.PageDTO;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.ProposalRecordEntity;
import com.mnnu.exception.BusinessException;
import com.mnnu.mapper.ProposalRecordMapper;
import com.mnnu.service.BlockchainService;
import com.mnnu.service.DaoService;
import com.mnnu.wrapper.DaoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.model.EXTH;
import org.web3j.protocol.Web3j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mnnu.constant.SystemConstants.RedisKey.DAO_SYNC_LOCK_KEY_PREFIX;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * DAO 服务实现类
 */
@Slf4j
@Service
public class DaoServiceImpl implements DaoService {

    @Autowired
    private DaoWrapper daoWrapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private ProposalRecordMapper proposalRecordMapper;
    @Autowired
    private Web3j web3j;


    private static final String[] PROPOSAL_STATE_DESC = {
            "提案已创建，等待投票中",
            "投票中",
            "等待加入公示期",
            "提案失败",
            "公示期中",
            "提案已执行",
            "提案已取消"
    };

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
        RLock lock = redissonClient.getLock(SystemConstants.RedisKey.TRADE_LOCK_KEY_PREFIX + "dao:vote:" + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

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
                EXTH exthContract = EXTH.load(
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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    @Override
    public void queueProposal(BigInteger proposalId) {
        RLock lock = redissonClient.getLock(SystemConstants.RedisKey.TRADE_LOCK_KEY_PREFIX + "dao:queue:" + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
            ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

            if (dbRecord == null) {
                throw new BusinessException("Proposal not found");
            }

            int state = dbRecord.getStatus();
            if (state != 1) {
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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    @Override
    public void executeProposal(BigInteger proposalId, BigInteger eta) {
        RLock lock = redissonClient.getLock(SystemConstants.RedisKey.TRADE_LOCK_KEY_PREFIX + "dao:execute:" + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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
    public void cancelProposal(BigInteger proposalId, String callerAddress) {
        RLock lock = redissonClient.getLock(SystemConstants.RedisKey.TRADE_LOCK_KEY_PREFIX + "dao:cancel:" + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    @Override
    public ProposalDTO getProposal(BigInteger proposalId, String address) {
        try {
            ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(
                    new LambdaQueryWrapper<ProposalRecordEntity>().eq(ProposalRecordEntity::getProposalId, proposalId.toString())
            );

            if (dbRecord == null) {
                throw new BusinessException("提案不存在");
            }

            ProposalDTO dto = new ProposalDTO();
            dto.setProposalId(new BigInteger(dbRecord.getProposalId()));
            dto.setDescription(dbRecord.getDescription());
            dto.setProposer(dbRecord.getProposer());
            dto.setYesVotes(dbRecord.getYesVotes().toBigInteger());
            dto.setNoVotes(dbRecord.getNoVotes().toBigInteger());
            dto.setDeadline(BigInteger.valueOf(dbRecord.getDeadline()));
            dto.setState(dbRecord.getStatus());
            dto.setStateDesc(PROPOSAL_STATE_DESC[dbRecord.getStatus()]);
            dto.setTargetContract(dbRecord.getTargetContract());
            dto.setValue(dbRecord.getValue() != null ? dbRecord.getValue().toBigInteger() : BigInteger.ZERO);
            dto.setCallData(dbRecord.getCallData());
            dto.setEta(dbRecord.getEta() != null ? BigInteger.valueOf(dbRecord.getEta()) : null);
            dto.setSnapshotBlock(dbRecord.getSnapshotBlock() != null ? BigInteger.valueOf(dbRecord.getSnapshotBlock()) : null);

            if (dbRecord.getCreatedAt() != null) {
                dto.setStartTime(BigInteger.valueOf(dbRecord.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()));
            }

            dto.setHasVoted(false);

            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get proposal details", e);
            throw new BusinessException("获取提案详情失败");
        }
    }



    @Override
    public PageDTO<ProposalDTO> getAllProposals(Integer pageNum, Integer pageSize, String address) {
        try {
            LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(ProposalRecordEntity::getId);

            Page<ProposalRecordEntity> page = new Page<>(pageNum, pageSize);
            Page<ProposalRecordEntity> resultPage = proposalRecordMapper.selectPage(page, wrapper);

            List<ProposalDTO> rows = new ArrayList<>();
            for (ProposalRecordEntity entity : resultPage.getRecords()) {
                ProposalDTO dto = new ProposalDTO();
                dto.setProposalId(new BigInteger(entity.getProposalId()));
                dto.setDescription(entity.getDescription());
                dto.setProposer(entity.getProposer());
                dto.setTargetContract(entity.getTargetContract());
                dto.setValue(entity.getValue() != null ? entity.getValue().toBigInteger() : BigInteger.ZERO);
                dto.setCallData(entity.getCallData());
                dto.setYesVotes(entity.getYesVotes() != null ? entity.getYesVotes().toBigInteger() : BigInteger.ZERO);
                dto.setNoVotes(entity.getNoVotes() != null ? entity.getNoVotes().toBigInteger() : BigInteger.ZERO);
                dto.setDeadline(BigInteger.valueOf(entity.getDeadline()));
                dto.setState(entity.getStatus());
                dto.setStateDesc(PROPOSAL_STATE_DESC[entity.getStatus()]);
                dto.setEta(entity.getEta() != null ? BigInteger.valueOf(entity.getEta()) : null);
                dto.setSnapshotBlock(entity.getSnapshotBlock() != null ? BigInteger.valueOf(entity.getSnapshotBlock()) : null);

                rows.add(dto);
            }

            PageDTO<ProposalDTO> pageDTO = new PageDTO<>();
            pageDTO.setPageIndex(resultPage.getCurrent());
            pageDTO.setPageSize(resultPage.getSize());
            pageDTO.setTotal(resultPage.getTotal());
            pageDTO.setPages(resultPage.getPages());
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
    @Transactional(rollbackFor = Exception.class)
    public boolean syncProposalFromChain(String proposalId, String proposer, String txHash) {
        RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                log.warn("Sync proposal {} skipped (locked)", proposalId);
                return false;
            }

            // 先检查是否已存在
            LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProposalRecordEntity::getProposalId, proposalId);
            ProposalRecordEntity existing = proposalRecordMapper.selectOne(wrapper);

            if (existing != null) {
                log.info("Proposal {} already exists, skip sync", proposalId);
                return true;
            }

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
                    ZoneOffset.ofHours(8)
            ));

            int insertCount = proposalRecordMapper.insert(record);

            if (insertCount > 0) {
                log.info("Proposal {} inserted from chain", proposalId);
                return true;
            } else {
                log.warn("Failed to insert proposal {}", proposalId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sync proposal {} interrupted", proposalId, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to sync proposal from chain: {}", proposalId, e);
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean syncProposalVotesFromChain(BigInteger proposalId) {
        RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                log.warn("Sync votes for proposal {} skipped (locked)", proposalId);
                return false;
            }

            DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);
            LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
            ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

            if (dbRecord == null) {
                log.warn("Proposal record not found for ID: {}", proposalId);
                return false;
            }

            dbRecord.setYesVotes(new BigDecimal(info.yesVotes));
            dbRecord.setNoVotes(new BigDecimal(info.noVotes));
            dbRecord.setStatus(info.status);

            int updateCount = proposalRecordMapper.updateById(dbRecord);

            if (updateCount > 0) {
                log.info("Votes synced for proposal {}: yes={}, no={}", proposalId, info.yesVotes, info.noVotes);
                return true;
            } else {
                log.warn("Failed to update votes for proposal {}", proposalId);
                return false;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sync votes for proposal {} interrupted", proposalId, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to sync votes for proposal {}", proposalId, e);
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
