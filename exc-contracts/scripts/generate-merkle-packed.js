const { MerkleTree } = require('merkletreejs');
const keccak256 = require('keccak256');
const fs = require('fs');
const { ethers } = require("ethers");
const Buffer = require('buffer/').Buffer;

async function main() {
    // 1. 读取白名单数据
    const whitelistData = JSON.parse(fs.readFileSync('whitelist.json', 'utf-8'));
    console.log("Whitelist loaded.");

    // 2. 创建叶子节点 - 使用 encodePacked（与合约一致）
    const leaves = Object.entries(whitelistData).map(([address, amount]) => {
        // 手动实现 encodePacked: address (20 bytes) + uint256 (32 bytes, big endian)
        const addressBytes = ethers.getBytes(address);
        const amountBytes = ethers.toBeArray(amount);

        // 创建 52 字节的 buffer (20 + 32)
        const packed = Buffer.alloc(52);
        packed.set(addressBytes, 0);
        // amount 填充到 32 字节（大端序）
        const amountPadded = Buffer.alloc(32);
        amountPadded.set(amountBytes, 32 - amountBytes.length);
        packed.set(amountPadded, 20);

        return keccak256(packed);
    });
    console.log("Leaf nodes created with packed encoding:");
    console.log(leaves);

    // 3. 创建 Merkle Tree
    const tree = new MerkleTree(leaves, keccak256, { sortPairs: true });

    // 4. 获取 Merkle Root
    const root = tree.getHexRoot();
    console.log("\n===============================================");
    console.log("✅ NEW Merkle Root (use this for redeployment):");
    console.log(root);
    console.log("===============================================\n");

    // 5. 为每个用户生成 proof
    for (const [address, amount] of Object.entries(whitelistData)) {
        // 重新计算 leaf
        const addressBytes = ethers.getBytes(address);
        const amountBytes = ethers.toBeArray(amount);
        const packed = Buffer.alloc(52);
        packed.set(addressBytes, 0);
        const amountPadded = Buffer.alloc(32);
        amountPadded.set(amountBytes, 32 - amountBytes.length);
        packed.set(amountPadded, 20);
        const leaf = keccak256(packed);

        const proof = tree.getHexProof(leaf);
        console.log(`\nProof for ${address} claiming ${amount}:`);
        console.log(JSON.stringify(proof));
    }
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
