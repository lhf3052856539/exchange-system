package com.mnnu.service.impl;

import com.mnnu.dto.ProposalDTO;
import com.mnnu.exception.BusinessException;
import com.mnnu.service.BlockchainService;
import com.mnnu.service.MultiSigWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSigWalletServiceImpl implements MultiSigWalletService {

    private final BlockchainService blockchainService;
    private final Web3j web3j;
    private final Credentials credentials;

    private static final String CONTRACT_ADDRESS = "0x349EFC3c2f59dAdBaDbd5Fb011Ea92E701Fe3582"; // 部署后填充
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(20_000_000_000L);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(500000);

    @Override
    public String getMultiSigWalletAddress() {
        return CONTRACT_ADDRESS;
    }

    @Override
    public List<String> getCommitteeMembers() {
        try {
            Function function = new Function(
                    "getCommitteeMembers",
                    Arrays.asList(),
                    Arrays.asList(new org.web3j.abi.TypeReference<org.web3j.abi.datatypes.DynamicArray<Address>>() {})
            );

            EthCall ethCall = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            CONTRACT_ADDRESS,
                            FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<String> members = new ArrayList<>();
            List<org.web3j.abi.datatypes.Type> results = FunctionReturnDecoder.decode(
                    ethCall.getValue(),
                    function.getOutputParameters()
            );

            if (!results.isEmpty()) {
                // 修正：从 DynamicArray 中获取值
                org.web3j.abi.datatypes.DynamicArray<Address> dynamicArray =
                        (org.web3j.abi.datatypes.DynamicArray<Address>) results.get(0);

                // 从 DynamicArray 获取实际的地址列表
                List<Address> addresses = dynamicArray.getValue();
                for (Address addr : addresses) {
                    members.add(addr.getValue());
                }
            }

            return members;
        } catch (Exception e) {
            log.error("Failed to get committee members", e);
            throw new BusinessException("Failed to get committee members");
        }
    }

    @Override
    public String createProposal(
            BigInteger tradeId,
            String accusedParty,
            String victimParty,
            BigInteger compensationAmount,
            String reason
    ) {
        try {
            Function function = new Function(
                    "createProposal",
                    Arrays.asList(
                            new Uint256(tradeId),
                            new Address(accusedParty),
                            new Address(victimParty),
                            new Uint256(compensationAmount),
                            new Utf8String(reason)
                    ),
                    Arrays.asList(new org.web3j.abi.TypeReference<Uint256>() {})
            );

            String txHash = sendTransaction(function);
            log.info("Proposal created: txHash={}, tradeId={}", txHash, tradeId);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to create proposal", e);
            throw new BusinessException("Failed to create proposal: " + e.getMessage());
        }
    }

    @Override
    public String voteProposal(BigInteger proposalId, boolean support) {
        try {
            Function function = new Function(
                    "voteProposal",
                    Arrays.asList(
                            new Uint256(proposalId),
                            new org.web3j.abi.datatypes.Bool(support)
                    ),
                    Arrays.asList()
            );

            String txHash = sendTransaction(function);
            log.info("Vote cast: txHash={}, proposalId={}, support={}", txHash, proposalId, support);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to vote proposal", e);
            throw new BusinessException("Failed to vote proposal: " + e.getMessage());
        }
    }

    @Override
    public String executeProposal(BigInteger proposalId) {
        try {
            Function function = new Function(
                    "executeProposal",
                    Arrays.asList(new Uint256(proposalId)),
                    Arrays.asList()
            );

            String txHash = sendTransaction(function);
            log.info("Proposal executed: txHash={}, proposalId={}", txHash, proposalId);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to execute proposal", e);
            throw new BusinessException("Failed to execute proposal: " + e.getMessage());
        }
    }

    @Override
    public ProposalDTO getProposalDetails(BigInteger proposalId) {
        try {
            Function function = new Function(
                    "getProposalDetails",
                    Arrays.asList(new Uint256(proposalId)),
                    Arrays.asList(
                            new org.web3j.abi.TypeReference<Uint256>() {},
                            new org.web3j.abi.TypeReference<Address>() {},
                            new org.web3j.abi.TypeReference<Address>() {},
                            new org.web3j.abi.TypeReference<Uint256>() {},
                            new org.web3j.abi.TypeReference<Utf8String>() {},
                            new org.web3j.abi.TypeReference<Uint256>() {},
                            new org.web3j.abi.TypeReference<org.web3j.abi.datatypes.Bool>() {},
                            new org.web3j.abi.TypeReference<org.web3j.abi.datatypes.Bool>() {},
                            new org.web3j.abi.TypeReference<Uint256>() {}
                    )
            );

            EthCall ethCall = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            CONTRACT_ADDRESS,
                            FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            log.info("EthCall result: {}, hasError: {}", ethCall.getValue(), ethCall.hasError());
            if (ethCall.hasError()) {
                log.error("EthCall failed: {}", ethCall.getError());
                throw new BusinessException("EthCall failed: " + ethCall.getError().getMessage());
            }

            List<org.web3j.abi.datatypes.Type> results = FunctionReturnDecoder.decode(
                    ethCall.getValue(),
                    function.getOutputParameters()
            );

            log.info("Decoded results size: {}", results.size());
            for (int i = 0; i < results.size(); i++) {
                log.info("Result[{}]: type={}, value={}", i, results.get(i).getTypeAsString(), results.get(i).getValue());
            }

            if (results.size() >= 9) {
                ProposalDTO dto = new ProposalDTO();
                dto.setProposalId(proposalId);
                dto.setTradeId(((Uint256) results.get(0)).getValue());
                dto.setAccusedParty(((Address) results.get(1)).getValue());
                dto.setVictimParty(((Address) results.get(2)).getValue());
                dto.setCompensationAmount(((Uint256) results.get(3)).getValue());
                dto.setArbitrationReason(((Utf8String) results.get(4)).getValue());
                dto.setVoteCount(((Uint256) results.get(5)).getValue());
                dto.setExecuted(((org.web3j.abi.datatypes.Bool) results.get(6)).getValue());
                dto.setRejected(((org.web3j.abi.datatypes.Bool) results.get(7)).getValue());
                dto.setDeadline(((Uint256) results.get(8)).getValue());

                // 🔥 使用 deadline 作为 createdAt（因为链上没有创建时间，用截止时间近似）
                dto.setCreatedAt(dto.getDeadline());

                return dto;
            }

            log.error("Invalid proposal data: expected 9 results, got {}", results.size());
            throw new BusinessException("Invalid proposal data");
        } catch (Exception e) {
            log.error("Failed to get proposal details", e);
            throw new BusinessException("Failed to get proposal details");
        }
    }


    @Override
    public boolean isCommitteeMember(String address) {
        try {
            Function function = new Function(
                    "isCommitteeMember",
                    Arrays.asList(new Address(address)),
                    Arrays.asList(new org.web3j.abi.TypeReference<org.web3j.abi.datatypes.Bool>() {})
            );

            EthCall ethCall = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            CONTRACT_ADDRESS,
                            FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<org.web3j.abi.datatypes.Type> results = FunctionReturnDecoder.decode(
                    ethCall.getValue(),
                    function.getOutputParameters()
            );

            return !results.isEmpty() && ((org.web3j.abi.datatypes.Bool) results.get(0)).getValue();
        } catch (Exception e) {
            log.error("Failed to check committee member", e);
            return false;
        }
    }

    private String sendTransaction(Function function) throws Exception {
        String data = FunctionEncoder.encode(function);

        // 获取 nonce
        BigInteger nonce = web3j.ethGetTransactionCount(
                credentials.getAddress(),
                DefaultBlockParameterName.PENDING
        ).send().getTransactionCount();

        log.info("Using nonce: {}", nonce);

        // 创建未签名的交易
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                GAS_PRICE,
                GAS_LIMIT,
                CONTRACT_ADDRESS,
                data
        );

        // 签名交易
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String signedTransaction = Numeric.toHexString(signedMessage);

        // 发送交易
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTransaction).send();

        if (ethSendTransaction.hasError()) {
            throw new BusinessException("Transaction failed: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();
        log.info("Transaction sent: {}", txHash);

        // 等待交易回执，最多重试 5 次
        org.web3j.protocol.core.methods.response.TransactionReceipt receipt = null;
        int retries = 0;
        int maxRetries = 5;
        long waitTime = 2000; // 2 秒

        while (retries < maxRetries && receipt == null) {
            log.info("Waiting for transaction receipt... (attempt {}/{})", retries + 1, maxRetries);
            Thread.sleep(waitTime);

            receipt = web3j.ethGetTransactionReceipt(txHash).send()
                    .getTransactionReceipt()
                    .orElse(null);

            if (receipt == null) {
                retries++;
                waitTime += 1000; // 每次增加 1 秒等待时间
                log.warn("Receipt not found, retrying in {} ms...", waitTime);
            }
        }

        if (receipt == null) {
            log.error("Transaction receipt not found after {} retries. TxHash: {}", maxRetries, txHash);
            throw new BusinessException("Transaction receipt not found");
        }

        log.info("Transaction receipt status: {}", receipt.getStatus());
        if (receipt.getStatus() == null || !receipt.getStatus().equals("0x1")) {
            log.error("Transaction failed on chain! TxHash: {}, Gas used: {}, Revert reason: {}",
                    txHash, receipt.getGasUsed(), receipt.getRevertReason());
            throw new BusinessException("Transaction failed on chain: " + receipt.getRevertReason());
        }

        log.info("Transaction confirmed! Gas used: {}", receipt.getGasUsed());
        return txHash;
    }

    @Override
    public BigInteger getProposalCount() {
        try {
            Function function = new Function(
                    "proposalCount",
                    Arrays.asList(),
                    Arrays.asList(new org.web3j.abi.TypeReference<Uint256>() {})
            );

            EthCall ethCall = web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(),
                            CONTRACT_ADDRESS,
                            FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            List<org.web3j.abi.datatypes.Type> results = FunctionReturnDecoder.decode(
                    ethCall.getValue(),
                    function.getOutputParameters()
            );

            if (!results.isEmpty()) {
                return ((Uint256) results.get(0)).getValue();
            }

            return BigInteger.ZERO;
        } catch (Exception e) {
            log.error("Failed to get proposal count", e);
            throw new BusinessException("Failed to get proposal count");
        }
    }

    @Override
    public List<ProposalDTO> getPendingProposals() {
        try {
            log.info("Getting proposal count...");
            // 获取提案总数
            BigInteger count = getProposalCount();
            log.info("Total proposals on chain: {}", count);

            List<ProposalDTO> pendingList = new ArrayList<>();

            // 遍历所有提案，过滤出待处理的
            for (BigInteger i = BigInteger.ZERO; i.compareTo(count) < 0; i = i.add(BigInteger.ONE)) {
                try {
                    log.info("Fetching proposal details for ID: {}", i);
                    ProposalDTO proposal = getProposalDetails(i);
                    log.info("Proposal {}: executed={}, rejected={}", i, proposal.getExecuted(), proposal.getRejected());

                    // 只返回未执行且未拒绝的提案
                    if (!Boolean.TRUE.equals(proposal.getExecuted()) && !Boolean.TRUE.equals(proposal.getRejected())) {
                        pendingList.add(proposal);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get proposal {}: {}", i, e.getMessage());
                }
            }

            log.info("Returning {} pending proposals", pendingList.size());
            return pendingList;
        } catch (Exception e) {
            log.error("Failed to get pending proposals", e);
            throw new BusinessException("Failed to get pending proposals");
        }
    }

    @Override
    public String sendRawTransaction(String signedTxHex) {
        log.info("Sending raw transaction: {}", signedTxHex);

        // 发送已签名的交易
        EthSendTransaction ethSendTransaction =
                null;
        try {
            ethSendTransaction = web3j.ethSendRawTransaction(signedTxHex).send();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (ethSendTransaction.hasError()) {
            throw new BusinessException("Transaction failed: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();
        log.info("Transaction sent: {}", txHash);

        // 等待交易回执
        log.info("Waiting for transaction receipt...");
        org.web3j.protocol.core.methods.response.TransactionReceipt receipt = null;
        int retries = 0;
        int maxRetries = 5;
        long waitTime = 2000;

        while (retries < maxRetries && receipt == null) {
            log.info("Waiting for transaction receipt... (attempt {}/{})", retries + 1, maxRetries);
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            try {
                receipt = web3j.ethGetTransactionReceipt(txHash).send()
                        .getTransactionReceipt()
                        .orElse(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (receipt == null) {
                retries++;
                waitTime += 1000;
                log.warn("Receipt not found, retrying in {} ms...", waitTime);
            }
        }

        if (receipt == null) {
            log.error("Transaction receipt not found after {} retries. TxHash: {}", maxRetries, txHash);
            throw new BusinessException("Transaction receipt not found");
        }

        log.info("Transaction receipt status: {}", receipt.getStatus());
        if (receipt.getStatus() == null || !receipt.getStatus().equals("0x1")) {
            log.error("Transaction failed on chain! TxHash: {}, Gas used: {}, Revert reason: {}",
                    txHash, receipt.getGasUsed(), receipt.getRevertReason());
            throw new BusinessException("Transaction failed on chain: " + receipt.getRevertReason());
        }

        log.info("Transaction confirmed! Gas used: {}", receipt.getGasUsed());
        return txHash;
    }
    @Override
    public List<ProposalDTO> getHistoryProposals() {
        try {
            log.info("Getting proposal count for history...");
            // 获取提案总数
            BigInteger count = getProposalCount();
            log.info("Total proposals on chain: {}", count);

            List<ProposalDTO> historyList = new ArrayList<>();

            // 遍历所有提案，过滤出已执行或已拒绝的
            for (BigInteger i = BigInteger.ZERO; i.compareTo(count) < 0; i = i.add(BigInteger.ONE)) {
                try {
                    log.info("Fetching proposal details for ID: {}", i);
                    ProposalDTO proposal = getProposalDetails(i);
                    log.info("Proposal {}: executed={}, rejected={}", i, proposal.getExecuted(), proposal.getRejected());

                    // 只返回已执行或已拒绝的提案
                    if (Boolean.TRUE.equals(proposal.getExecuted()) || Boolean.TRUE.equals(proposal.getRejected())) {
                        historyList.add(proposal);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get proposal {}: {}", i, e.getMessage());
                }
            }

            log.info("Returning {} history proposals", historyList.size());
            return historyList;
        } catch (Exception e) {
            log.error("Failed to get history proposals", e);
            throw new BusinessException("Failed to get history proposals");
        }
    }

}