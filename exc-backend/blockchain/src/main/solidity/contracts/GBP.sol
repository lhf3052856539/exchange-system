// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;
import './node_modules/@openzeppelin/contracts-5.1.0/token/ERC20/ERC20.sol';


contract GBP is ERC20 {

    uint256 constant Supply = 10000 * 10**6;
    constructor() ERC20("GBP","GBP"){
        _mint(msg.sender, Supply);

    }
    function decimals() public view virtual override returns (uint8) {
        return 6;
    }
}
