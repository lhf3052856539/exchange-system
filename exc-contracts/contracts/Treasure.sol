// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import "@openzeppelin/contracts-5.1.0/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts-5.1.0/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts-5.1.0/access/Ownable.sol";

/**
 * @title Treasure金库合约
 * @notice 由Timelock控制，本身不包含任何投票逻辑，仅作为一个安全的资金保险库
 */
contract Treasure is Ownable {

    address public multiSigWallet;
    mapping(address => bool) public authorizedCallers;

    // --- 事件 ---
    event Deposit(address indexed sender, uint256 amount);
    event WithdrawalETH(address indexed to, uint256 amount);
    event WithdrawalERC20(address indexed token, address indexed to, uint256 amount);
    event CallExecuted(address indexed target, uint256 value, bytes data);
    event CompensationPaid(address indexed token, address indexed victim, uint256 amount);
    event AuthorizedCallerAdded(address indexed caller);
    event AuthorizedCallerRemoved(address indexed caller);

    constructor() Ownable(msg.sender) {}

    modifier onlyAuthorized() {
        require(msg.sender == owner() || authorizedCallers[msg.sender], "Not authorized");
        _;
    }

    // --- 资金接收 ---

    /**
     * @notice 允许合约接收ETH。
     */
    receive() external payable {
        emit Deposit(msg.sender, msg.value);
    }

    // --- 资金提取函数 ---

    /**
     * @notice 提取ETH到指定地址。
     * @param _to 接收ETH的地址。
     * @param _amount 提取的金额。
     */
    function withdrawETH(address payable _to, uint256 _amount) external onlyOwner {
        require(_to != address(0), "Treasure: Invalid recipient address");
        require(address(this).balance >= _amount, "Treasure: Insufficient ETH balance");

        (bool success, ) = _to.call{value: _amount}("");
        require(success, "Treasure: ETH transfer failed");

        emit WithdrawalETH(_to, _amount);
    }

    /**
     * @notice 提取指定的ERC20代币到指定地址。
     * @param _tokenAddress 代币的合约地址。
     * @param _to 接收代币的地址。
     * @param _amount 提取的代币数量。
     */
    function withdrawERC20(address _tokenAddress, address _to, uint256 _amount) external onlyAuthorized {
        require(_to != address(0), "Treasure: Invalid recipient address");
        IERC20 token = IERC20(_tokenAddress);
        require(token.balanceOf(address(this)) >= _amount, "Treasure: Insufficient token balance");

        token.transfer(_to, _amount);

        emit WithdrawalERC20(_tokenAddress, _to, _amount);
    }

    function payCompensation(address _tokenAddress, address _victim, uint256 _amount) external onlyAuthorized {
        require(_victim != address(0), "Treasure: Invalid victim address");
        require(_amount > 0, "Treasure: Amount must be greater than 0");

        IERC20 token = IERC20(_tokenAddress);
        require(token.balanceOf(address(this)) >= _amount, "Treasure: Insufficient token balance");

        token.transfer(_victim, _amount);

        emit CompensationPaid(_tokenAddress, _victim, _amount);
    }

    function setMultiSigWallet(address _multiSigWallet) external onlyOwner {
        require(_multiSigWallet != address(0), "Invalid address");
        multiSigWallet = _multiSigWallet;
        authorizedCallers[_multiSigWallet] = true;
        emit AuthorizedCallerAdded(_multiSigWallet);
    }

    function addAuthorizedCaller(address _caller) external onlyOwner {
        require(_caller != address(0), "Invalid address");
        authorizedCallers[_caller] = true;
        emit AuthorizedCallerAdded(_caller);
    }

    function removeAuthorizedCaller(address _caller) external onlyOwner {
        authorizedCallers[_caller] = false;
        emit AuthorizedCallerRemoved(_caller);
    }


    // --- 通用执行函数（高级功能） ---

    /**
     * @notice 允许金库调用其他合约的任意函数，实现资产增值（如存入Aave生息）
     * @param _target 要调用的目标合约地址。
     * @param _value 随调用发送的ETH数量。
     * @param _data 编码后的函数调用数据 (calldata)。
     */
    function executeCall(address _target, uint256 _value, bytes calldata _data) external payable onlyOwner {
        require(_target != address(0), "Treasure: Invalid target address");
        (bool success, ) = _target.call{value: _value}(_data);
        require(success, "Treasure: External call failed");

        emit CallExecuted(_target, _value, _data);
    }
}


