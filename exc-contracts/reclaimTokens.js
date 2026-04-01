const { ethers } = require("ethers");
const fs = require("fs");
const path = require("path");

async function main() {
    console.log("=== 生成回收空投代币提案 Calldata ===\n");

    // 读取合约地址配置
    const addressesFile = path.join(__dirname, "contract-addresses.json");
    if (!fs.existsSync(addressesFile)) {
        throw new Error("找不到 contract-addresses.json 文件");
    }

    const addresses = JSON.parse(fs.readFileSync(addressesFile, "utf8"));

    console.log("📋 读取合约地址配置...");
    console.log(`  Airdrop: ${addresses.core.Airdrop}`);
    console.log(`  Treasure: ${addresses.core.Treasure}`);
    console.log(`  EXTH Token: ${addresses.tokens.EXTH}`);

    // --- 生成 Airdrop.reclaimTokens() 的 calldata ---
    console.log("\n=== 方案：调用 Airdrop.reclaimTokens() ===");

    const airdropABI = [
        "function reclaimTokens()"
    ];

    const airdropInterface = new ethers.Interface(airdropABI);

    // encodeFunctionData 会自动添加函数选择器
    const calldata = airdropInterface.encodeFunctionData("reclaimTokens");

    console.log("\n✅ Calldata 生成成功:");
    console.log(calldata);

    // --- 输出提案所需的全部信息 ---
    console.log("\n" + "=".repeat(80));
    console.log("📝 提案信息汇总");
    console.log("=".repeat(80));

    console.log("\n【回收空投代币提案】");
    console.log(`  Target (目标合约): ${addresses.core.Airdrop}`);
    console.log(`  Value (ETH 金额): 0`);
    console.log(`  Calldata: ${calldata}`);
    console.log(`  Description: Proposal: Reclaim remaining tokens from Airdrop contract and return to DAO Treasury`);

    console.log("\n⚠️  注意事项:");
    console.log("1. 确保 Airdrop 合约中有剩余的代币");
    console.log("2. 确保 Airdrop 合约的 owner 是 Timelock 或者支持被 Timelock 调用");
    console.log("3. reclaimTokens 函数会将剩余代币转回 Treasure 金库");

    // --- 保存结果到文件 ---
    const output = {
        timestamp: new Date().toISOString(),
        proposalInfo: {
            target: addresses.core.Airdrop,
            value: "0",
            calldata: calldata,
            description: "Proposal: Reclaim remaining tokens from Airdrop contract and return to DAO Treasury",
            method: "Airdrop.reclaimTokens()"
        },
        contracts: {
            airdrop: addresses.core.Airdrop,
            treasure: addresses.core.Treasure,
            exthToken: addresses.tokens.EXTH
        }
    };

    const outputFile = path.join(__dirname, "airdrop-reclaim-proposal.json");
    fs.writeFileSync(outputFile, JSON.stringify(output, null, 2));

    console.log(`\n💾 提案信息已保存到：${outputFile}`);
    console.log("\n=== 完成 ===");
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
