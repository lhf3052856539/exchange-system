// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import '@openzeppelin/contracts-5.1.0/token/ERC20/ERC20.sol';
import '@openzeppelin/contracts-5.1.0/access/Ownable.sol';
import '@openzeppelin/contracts-5.1.0/utils/ReentrancyGuard.sol';
import './EXTH.sol';
import './USDT.sol';

/**
 * @title 去中心化兑换系统合约
 * @notice 实现P2P兑换交易，包含用户分级、手续费、争议处理等功能
 */
contract Exchange is Ownable, ReentrancyGuard {

    // ==================== 状态变量 ====================

    EXTH public exthToken;
    USDT public usdtToken;

    // 手续费：万分之一
    uint256 public constant FEE_RATE = 1;
    uint256 public constant FEE_DENOMINATOR = 10000;

    // 交易额度限制
    uint256 public constant MIN_UT = 1; // 1 UT = 100 USD
    uint256 public constant MAX_UT = 70; // 70 UT

    // 用户类型枚举
    enum UserType { NEW, NORMAL, SEED }

    // 用户结构体
    struct User {
        UserType userType;
        uint256 newUserTradeCount; // 新用户还需要作为率先转账方的次数
        uint256 exthBalance; // 缓存的EXTH余额，用于计算可交易额度
        uint256 lastUpdateTime; // 最后更新时间
        bool isBlacklisted; // 是否被拉黑
    }

    // 交易对结构体
    struct TradePair {
        address partyA; // 率先转账方
        address partyB; // 履约方
        uint256 amount; // 交易额度（UT）
        uint256 exthReward; // EXTH奖励数量
        uint256 timestamp;
        bool isCompleted;
        bool isDisputed;
        address disputedParty; // 被争议方
    }

    // 奖励减半相关
    uint256 public constant INITIAL_REWARD = 0.05 * 10**6; // 0.05 EXTH (精度6)
    uint256 public constant REWARD_HALVING_INTERVAL = 100_000_000; // 1亿 UT
    uint256 public totalUTVolume; // 总交易量
    uint256 public currentReward = INITIAL_REWARD; // 当前奖励数量

    // 用户映射
    mapping(address => User) public users;

    // 交易对映射
    mapping(uint256 => TradePair) public tradePairs;
    uint256 public tradePairCounter;



    // 事件
    event UserUpgraded(address indexed user, UserType newType);
    event TradeMatched(uint256 indexed tradeId, address indexed partyA, address indexed partyB, uint256 amount);
    event TradeCompleted(uint256 indexed tradeId);
    event TradeDisputed(uint256 indexed tradeId, address indexed disputedParty);
    event UserBlacklisted(address indexed user);
    event RewardUpdated(uint256 newReward, uint256 totalUTVolume);
    event MatchRequested(bytes32 indexed requestId, address indexed user, uint256 amount, uint256 timestamp);
    event FeeCollected(uint256 indexed tradeId, address indexed feePayerA,address indexed feePayerB, uint256 feeAmount);

    // ==================== 修饰器 ====================

    modifier notBlacklisted(address user) {
        require(!users[user].isBlacklisted, unicode"用户在黑名单中！");
        _;
    }


    // ==================== 构造函数 ====================

    constructor(address _exthToken, address _usdtToken) Ownable(msg.sender) {
        exthToken = EXTH(_exthToken);
        usdtToken = USDT(_usdtToken);
    }

    // ==================== 用户管理函数 ====================

    /**
     * @notice 更新用户类型（基于 EXTH 余额）
     * @dev 当用户账户中的 EXTH 余额为 900 时，晋升为种子用户
     * @param user 用户地址
     */
    function updateUserType(address user) public {
        User storage userData = users[user];

        uint256 exthBalance = exthToken.balanceOf(user);
        userData.exthBalance = exthBalance;

        if (exthBalance >= 900 * 10**6) { // 900 EXTH (精度 6)
            if (userData.userType != UserType.SEED) {
                userData.userType = UserType.SEED;
                emit UserUpgraded(user, UserType.SEED);
            }
        } else if (userData.userType == UserType.NEW) {
            // 新用户且余额不足 900，升级为 NORMAL
            userData.userType = UserType.NORMAL;
            emit UserUpgraded(user, UserType.NORMAL);
        } else {
            // 普通用户但余额不足，降级为 NORMAL
            if (userData.userType != UserType.NORMAL) {
                userData.userType = UserType.NORMAL;
                emit UserUpgraded(user, UserType.NORMAL);
            }
        }
    }


    /**
     * @notice 计算用户可交易额度
     * @dev 每个人可以交易的 UT 为 1 和当前账户所拥有的 EXTH 的 10 倍这两个数之间的最大值，存在一个上下限，最低为 1UT，最高为 70UT
     * @param user 用户地址
     * @return 可交易的 UT 数量
     */
    function getTradeableUT(address user) public view returns (uint256) {
        uint256 exthBalance = exthToken.balanceOf(user);

        uint256 exthBased = (exthBalance / 10**6) * 10;

        uint256 tradeable = exthBased > MIN_UT ? exthBased : MIN_UT;

        if (tradeable > MAX_UT) {
            tradeable = MAX_UT;
        }

        return tradeable;
    }

    // ==================== 交易匹配函数 ====================

    /**
     * @notice 请求交易匹配
     * @dev 后端实现实际的匹配逻辑，合约只记录匹配结果
     * @param amount 交易金额（UT）
     */
    function requestMatch(uint256 amount) external notBlacklisted(msg.sender) nonReentrant {
        require(amount >= MIN_UT && amount <= MAX_UT, unicode"金额无效！");
        require(amount <= getTradeableUT(msg.sender), unicode"金额超出交易限制！");

        // 生成请求哈希
        bytes32 requestId = keccak256(abi.encodePacked(msg.sender, amount, block.timestamp));

        // 触发事件让后端监听
        emit MatchRequested(requestId, msg.sender, amount, block.timestamp);
        emit TradeMatched(0, address(0), msg.sender, amount); // 注：tradeId 为 0 表示等待匹配

    }

    /**
     * @notice 创建交易对（由后端调用）
     * @param partyA 率先转账方
     * @param partyB 履约方
     * @param amount 交易金额（UT）
     * @return 交易对 ID
     */
    function createTradePair(
        address partyA,
        address partyB,
        uint256 amount
    ) external  notBlacklisted(partyA) notBlacklisted(partyB) returns (uint256) {

        // 简化：不再检查用户类型和新手次数，完全信任后端传入的参数
        // 后端已经决定了谁是 partyA（率先转账方）

        // 创建交易对
        tradePairCounter++;
        tradePairs[tradePairCounter] = TradePair({
        partyA: partyA,
        partyB: partyB,
        amount: amount,
        exthReward: currentReward,
        timestamp: block.timestamp,
        isCompleted: false,
        isDisputed: false,
        disputedParty: address(0)
        });

        emit TradeMatched(tradePairCounter, partyA, partyB, amount);

        return tradePairCounter;
    }


    /**
     * @notice 完成交易（由后端调用）
     * @param tradeId 交易 ID
     * @return 是否成功完成
     */
    function completeTrade(uint256 tradeId) external  nonReentrant returns (bool) {
        require(tradeId > 0 && tradeId <= tradePairCounter, unicode"无效的交易 ID！");
        TradePair storage trade = tradePairs[tradeId];
        require(!trade.isCompleted, unicode"交易已完成！");
        require(!trade.isDisputed, unicode"交易存在争议！");

        // 标记为已完成
        trade.isCompleted = true;
        emit TradeCompleted(tradeId);
        // 移除：不再减少 newUserTradeCount，这个逻辑移到链下处理
        return true;
    }

    /**
     * @notice 收取手续费（由后端调用，在交易完成后）
     * @dev 用户需要先通过 EXTH 合约的 approve 函数授权给本合约
     * @param tradeId 交易 ID
     * @param feeAmount 手续费金额（EXTH）
     */
    function collectFee(uint256 tradeId, uint256 feeAmount) external nonReentrant {
        require(tradeId > 0 && tradeId <= tradePairCounter, unicode"无效的交易 ID！");
        TradePair storage trade = tradePairs[tradeId];
        require(trade.isCompleted, unicode"交易未完成！");
        require(feeAmount > 0, unicode"手续费必须大于 0！");

        // 从 partyA（率先转账方）收取手续费
        address feePayerA = trade.partyA;
        // 从 partyB（履约方）收取手续费
        address feePayerB = trade.partyB;

        // 检查授权额度是否足够
        require(exthToken.allowance(feePayerA, address(this)) >= feeAmount, unicode"授权额度不足！");
        require(exthToken.allowance(feePayerB, address(this)) >= feeAmount, unicode"授权额度不足！");

        // 从 feePayer 账户扣取手续费到合约
        require(exthToken.transferFrom(feePayerA, address(this), feeAmount), unicode"EXTH 转账失败！");
        require(exthToken.transferFrom(feePayerB, address(this), feeAmount), unicode"EXTH 转账失败！");

        emit FeeCollected(tradeId, feePayerA, feePayerB, feeAmount);
    }


    /**
     * @notice 发放 EXTH 奖励（内部函数）
     * @dev 实际奖励发放由后端处理，本合约有 EXTH 代币授权
     * @param user 接收奖励的用户地址
     * @param reward 奖励金额
     */
    function _distributeReward(address user, uint256 reward) internal {
        // 注：实际奖励发放由后端处理，合约有EXTH代币授权
        require(exthToken.transfer(user, reward), unicode"奖励发放失败！");
    }

    /**
     * @notice 检查奖励是否需要减半（内部函数）
     * @dev 每年每多发生 1 亿个 UT 后奖励就减半
     */
    function _checkRewardHalving() internal {
        uint256 halvingCount = totalUTVolume / REWARD_HALVING_INTERVAL;
        uint256 expectedReward = INITIAL_REWARD;

        for (uint256 i = 0; i < halvingCount; i++) {
            expectedReward /= 2;
        }

        if (expectedReward != currentReward) {
            currentReward = expectedReward;
            emit RewardUpdated(currentReward, totalUTVolume);
        }
    }

    // ==================== 争议处理函数 ====================

    /**
     * @notice 发起争议
     * @dev 争议发起在后端完成，合约记录争议状态
     * @param tradeId 交易 ID
     * @param disputedParty 被争议方地址
     */
    function disputeTrade(uint256 tradeId, address disputedParty) external  {
        TradePair storage trade = tradePairs[tradeId];
        require(!trade.isCompleted, unicode"已完成的交易无法发起争议！");
        require(!trade.isDisputed, unicode"争议已解决！");
        require(disputedParty == trade.partyA || disputedParty == trade.partyB, unicode"无效的被争议方！");

        trade.isDisputed = true;
        trade.disputedParty = disputedParty;

        emit TradeDisputed(tradeId, disputedParty);
    }

    /**
     * @notice 将用户加入黑名单
     * @dev 如果存在争议，需要查询区块链浏览器，将有问题的一方由Dao合约拉入黑名单
     * @param user 用户地址
     */
    function blacklistUser(address user) external onlyOwner {
        require(!users[user].isBlacklisted, unicode"该用户已在黑名单中！");

        users[user].isBlacklisted = true;

        emit UserBlacklisted(user);
    }


    // ==================== 金库合约交互 ====================

    /**
     * @notice 检查用户是否在黑名单中
     * @dev 受到损失的一方可以到金库合约领取受到损失的金额，这部分功能由金库合约实现，本合约只提供黑名单状态查询
     * @param user 用户地址
     * @return 是否在黑名单中
     */
    function checkBlacklisted(address user) external view returns (bool) {
        return users[user].isBlacklisted;
    }

    // ==================== 视图函数 ====================

    /**
     * @notice 获取用户信息
     * @param user 用户地址
     * @return userType 用户类型
     * @return newUserTradeCount 新用户剩余强制转账次数
     * @return exthBalance EXTH 余额
     * @return tradeableUT 可交易 UT 数量
     * @return isBlacklisted 是否在黑名单中
     */
    function getUserInfo(address user) external view returns (
        UserType userType,
        uint256 newUserTradeCount,
        uint256 exthBalance,
        uint256 tradeableUT,
        bool isBlacklisted
    ) {
        User memory userData = users[user];
        return (
        userData.userType,
        userData.newUserTradeCount,
        userData.exthBalance,
        getTradeableUT(user),
        userData.isBlacklisted
        );
    }

    /**
     * @notice 获取交易信息
     * @param tradeId 交易 ID
     * @return partyA 率先转账方
     * @return partyB 履约方
     * @return amount 交易金额（UT）
     * @return exthReward EXTH 奖励数量
     * @return isCompleted 是否已完成
     * @return isDisputed 是否存在争议
     * @return disputedParty 被争议方地址
     */
    function getTradeInfo(uint256 tradeId) external view returns (
        address partyA,
        address partyB,
        uint256 amount,
        uint256 exthReward,
        bool isCompleted,
        bool isDisputed,
        address disputedParty
    ) {
        TradePair memory trade = tradePairs[tradeId];
        return (
        trade.partyA,
        trade.partyB,
        trade.amount,
        trade.exthReward,
        trade.isCompleted,
        trade.isDisputed,
        trade.disputedParty
        );
    }
}
