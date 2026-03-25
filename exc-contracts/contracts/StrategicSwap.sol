// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

// 这个合约是一次性的，专门用于本次战略投资交换
contract StrategicSwap is Ownable {

    // --- 不可变量，在部署时锁定，保证安全 ---
    IERC20 public immutable exthToken; // 项目代币
    IERC20 public immutable usdtToken; // 稳定币
    address public immutable investor;  // 投资者地址
    address public immutable treasure;  // DAO金库地址
    uint256 public immutable exthAmount; // 项目代币数量
    uint256 public immutable usdtAmount; // 稳定币数量

    event SwapExecuted(address indexed investor, uint256 usdtAmount);
    event FundsReclaimedByDAO();

    constructor(
        address _exthAddress,
        address _usdtAddress,
        address _investorAddress,
        address _treasureAddress,
        uint256 _exthAmount,
        uint256 _usdtAmount,
        address _daoTimelock // 合约所有权直接给Timelock，用于处理意外
    ) Ownable(_daoTimelock) {
        exthToken = IERC20(_exthAddress);
        usdtToken = IERC20(_usdtAddress);
        investor = _investorAddress;
        treasure = _treasureAddress;
        exthAmount = _exthAmount;
        usdtAmount = _usdtAmount;
    }

    /**
     * @notice 由投资者调用，以原子方式完成交换。
     * 前提：1. DAO已将EXTH存入本合约。2. 投资者已授权本合约转移其USDT。
     */
    function executeSwap() external {
        require(msg.sender == investor, "Only investor can execute swap");
        require(exthToken.balanceOf(address(this)) >= exthAmount, "DAO has not funded the contract yet");

        // 从投资者钱包拉取USDT到本合约
        usdtToken.transferFrom(investor, address(this), usdtAmount);

        // 将本合约中的EXTH发送给投资者
        exthToken.transfer(investor, exthAmount);

        // 将刚刚收到的USDT发送给DAO的金库
        usdtToken.transfer(treasure, usdtAmount);

        emit SwapExecuted(investor, usdtAmount);
    }

    /**
     * @notice [onlyOwner] 安全后门：如果投资者长时间未完成交易，允许DAO取回其EXTH。
     */
    function reclaimDAOFunds() external onlyOwner {
        exthToken.transfer(treasure, exthToken.balanceOf(address(this)));
        emit FundsReclaimedByDAO();
    }
}
