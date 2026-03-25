const { ethers } = require("hardhat");

// --- 配置 ---
const DAO_CONTRACT_ADDRESS = "0x...你的Dao合约地址...";
const AIRDROP_CONTRACT_ADDRESS = "0x...你刚刚部署的Airdrop合约地址...";
// ---

async function main() {
    const [proposer] = await ethers.getSigners();
    console.log("Creating proposal with account:", proposer.address);

    const dao = await ethers.getContractAt("Dao", DAO_CONTRACT_ADDRESS);
    const airdrop = await ethers.getContractAt("Airdrop", AIRDROP_CONTRACT_ADDRESS);

    // 1. 准备提案内容
    // 目标合约: Airdrop 合约
    const targets = [AIRDROP_CONTRACT_ADDRESS];
    // 发送的ETH: 0
    const values = [0];
    // 要调用的函数和参数: reclaimTokens()
    const calldatas = [airdrop.interface.encodeFunctionData("reclaimTokens")];
    // 提案描述
    const description = "Proposal #3: Reclaim remaining tokens from the Airdrop contract";

    console.log("Proposal details:");
    console.log(`  - Target: ${targets[0]}`);
    console.log(`  - Function: reclaimTokens()`);
    console.log(`  - Description: ${description}`);

    // 2. 发起提案
    const tx = await dao.propose(targets, values, calldatas, description);
    console.log("\nSubmitting proposal...");

    const receipt = await tx.wait(1);

    // 从事件中解析 proposalId
    const proposalIdEvent = receipt.logs.find(e => e.eventName === 'ProposalCreated');
    const proposalId = proposalIdEvent.args[0];

    console.log("✅ Proposal submitted successfully!");
    console.log(`Proposal ID: ${proposalId.toString()}`);
    console.log(`Now, go and vote for this proposal!`);
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
