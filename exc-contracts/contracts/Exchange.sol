// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import '@openzeppelin/contracts-5.1.0/token/ERC20/ERC20.sol';
import '@openzeppelin/contracts-5.1.0/access/Ownable.sol';
import '@openzeppelin/contracts-5.1.0/utils/ReentrancyGuard.sol';
import './EXTH.sol';
import './USDT.sol';
import './MultiSigWallet.sol';
import './Treasure.sol'; // 导入金库合约

/**
 * @title 去中心化兑换系统合约
 * @notice 实现P2P兑换交易，包含用户分级、手续费、争议处理等功能
 */
contract Exchange is Ownable, ReentrancyGuard {

    // ==================== 状态变量 ====================

    EXTH public exthToken;
    USDT public usdtToken;
    MultiSigWallet public multiSigWallet;
    Treasure public treasure; // 金库合约地址
    mapping(address => bool) public authorizedCallers; //授权调用者

    // 手续费：万分之一
    uint256 public constant FEE_RATE = 1;
    uint256 public constant FEE_DENOMINATOR = 10000;

    // 交易额度限制
    uint256 public constant MIN_UT = 1; // 1 UT = 100 USD
    uint256 public constant MAX_UT = 70; // 70 UT

    uint256 public constant TRADE_TIMEOUT_SECONDS = 18000; // 交易超时时间（5小时）



    // 用户类型枚举
    enum UserType { NEW, NORMAL, SEED }

    // 用户结构体
    struct User {
        UserType userType;
        uint256 newUserTradeCount; // 新用户还需要作为率先转账方的次数
        uint256 exthBalance; // 缓存的EXTH余额，用于计算可交易额度
        bool isBlacklisted; // 是否被拉黑
    }

    // 交易对结构体
    struct TradePair {
        address partyA; // 率先转账方
        address partyB; // 履约方
        uint256 amount; // 交易额度（UT）
        uint256 exthReward; // EXTH奖励数量
        uint256 feeAmount; // 手续费金额（EXTH）
        uint256 createTime; //交易创建时的区块时间
        uint256 completeTime; // 记录完成或解决争议的时间
        // 0: Created, 1: PartyA Confirmed, 2: PartyB Confirmed, 3: Completed, 4: Cancell,5:dispute,6:Resolved,7:expired
        uint8 state;
        // 争议状态: 0: 无争议, 1: 争议处理中, 2: 争议请求已执行, 3: 争议请求已驳回, 4: 争议已过期
        uint8 disputeStatus;
        address disputedParty; // 被争议方
    }

    // 奖励减半相关
    uint256 public constant INITIAL_REWARD = 0.05 * 10**6; // 0.05 EXTH (精度6)
    uint256 public constant REWARD_HALVING_INTERVAL = 1000_000_000; // 1亿 UT
    uint256 public totalUTVolume;      // 当年累计交易量
    uint256 public currentReward;      // 当前奖励额度
    uint256 public lastRewardYear;     //上一次结算奖励的年份

    // 用户映射
    mapping(address => User) public users;

    // 交易对映射
    mapping(uint256 => TradePair) public tradePairs;
    uint256 public tradePairCounter;



    // 事件
    event UserUpgraded(address indexed user, UserType newType);
    event TradeCreate(uint256 indexed chainTradeId,uint256 indexed tradeId, address indexed partyA, address partyB, uint256 amount);
    event TradeCompleted(uint256 indexed tradeId);
    event TradeDisputed(uint256 indexed tradeId, address indexed disputedParty);
    event UserBlacklisted(address indexed user);
    event RewardUpdated(uint256 newReward, uint256 totalUTVolume);
    event FeeCollected(uint256 indexed tradeId, address indexed feePayerA,address indexed feePayerB, uint256 feeAmount);
    event DisputeSubmittedToArbitration(uint256 indexed tradeId, address indexed initiator, address indexed accusedParty);
    event PartyAConfirmed(uint256 indexed tradeId,address indexed party,string txHash);
    event PartyBConfirmed(uint256 indexed tradeId,address indexed party,string txHash);
    event TradeCancelled(uint256 indexed tradeId);
    event TradeResolved(uint256 indexed tradeId);
    event TradeExpired(uint256 indexed tradeId);
    // ==================== 修饰器 ====================

    modifier notBlacklisted(address user) {
        require(!users[user].isBlacklisted, unicode"用户在黑名单中！");
        _;
    }
    modifier onlyAuthorized() {
        require(msg.sender == owner() || authorizedCallers[msg.sender], "Not authorized");
        _;
    }


    // ==================== 构造函数 ====================

    constructor(address _exthToken, address _usdtToken) Ownable(msg.sender) {
        exthToken = EXTH(_exthToken);
        usdtToken = USDT(_usdtToken);
        lastRewardYear = _getCurrentYear(); // 初始化部署年份
    }

    // ==================== 用户管理函数 ====================

    /**
     * @notice 注册用户
     * @dev 初始化用户状态：EXTH余额为0，新用户率先转账次数为3
     */
    function registerUser() external {
        User storage userData = users[msg.sender];

        // 防止重复注册覆盖已有数据
        require(userData.newUserTradeCount == 0 && userData.userType == UserType(0), "Already registered");

        userData.userType = UserType.NEW;
        userData.newUserTradeCount = 3;
        userData.exthBalance = 0;
        userData.isBlacklisted = false;

        emit UserUpgraded(msg.sender, UserType.NEW);
    }

    /**
     * @notice 批量设置种子用户状态
     * @dev 仅更新链上状态：类型设为 SEED，率先转账次数清零。
     *      代币分发应由部署脚本通过 Treasure 合约完成。
     * @param seedUsers 种子用户地址列表
     */
    function initSeedUsers(address[] calldata seedUsers) external onlyOwner {
        for (uint256 i = 0; i < seedUsers.length; i++) {
            address user = seedUsers[i];
            User storage userData = users[user];

            // 设置为种子用户
            userData.userType = UserType.SEED;

            // 率先转账次数清零
            userData.newUserTradeCount = 0;

            // 同步余额
            userData.exthBalance = exthToken.balanceOf(user);

            emit UserUpgraded(user, UserType.SEED);
        }
    }


    /**
     * @notice 更新用户类型（基于 EXTH 余额和新手任务进度）
     * @param user 用户地址
     */
    function updateUserType(address user) public {
        User storage userData = users[user];

        uint256 exthBalance = exthToken.balanceOf(user);
        userData.exthBalance = exthBalance;

        // 只要 newUserTradeCount > 0，强制保持 NEW 身份
        if (userData.newUserTradeCount > 0) {
            if (userData.userType != UserType.NEW) {
                userData.userType = UserType.NEW;
                emit UserUpgraded(user, UserType.NEW);
            }
        }
        // 新手任务完成后，根据余额判定是 SEED 还是 NORMAL
        else if (exthBalance >= 900 * 10**6) {
            if (userData.userType != UserType.SEED) {
                userData.userType = UserType.SEED;
                emit UserUpgraded(user, UserType.SEED);
            }
        }
        else {
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
     * @notice 创建交易对（由后端调用）
     * @param partyA 率先转账方
     * @param partyB 履约方
     * @param amount 交易金额（UT）
     * @return 交易对 ID
     */
    function createTradePair(
        address partyA,
        address partyB,
        uint256 amount,
        uint256 feeAmount,  // 手续费
        uint256 tradeId
    ) external  notBlacklisted(partyA) notBlacklisted(partyB) returns (uint256) {

        // 简化：不再检查用户类型和新手次数，完全信任后端传入的参数
        // 后端已经决定了谁是 partyA（率先转账方）

        // 计算当前奖励（根据总交易量减半）
        _checkRewardHalving();

        // 根据交易量动态计算奖励：每 1UT 奖励 currentReward（0.05 EXTH）
        uint256 reward = (amount * currentReward) / 10**6;

        // 创建交易对
        tradePairCounter++;
        tradePairs[tradePairCounter] = TradePair({
        partyA: partyA,
        partyB: partyB,
        amount: amount,
        exthReward: reward, // 使用动态计算的奖励
        feeAmount: feeAmount, // 手续费
        createTime: block.timestamp,
        completeTime: 0,
        state: 0,
        disputeStatus: 0,
        disputedParty: address(0)
        });

        emit TradeCreate(tradePairCounter, tradeId, partyA, partyB, amount);

        return tradePairCounter;
    }
    function confirmPartyA(uint256 tradeId, string calldata txHash) external {
        TradePair storage trade = tradePairs[tradeId];
        require(msg.sender == trade.partyA, "Not party A");
        require(trade.state == 0, "Invalid state or already confirmed"); // 只有 state 为 0 才能确认
        require(block.timestamp <= trade.createTime + TRADE_TIMEOUT_SECONDS, "Trade expired");

        trade.state = 1; // Update state to PartyA Confirmed

        emit PartyAConfirmed(tradeId, msg.sender, txHash);
    }
    function confirmPartyB(uint256 tradeId, string calldata txHash) external {
        TradePair storage trade = tradePairs[tradeId];
        require(msg.sender == trade.partyB, "Not party B");
        require(trade.state == 1, "Waiting for Party A confirmation"); // 只有 state 为 1 才能确认
        require(block.timestamp <= trade.createTime + TRADE_TIMEOUT_SECONDS, "Trade expired");

        trade.state = 2; // Update state to PartyB Confirmed

        emit PartyBConfirmed(tradeId, msg.sender, txHash);

    }

    function cancelTrade(uint256 tradeId) external {
        TradePair storage trade = tradePairs[tradeId];
        require(msg.sender == trade.partyA || msg.sender == trade.partyB, "Not authorized");
        require(trade.state < 3, "Trade already completed");
        require(block.timestamp <= trade.createTime + TRADE_TIMEOUT_SECONDS, "Trade expired");

        trade.state = 4; // Cancelled
        trade.completeTime = block.timestamp;
        emit TradeCancelled(tradeId);
    }


    /**
     * @notice 完成交易
     * @param tradeId 交易 ID
     * @return 是否成功完成
     */
    function completeTrade(uint256 tradeId) external  nonReentrant returns (bool) {
        require(tradeId > 0 && tradeId <= tradePairCounter, unicode"无效的交易 ID！");
        TradePair storage trade = tradePairs[tradeId];
        require(block.timestamp <= trade.createTime + 18000, "Trade expired");
        require(trade.state == 2, "Invalid state: waiting for confirmations or disputed");


        // 标记为已完成
        trade.state = 3;


        // 自动发放奖励
        _distributeReward(trade.partyA, trade.exthReward);
        _distributeReward(trade.partyB, trade.exthReward);

        // 自动收取手续费（根据 trade 中存储的 feeAmount）
        _collectFee(tradeId, trade.feeAmount);

        // 更新总交易量
        totalUTVolume += trade.amount;
        if (users[trade.partyA].newUserTradeCount > 0) {
            users[trade.partyA].newUserTradeCount -= 1;
        }

        trade.completeTime = block.timestamp;

        //交易完成后，检查并更新双方用户类型
        updateUserType(trade.partyA);
        updateUserType(trade.partyB);

        emit TradeCompleted(tradeId);
        return true;
    }

    /**
     * @notice 收取手续费
     * @param tradeId 交易 ID
     * @param feeAmount 手续费金额（EXTH）
     */
    function _collectFee(uint256 tradeId, uint256 feeAmount) internal {
        TradePair storage trade = tradePairs[tradeId];

        // 从 partyA（率先转账方）收取手续费
        address feePayerA = trade.partyA;
        // 从 partyB（履约方）收取手续费
        address feePayerB = trade.partyB;

        // 检查授权额度是否足够
        require(exthToken.allowance(feePayerA, address(this)) >= feeAmount, unicode"授权额度不足！");
        require(exthToken.allowance(feePayerB, address(this)) >= feeAmount, unicode"授权额度不足！");

        // 从用户账户扣取手续费到金库合约
        require(exthToken.transferFrom(feePayerA, address(treasure), feeAmount), unicode"EXTH 转账失败！");
        require(exthToken.transferFrom(feePayerB, address(treasure), feeAmount), unicode"EXTH 转账失败！");

        emit FeeCollected(tradeId, feePayerA, feePayerB, feeAmount);
    }


    /**
     * @notice 发放 EXTH 奖励（内部函数）
     * @dev 从金库合约提取 EXTH 代币发放给用户
     * @param user 接收奖励的用户地址
     * @param reward 奖励金额
     */
    function _distributeReward(address user, uint256 reward) internal {
        require(address(treasure) != address(0), "Treasure not set");
        treasure.withdrawERC20(address(exthToken), user, reward);
    }

    /**
     * @notice 标记交易为过期状态
     * @dev 当交易创建超过 18000 秒（5小时）后，任何人均可调用此函数将状态置为过期
     * @param tradeId 交易 ID
     */
    function expireTrade(uint256 tradeId) external {
        TradePair storage trade = tradePairs[tradeId];
        require(trade.state < 3, "Trade already finished"); // 只有未完成的状态才能过期
        require(block.timestamp > trade.createTime + TRADE_TIMEOUT_SECONDS, "Trade not yet expired");

        trade.state = 7; // Expired
        trade.completeTime = block.timestamp;

        emit TradeExpired(tradeId);
    }

    /**
     * @notice 获取当前区块时间的年份
     */
    function _getCurrentYear() internal view returns (uint256) {
        // 从 1970-01-01 开始的秒数转换为年
        return (block.timestamp / 31536000) + 1970;
    }

    /**
     * @notice 检查奖励是否需要减半（内部函数）
     * @dev 每年每多发生 1 亿个 UT 后奖励就减半
     */
    function _checkRewardHalving() internal {
        uint256 currentYear = _getCurrentYear();
        // 如果跨年了，重置交易量计数器
        if (currentYear > lastRewardYear) {
            // 如果需要保留历史总量用于其他用途，可以移到另一个变量 totalAllTimeVolume
            totalUTVolume = 0;
            lastRewardYear = currentYear;

            // 跨年时，奖励恢复初始值
            currentReward = INITIAL_REWARD;
            emit RewardUpdated(currentReward, 0);
        }

        //检查当年交易量是否达到减半阈值（1亿）
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
    // 设置金库合约地址（仅 Owner）
    function setTreasure(address _treasure) external onlyOwner {
        require(_treasure != address(0), "Invalid address");
        treasure = Treasure(payable(_treasure));
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
        require(disputedParty == trade.partyA || disputedParty == trade.partyB, unicode"无效的被争议方！");
        require(trade.state < 3, "Trade already finished");
        require(trade.state != 5, "Already disputed");

        trade.disputedParty = disputedParty;

        trade.state = 5;
        trade.disputeStatus = 1;

        emit TradeDisputed(tradeId, disputedParty);

        if (address(multiSigWallet) != address(0)) {
            emit DisputeSubmittedToArbitration(tradeId, msg.sender, disputedParty);
        }
    }

    /**
     * @notice 将用户加入黑名单
     * @dev 如果存在争议，需要查询区块链浏览器，将有问题的一方由Dao合约或者仲裁委员会拉入黑名单
     * @param user 用户地址
     */
    function blacklistUser(address user) external onlyAuthorized {
        require(!users[user].isBlacklisted, unicode"该用户已在黑名单中！");

        users[user].isBlacklisted = true;

        emit UserBlacklisted(user);
    }

    /**
     * @notice [onlyOwner] 设置授权的黑名单调用者
     * @param _caller 被授权的调用者地址
     * @param _authorized 是否授权
     */
    function setAuthorizedCaller(address _caller, bool _authorized) external onlyOwner {
        authorizedCallers[_caller] = _authorized;
    }

    // 只有白名单地址能调用
    function markTradeAsResolved(uint256 tradeId, uint8 newDisputeStatus) external onlyAuthorized {
        require(tradePairs[tradeId].state == 5, "Trade not in dispute");
        require(newDisputeStatus <= 4, "Invalid dispute status");
        tradePairs[tradeId].state = 6; // 标记为已解决
        tradePairs[tradeId].disputeStatus = newDisputeStatus;
        tradePairs[tradeId].completeTime = block.timestamp;
        emit TradeResolved(tradeId);
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

    function setMultiSigWallet(address _multiSigWallet) external onlyOwner {
        require(_multiSigWallet != address(0), "Invalid address");
        multiSigWallet = MultiSigWallet(_multiSigWallet);
    }



    function getTradeInfo(uint256 tradeId) external view returns (
        address partyA,
        address partyB,
        uint256 amount,
        uint256 exthReward,
        uint256 feeAmount,
        uint8 state,
        uint8 disputeStatus,
        address disputedParty,
        uint256 completeTime,
        uint256 createTime
    ) {
        TradePair memory trade = tradePairs[tradeId];
        return (
        trade.partyA,
        trade.partyB,
        trade.amount,
        trade.exthReward,
        trade.feeAmount,
        trade.state,
        trade.disputeStatus,
        trade.disputedParty,
        trade.completeTime,
        trade.createTime
        );
    }
}