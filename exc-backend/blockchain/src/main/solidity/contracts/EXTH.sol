// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

import './node_modules/@openzeppelin/contracts-5.1.0/token/ERC20/ERC20.sol';
import './node_modules/@openzeppelin/contracts-5.1.0/token/ERC20/extensions/ERC20Permit.sol';
import './node_modules/@openzeppelin/contracts-5.1.0/token/ERC20/extensions/ERC20Votes.sol';
import './node_modules/@openzeppelin/contracts-5.1.0/access/Ownable.sol';

/**
 * @title EXTH 代币合约
 * @notice 平台治理代币，支持投票、授权和转账功能
 */
contract EXTH is ERC20, ERC20Permit, ERC20Votes, Ownable {


    constructor()
    ERC20("EXTH", "EXTH")
    ERC20Permit("EXTH")
    Ownable(msg.sender)
    {
    }

    function mint(address to, uint256 amount) public onlyOwner {
        _mint(to, amount);
    }


    /**
     * @notice 重写 decimals，返回代币精度为 6
     * @return 代币精度（小数位数）
     */
    function decimals() public view virtual override returns (uint8) {
        return 6;
    }

    /**
     * @notice 重写 _update 函数，处理 ERC20 和 ERC20Votes 的状态更新
     * @param from 转出地址
     * @param to 转入地址
     * @param value 转账金额
     */
    function _update(address from, address to, uint256 value)
    internal
    override(ERC20, ERC20Votes)
    {
        super._update(from, to, value);
    }

    /**
     * @notice 重写 nonces 函数，获取指定地址的 nonce（用于 Permit 授权）
     * @param owner 代币持有者地址
     * @return nonce 值
     */
    function nonces(address owner)
    public
    view
    override(ERC20Permit, Nonces)
    returns (uint256)
    {
        return super.nonces(owner);
    }


}
