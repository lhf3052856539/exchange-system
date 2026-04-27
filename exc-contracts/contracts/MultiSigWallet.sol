// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import "@openzeppelin/contracts-5.1.0/access/Ownable.sol";
import "@openzeppelin/contracts-5.1.0/utils/ReentrancyGuard.sol";
import "./Exchange.sol";
import "./Treasure.sol";

/**
 * @title MultiSigWallet - 多签钱包仲裁合约
 * @notice Gnosis Safe 风格的多签钱包，用于仲裁交易争议
 * @dev 3 人委员会，至少 2 人签名才能执行操作
 */
contract MultiSigWallet is Ownable, ReentrancyGuard {

    uint256 public constant REQUIRED_SIGNATURES = 2;
    uint256 public constant MAX_COMMITTEE_SIZE = 3;
    uint256 public constant VOTING_PERIOD = 10 minutes;

    Exchange public exchangeContract;
    Treasure public treasureContract;
    IERC20 public usdtToken;
    IERC20 public exthToken;


    mapping(address => bool) public isCommitteeMember;
    address[] public committeeMembers;

    struct Proposal {
        uint256 tradeId;
        address accusedParty;
        address victimParty;
        uint256 compensationAmount;
        string reason;
        uint256 voteCount;
        uint256 rejectCount;
        mapping(address => bool) hasVoted;
        uint8 status;
        uint256 createdAt;
        uint256 deadline;
    }

    mapping(uint256 => Proposal) public proposals;
    uint256 public proposalCount;

    event CommitteeMemberAdded(address indexed member);
    event CommitteeMemberRemoved(address indexed member);
    event ProposalCreated(uint256 indexed proposalId, uint256 indexed tradeId, address indexed accusedParty);
    event VoteCast(uint256 indexed proposalId, address indexed voter, bool support);
    event ProposalExecuted(uint256 indexed proposalId, address indexed accusedParty, address indexed victimParty);
    event ProposalRejected(uint256 indexed proposalId);
    event ProposalExpired(uint256 indexed proposalId);

    constructor(
        address _treasureContract,
        address _usdtAddress,
        address _exthAddress,
        address[] memory initialCommittee
    ) Ownable(msg.sender) {
        require(initialCommittee.length == MAX_COMMITTEE_SIZE, "Need exactly 3 committee members");

        treasureContract = Treasure(payable(_treasureContract));
        usdtToken = IERC20(_usdtAddress);
        exthToken = IERC20(_exthAddress);

        for (uint256 i = 0; i < initialCommittee.length; i++) {
            require(initialCommittee[i] != address(0), "Invalid committee member address");
            require(!isCommitteeMember[initialCommittee[i]], "Duplicate committee member");
            isCommitteeMember[initialCommittee[i]] = true;
            committeeMembers.push(initialCommittee[i]);
            emit CommitteeMemberAdded(initialCommittee[i]);
        }
    }

    function setExchangeContract(address _exchangeContract) external onlyOwner {
        exchangeContract = Exchange(_exchangeContract);
    }

    function addCommitteeMember(address _member) external onlyOwner {
        require(committeeMembers.length < MAX_COMMITTEE_SIZE, "Committee full");
        require(!isCommitteeMember[_member], "Already a member");
        require(_member != address(0), "Invalid address");

        isCommitteeMember[_member] = true;
        committeeMembers.push(_member);
        emit CommitteeMemberAdded(_member);
    }

    function removeCommitteeMember(address _member) external onlyOwner {
        require(isCommitteeMember[_member], "Not a committee member");
        isCommitteeMember[_member] = false;

        for (uint256 i = 0; i < committeeMembers.length; i++) {
            if (committeeMembers[i] == _member) {
                committeeMembers[i] = committeeMembers[committeeMembers.length - 1];
                committeeMembers.pop();
                break;
            }
        }
        emit CommitteeMemberRemoved(_member);
    }

    function createProposal(
        uint256 _tradeId,
        address _accusedParty,
        address _victimParty,
        uint256 _compensationAmount,
        string calldata _reason
    ) external returns (uint256) {
        require(isCommitteeMember[msg.sender], "Not a committee member");
        require(_accusedParty != address(0) && _victimParty != address(0), "Invalid addresses");
        require(_compensationAmount > 0, "Amount must be greater than 0");

        uint256 proposalId = proposalCount++;
        Proposal storage proposal = proposals[proposalId];

        proposal.tradeId = _tradeId;
        proposal.accusedParty = _accusedParty;
        proposal.victimParty = _victimParty;
        proposal.compensationAmount = _compensationAmount;
        proposal.reason = _reason;
        proposal.status = 0;//仲裁提案已创建，等待投票中
        proposal.createdAt = block.timestamp;
        proposal.deadline = block.timestamp + VOTING_PERIOD;

        emit ProposalCreated(proposalId, _tradeId, _accusedParty);
        return proposalId;
    }

    function voteProposal(uint256 _proposalId, bool _support) external {
        require(isCommitteeMember[msg.sender], "Not a committee member");
        require(_proposalId < proposalCount, "Invalid proposal ID");

        Proposal storage proposal = proposals[_proposalId];
        require(proposal.status == 0 || proposal.status == 1, "Proposal already resolved");
        require(block.timestamp < proposal.deadline, "Voting period ended");
        require(!proposal.hasVoted[msg.sender], "Already voted");

        proposal.hasVoted[msg.sender] = true;

        if(proposal.status == 0){
            proposal.status = 1;//投票中
        }

        if (_support) {
            proposal.voteCount++;
            if (proposal.voteCount >= REQUIRED_SIGNATURES) {
                executeProposal(_proposalId);
            }
        }else {
            proposal.rejectCount++;
            // 反对票达到阈值（2票），立即拒绝
            if (proposal.rejectCount >= REQUIRED_SIGNATURES) {
                proposal.status = 2;//仲裁提案被驳回
                // 同步更新 Exchange 合约中的争议状态为"争议请求已驳回"
                exchangeContract.markTradeAsResolved(proposal.tradeId, 3);
                emit ProposalRejected(_proposalId);
            }
        }

        emit VoteCast(_proposalId, msg.sender, _support);
    }
    /**
     * @notice finalize 提案：处理超时未通过的提案
     * @dev 任何人都可以调用此函数来“清理”过期的提案，将其状态改为 4-仲裁提案已过期
     */
    function finalizeProposal(uint256 _proposalId) external {
        require(_proposalId < proposalCount, "Invalid proposal ID");
        Proposal storage proposal = proposals[_proposalId];

        require(proposal.status == 0 || proposal.status == 1, "Already finalized");
        require(block.timestamp > proposal.deadline, "Voting still active");

        //时间到了，票数不够，直接过期
        proposal.status = 4;//仲裁提案已过期

        // 同步更新 Exchange 合约中的争议状态为"争议已过期"
        exchangeContract.markTradeAsResolved(proposal.tradeId, 4);
        emit ProposalExpired(_proposalId);
    }

    function executeProposal(uint256 _proposalId) public nonReentrant {
        require(_proposalId < proposalCount, "Invalid proposal ID");
        Proposal storage proposal = proposals[_proposalId];
        require(proposal.status == 1, "Proposal already resolved");
        require(proposal.voteCount >= REQUIRED_SIGNATURES, "Insufficient votes");

        // 标记为已执行，防止重入攻击重复执行
        proposal.status = 3;//仲裁提案已执行
        // 同步更新 Exchange 合约中的争议状态为"争议请求已执行"
        exchangeContract.markTradeAsResolved(proposal.tradeId,2);

        exchangeContract.blacklistUser(proposal.accusedParty);

        // 直接调用金库的专门赔付函数
        treasureContract.payCompensation(
            address(usdtToken),
            proposal.victimParty,
            proposal.compensationAmount
        );

        emit ProposalExecuted(_proposalId, proposal.accusedParty, proposal.victimParty);
    }

    function getProposalDetails(uint256 _proposalId) external view returns (
        uint256 tradeId,
        address accusedParty,
        address victimParty,
        uint256 compensationAmount,
        string memory reason,
        uint256 voteCount,
        uint256 rejectCount,
        uint8 status,
        uint256 createdAt,
        uint256 deadline
    ) {
        require(_proposalId < proposalCount, "Invalid proposal ID");
        Proposal storage proposal = proposals[_proposalId];
        return (
        proposal.tradeId,
        proposal.accusedParty,
        proposal.victimParty,
        proposal.compensationAmount,
        proposal.reason,
        proposal.voteCount,
        proposal.rejectCount,
        proposal.status,
        proposal.createdAt,
        proposal.deadline
        );
    }

    function getCommitteeMembers() external view returns (address[] memory) {
        return committeeMembers;
    }
}
