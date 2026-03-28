// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title 时间锁合约 - DAO 的安全执行官
 * @notice 继承自 AccessControl 来管理提案和执行的明确角色
 */
contract Timelock is AccessControl {

    // --- 角色定义 ---
    bytes32 public constant PROPOSER_ROLE = keccak256("PROPOSER_ROLE");
    bytes32 public constant EXECUTOR_ROLE = keccak256("EXECUTOR_ROLE");
    // DEFAULT_ADMIN_ROLE 已在 AccessControl 中定义，用于管理角色本身。

    // --- 状态变量 ---
    uint256 public minDelay; // 交易执行前的最小延迟时间（秒）

    // 映射：从交易ID到其可执行时间戳(ETA)。如果为0，则表示未排队。
    mapping(bytes32 => uint256) public timestamps;

    // --- 事件 ---
    event QueuedTransaction(bytes32 indexed txId, address indexed target, uint256 value, bytes data, uint256 eta);
    event ExecutedTransaction(bytes32 indexed txId, address indexed target, uint256 value, bytes data);
    event CanceledTransaction(bytes32 indexed txId);
    event MinDelayChanged(uint256 oldDelay, uint256 newDelay);

    // --- 构造函数 ---
    /**
     * @param _minDelay 初始的最小延迟时间。
     */
    constructor(uint256 _minDelay) {
        require(_minDelay > 0, unicode"Timelock: 最小延迟必须大于0");
        minDelay = _minDelay;

        // --- 设置角色 ---
        // 部署者获得 ADMIN 角色，以便完成初始设置。
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);

        // 任何人 (`address(0)`) 都被授予执行者角色。这是安全的。
        _grantRole(EXECUTOR_ROLE, address(0));
    }

    // --- 核心功能 ---

    /**
     * @notice 将一笔交易排队等待后续执行。只能由 PROPOSER 调用。
     * @param target 目标合约地址。
     * @param value 随调用发送的 ETH 数量。
     * @param data 要执行的函数的 calldata。
     * @return txId 排队交易的ID。
     */
    function queueTransaction(
        address target,
        uint256 value,
        bytes calldata data
    ) external onlyRole(PROPOSER_ROLE) returns (bytes32) {
        uint256 eta = block.timestamp + minDelay; // eta = Estimated Time of Arrival
        bytes32 txId = getTransactionId(target, value, data, eta);

        require(timestamps[txId] == 0, unicode"Timelock: 交易已在队列中");

        timestamps[txId] = eta;

        emit QueuedTransaction(txId, target, value, data, eta);
        return txId;
    }

    /**
     * @notice 执行一个已通过时间锁的交易。可由 EXECUTOR 调用。
     * @param target 目标合约地址。
     * @param value 随调用发送的 ETH 数量。
     * @param data 要执行的函数的 calldata。
     * @param eta 预期的执行时间戳。
     */
    function executeTransaction(
        address target,
        uint256 value,
        bytes calldata data,
        uint256 eta
    ) external payable onlyRole(EXECUTOR_ROLE) {
        bytes32 txId = getTransactionId(target, value, data, eta);

        uint256 executionTime = timestamps[txId];
        require(executionTime > 0, unicode"Timelock: 交易不在队列中");
        require(executionTime == eta, unicode"Timelock: 提供了错误的ETA");
        require(block.timestamp >= eta, unicode"Timelock: 执行时间未到");

        // 在执行前清除队列，以防止重入攻击。
        timestamps[txId] = 0;

        (bool success, ) = target.call{value: value}(data);
        require(success, unicode"Timelock: 交易执行失败");

        emit ExecutedTransaction(txId, target, value, data);
    }

    /**
     * @notice 取消一个已排队的交易。只能由 PROPOSER 调用。
     * @param target 目标合约地址。
     * @param value 随调用发送的 ETH 数量。
     * @param data 要执行的函数的 calldata。
     * @param eta 预期的执行时间戳。
     */
    function cancelTransaction(
        address target,
        uint256 value,
        bytes calldata data,
        uint256 eta
    ) external onlyRole(PROPOSER_ROLE) {
        bytes32 txId = getTransactionId(target, value, data, eta);

        require(timestamps[txId] == eta, unicode"Timelock: 在此ETA下队列中未找到交易");

        timestamps[txId] = 0;

        emit CanceledTransaction(txId);
    }

    // --- DAO 自我治理功能 ---

    /**
     * @notice 更新最小延迟时间。只能由 TIMELOCK 合约自身通过提案来调用。
     * @param newMinDelay 新的最小延迟时间（秒）。
     */
    function updateMinDelay(uint256 newMinDelay) external {
        // 此检查确保只有通过成功的DAO提案才能更改延迟时间。
        require(msg.sender == address(this), unicode"Timelock: 调用者必须是 Timelock 合约本身");
        require(newMinDelay > 0, unicode"Timelock: 新延迟必须大于0");

        uint256 oldMinDelay = minDelay;
        minDelay = newMinDelay;

        emit MinDelayChanged(oldMinDelay, newMinDelay);
    }

    // --- 查询功能 ---

    /**
     * @notice 为一笔交易生成唯一的ID。
     */
    function getTransactionId(
        address target,
        uint256 value,
        bytes calldata data,
        uint256 eta
    ) public pure returns (bytes32) {
        return keccak256(abi.encode(target, value, data, eta));
    }

    /**
     * @notice 检查一笔交易是否已排队。
     * @return 如果交易已排队，则为 true，否则为 false。
     */
    function isQueued(bytes32 txId) external view returns (bool) {
        return timestamps[txId] > 0;
    }

    // --- Fallback 函数 ---
    /**
     * @notice 允许 Timelock 合约自身接收 ETH。
     */
    receive() external payable {}
}
