const { ethers } = require("hardhat");

// --- 配置 ---
const DAO_CONTRACT_ADDRESS = "0xF202E9baEa03174aE391DA45FD6545B74E53B390";
const AIRDROP_CONTRACT_ADDRESS = "0x65448A5dCC4d672cEa986CDa8DfeAA194037b8DB";
// ---

async function main() {
    const [proposer] = await ethers.getSigners();
    console.log("Creating proposal with account:", proposer.address);

    const dao = await ethers.getContractAt("Dao", DAO_CONTRACT_ADDRESS);
    const airdrop = await ethers.getContractAt("Airdrop", AIRDROP_CONTRACT_ADDRESS);

    // 1. 准备提案内容
    // 目标合约: Airdrop 合约
    const target = AIRDROP_CONTRACT_ADDRESS;
    // 发送的ETH: 0
    const value = 0;
    // 要调用的函数和参数: reclaimTokens()
    const calldata = airdrop.interface.encodeFunctionData("reclaimTokens");
    // 提案描述
    const description = "Proposal #3: Reclaim remaining tokens from the Airdrop contract";

    console.log("Proposal details:");
    console.log(`  - Target: ${target}`);
    console.log(`  - Function: reclaimTokens()`);
    console.log(`  - Calldata: ${calldata}`);
    console.log(`  - Description: ${description}`);

    // 2. 发起提案
    console.log("\nSubmitting proposal...");
    const tx = await dao.propose(target, value, calldata, description);
    console.log(`Transaction hash: ${tx.hash}`);

    const receipt = await tx.wait(1);
    console.log(`✅ Transaction confirmed in block ${receipt.blockNumber}`);

    // 从事件中解析 proposalId
    // 使用 ethers 的正确方式解析事件
    const iface = dao.interface;
    let proposalId;

    for (const log of receipt.logs) {
        try {
            const parsedLog = iface.parseLog(log);
            if (parsedLog && parsedLog.name === 'ProposalCreated') {
                proposalId = parsedLog.args[0];
                console.log(`\n📋 ProposalCreated event found:`);
                console.log(`   Proposal ID: ${proposalId.toString()}`);
                console.log(`   Proposer: ${parsedLog.args[1]}`);
                console.log(`   Target: ${parsedLog.args[2]}`);
                break;
            }
        } catch (e) {
            // 跳过无法解析的日志
        }
    }

    if (!proposalId) {
        console.log("\n⚠️  Warning: Could not find ProposalCreated event");
        console.log("But proposal was likely created successfully.");
        console.log("You can check the transaction on blockchain explorer.");
        return;
    }

    console.log("\n✅ Proposal submitted successfully!");
    console.log(`Proposal ID: ${proposalId.toString()}`);
    console.log(`Now, go and vote for this proposal!`);
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
