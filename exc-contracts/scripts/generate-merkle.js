const { MerkleTree } = require('merkletreejs');
const keccak256 = require('keccak256'); // merkletreejs 依赖它，但我们可以用 ethers 的哈希函数
const fs = require('fs');
const { ethers } = require("ethers");

async function main() {
    // 1. 读取白名单数据
    const whitelistData = JSON.parse(fs.readFileSync('whitelist.json', 'utf-8'));
    console.log("Whitelist loaded.");

    // 2. 创建叶子节点
    // 使用 ethers.solidityPackedKeccak256 来确保与合约的 abi.encodePacked 行为完全一致
    const leaves = Object.entries(whitelistData).map(([address, amount]) => {
        // 重要：这里的 amount 必须是合约期望的 wei 单位
        // 如果你的 whitelist.json 里是 "100"，这里会处理成 100 * 10^18
        // 如果已经是 wei 单位，请确保它是字符串格式
        const amountInWei = typeof amount === 'number' ? ethers.parseEther(amount.toString()).toString() : amount;

        return ethers.solidityPackedKeccak256(
            ["address", "uint256"],
            [address, amountInWei]
        );
    });
    console.log("Leaf nodes created correctly.");

    // 3. 创建默克尔树
    const tree = new MerkleTree(leaves, keccak256, { sortPairs: true });

    // 4. 获取默克尔根
    const root = tree.getHexRoot();
    console.log("\n===============================================");
    console.log("✅ Merkle Root (复制这个到你的部署脚本):");
    console.log(root);
    console.log("===============================================\n");

    // 5. 生成 JSON 文件供前端使用
    const claims = {};
    for (const [address, amount] of Object.entries(whitelistData)) {
        const amountInWei = typeof amount === 'number' ? ethers.parseEther(amount.toString()).toString() : amount;

        // 重新计算叶子节点以获取证明
        const leaf = ethers.solidityPackedKeccak256(
            ["address", "uint256"],
            [address, amountInWei]
        );
        const proof = tree.getHexProof(leaf);
        claims[address] = {
            amount: amountInWei.toString(), // 确保 amount 是字符串格式
            proof: proof
        };
    }

    const output = {
        merkleRoot: root,
        claims: claims
    };

    fs.writeFileSync('merkle-output.json', JSON.stringify(output, null, 2));
    console.log('✅ Generated merkle-output.json for frontend use');
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
