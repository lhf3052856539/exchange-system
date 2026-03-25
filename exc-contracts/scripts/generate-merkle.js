const { MerkleTree } = require('merkletreejs');
const keccak256 = require('keccak256');
const fs = require('fs');
const { ethers } = require("ethers");

async function main() {
    // 1. 读取白名单数据
    const whitelistData = JSON.parse(fs.readFileSync('whitelist.json', 'utf-8'));
    console.log("Whitelist loaded.");

    // 2. 创建叶子节点
    const leaves = Object.entries(whitelistData).map(([address, amount]) => {
        // 重要：这里的哈希方式必须和合约中的 `keccak256(abi.encodePacked(msg.sender, amount))` 完全一致
        return keccak256(
            ethers.AbiCoder.defaultAbiCoder().encode(
                ["address", "uint256"],
                [address, amount]
            )
        );
    });
    console.log("Leaf nodes created.");

    // 3. 创建默克尔树
    const tree = new MerkleTree(leaves, keccak256, { sortPairs: true });

    // 4. 获取默克尔根
    const root = tree.getHexRoot();
    console.log("\n===============================================");
    console.log("✅ Merkle Root (复制这个到你的部署脚本):");
    console.log(root);
    console.log("===============================================\n");

    // 5. (可选) 为一个用户生成证明，用于前端测试
    const testAddress = "0x6f5C2cc9eF8a419dF1B1EDd5aC604780d6575495";
    const testAmount = "100000000";
    const testLeaf = keccak256(
        ethers.AbiCoder.defaultAbiCoder().encode(
            ["address", "uint256"],
            [testAddress, testAmount]
        )
    );
    const proof = tree.getHexProof(testLeaf);
    console.log(`Proof for ${testAddress} claiming ${testAmount}:`);
    console.log(proof);
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
