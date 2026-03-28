// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import './EXTH.sol';
import './Timelock.sol' as TimelockContract;


/**
 * @title Dao - 模块化的DAO治理合约
 * @notice 职责：提案、投票、计票。通过后将提案指令发送给Timelock执行。
 */
contract Dao {
    // --- 状态变量 ---

    TimelockContract.Timelock public immutable timelock; // 指向Timelock执行官
    EXTH public immutable exth;       // 指向投票代币（EXTH）
    uint256 public votingPeriod = 10 minutes; // 投票周期 (可以通过治理来修改)
    uint256 public proposalCount;

    mapping(uint256 => Proposal) public proposals;
    mapping(uint256 => mapping(address => bool)) public hasVoted;

    // --- 数据结构 ---

    enum ProposalState { Pending, Active, Succeeded, Defeated, Queued, Executed, Canceled }

    struct Proposal {
        string description;
        uint256 deadline;
        uint256 forVotes;
        uint256 againstVotes;
        bool executed;
        bool canceled;
        address target;
        uint256 value;
        bytes callData;
        bytes32 timelockId;
        uint256 snapshotBlock;
        uint256 eta; // Expected Time of Arrival (执行时间)
    }

    // --- 事件 ---

    event ProposalCreated(uint256 indexed proposalId, address indexed proposer, address target, string description);
    event VoteCast(uint256 indexed proposalId, address indexed voter, bool support, uint256 weight);
    event ProposalQueued(uint256 indexed proposalId, uint256 eta);
    event ProposalExecuted(uint256 indexed proposalId);
    event ProposalCanceled(uint256 indexed proposalId);

    // --- 构造函数 ---

    constructor(address _token, address payable _timelock) {
        exth = EXTH(_token);
        timelock = TimelockContract.Timelock(_timelock);
    }

    // --- 核心治理流程 ---

    /**
    * 设置投票周期
    */
    function  setVotingPeriod (uint256 newPeriod) external{
    votingPeriod = newPeriod;
    }

    function propose(address _target, uint256 _value, bytes calldata _callData, string calldata _description) external returns (uint256) {
        uint256 proposalId = proposalCount++;
        Proposal storage newProposal = proposals[proposalId];

        newProposal.description = _description;
        newProposal.snapshotBlock = block.number;
        newProposal.deadline = block.timestamp + votingPeriod;
        newProposal.target = _target;
        newProposal.value = _value;
        newProposal.callData = _callData;

        emit ProposalCreated(proposalId, msg.sender, _target, _description);
        return proposalId;
    }

    function vote(uint256 _proposalId, bool _support) external {
        Proposal storage p = proposals[_proposalId];
        require(block.timestamp < p.deadline, "Voting period has ended");
        require(!hasVoted[_proposalId][msg.sender], "Already voted");

        uint256 weight = exth.getPastVotes(msg.sender, p.snapshotBlock);
        require(weight > 0, "No voting power");

        hasVoted[_proposalId][msg.sender] = true;
        if (_support) {
            p.forVotes += weight;
        } else {
            p.againstVotes += weight;
        }

        emit VoteCast(_proposalId, msg.sender, _support, weight);
    }

    function queue(uint256 _proposalId) external {
        Proposal storage p = proposals[_proposalId];
        require(state(_proposalId) == ProposalState.Succeeded, "Proposal not successful");

        uint256 eta = block.timestamp + timelock.minDelay();
        p.eta = eta;

        // 命令 Timelock 将此交易排队（eta 在 queueTransaction 内部计算）
        bytes32 txId = timelock.queueTransaction(p.target, p.value, p.callData);
        p.timelockId = txId;

        // 通过查询获取 eta 值
        p.eta = timelock.timestamps(txId);

        emit ProposalQueued(_proposalId, p.eta);
    }

    function execute(uint256 _proposalId) external payable {
        Proposal storage p = proposals[_proposalId];
        require(state(_proposalId) == ProposalState.Queued, "Proposal not in queue");

        // 命令Timelock执行此交易
        timelock.executeTransaction{value: p.value}(p.target, p.value, p.callData, p.eta);
        p.executed = true;

        emit ProposalExecuted(_proposalId);
    }

    function cancel(uint256 _proposalId) external {
        Proposal storage p = proposals[_proposalId];
        require(state(_proposalId) != ProposalState.Executed, "Cannot cancel executed proposal");

        if (p.timelockId != bytes32(0)) {
            timelock.cancelTransaction(p.target, p.value, p.callData, p.eta);
        }

        p.canceled = true;
        emit ProposalCanceled(_proposalId);
    }

    // --- 查询功能 ---

    function state(uint256 _proposalId) public view returns (ProposalState) {
        Proposal storage p = proposals[_proposalId];
        if (p.executed) return ProposalState.Executed;
        if (p.canceled) return ProposalState.Canceled;
        if (block.timestamp >= p.deadline) {
            if (p.forVotes > p.againstVotes) {
                // 检查是否已在Timelock队列中
                if (p.timelockId == bytes32(0)) {
                    return ProposalState.Succeeded;
                }
                // 检查Timelock中的交易是否到期可执行
                if (block.timestamp >= p.eta) {
                    return ProposalState.Queued; // 严格来说是Ready for Execution
                }
                return ProposalState.Queued;
            } else {
                return ProposalState.Defeated;
            }
        }
        return ProposalState.Active;
    }
}

