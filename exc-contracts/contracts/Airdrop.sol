// SPDX-License-Identifier: MIT
pragma solidity ^0.8.21;

// 导入 OpenZeppelin 的标准库，这是行业最佳实践
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

contract Airdrop is Ownable {
    // --- 不可变量 (Immutable) ---
    // 在构造时一次性设定，永不可改，保证了合约的核心安全性，且节省Gas。

    bytes32 public immutable merkleRoot; // 白名单的“数字指纹”
    IERC20 public immutable token;       // 空投的代币

    // --- 状态变量 ---

    // 记录地址是否已领取，防止二次领取攻击。
    mapping(address => bool) public hasClaimed;

    // --- 事件 ---

    event AirdropClaimed(address indexed claimant, uint256 amount);
    event TokensReclaimed(address indexed recipient, uint256 amount);

    // --- 构造函数 ---

    /**
     * @param _merkleRoot 链下计算好的白名单默克尔根
     * @param _tokenAddress 空投代币的合约地址
     * @param _daoTimelock DAO的Timelock合约地址，将所有权直接赋予DAO
     */
    constructor(
        bytes32 _merkleRoot,
        address _tokenAddress,
        address _daoTimelock
    ) Ownable(_daoTimelock) {
        require(_merkleRoot != bytes32(0), "Merkle root cannot be zero");
        require(_tokenAddress != address(0), "Token address cannot be zero");

        merkleRoot = _merkleRoot;
        token = IERC20(_tokenAddress);
    }

    // --- 用户功能 ---

    /**
     * @notice 用户调用此函数领取空投
     * @param amount 用户有权领取的具体金额
     * @param merkleProof 用户的个人领取凭证（由前端提供）
     */
    function claim(uint256 amount, bytes32[] calldata merkleProof) external {
        // 检查：是否已经领取过(Checks)
        require(!hasClaimed[msg.sender], "Airdrop already claimed");

        // 验证：用户的凭证是否有效？
        // 根据调用者地址和其声称的金额，在链上重新计算出它的“叶子节点”
        bytes32 leaf = keccak256(abi.encodePacked(msg.sender, amount));

        // 使用OpenZeppelin的库函数验证叶子节点和凭证是否能匹配上合约里的根
        require(MerkleProof.verify(merkleProof, merkleRoot, leaf), "Invalid Merkle proof");

        // 更新状态：立即将用户标记为“已领取”，防止重入攻击
        hasClaimed[msg.sender] = true;

        // 执行：向用户转账 (Interactions)
        // 使用 safeTransfer 可以避免某些特殊ERC20代币的问题，但为简化，这里用transfer
        require(token.transfer(msg.sender, amount), "Token transfer failed");

        emit AirdropClaimed(msg.sender, amount);
    }

    // --- DAO 管理功能 ---

    /**
     * @notice [仅限DAO调用] 在空投结束后，取回合约中剩余的代币。
     */
    function reclaimTokens() external onlyOwner {
        uint256 remainingBalance = token.balanceOf(address(this));
        if (remainingBalance > 0) {
            // 将剩余代币发送给合约的所有者（即Timelock地址）
            require(token.transfer(owner(), remainingBalance), "Reclaim transfer failed");
            emit TokensReclaimed(owner(), remainingBalance);
        }
    }

    // --- 查询功能 ---

    /**
     * @notice 查询指定地址是否已领取空投
     */
    function isClaimed(address user) external view returns (bool) {
        return hasClaimed[user];
    }
}
