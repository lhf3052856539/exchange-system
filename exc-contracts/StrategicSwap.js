const { ethers } = require("ethers");
const fs = require("fs");
const path = require("path");

async function main() {
    console.log("=== 生成战略投资者提案 Calldata ===\n");

    // 读取合约地址配置
    const addressesFile = path.join(__dirname, "contract-addresses.json");
    if (!fs.existsSync(addressesFile)) {
        throw new Error("找不到 contract-addresses.json 文件");
    }

    const addresses = JSON.parse(fs.readFileSync(addressesFile, "utf8"));

    console.log("📋 读取合约地址配置...");
    console.log(`  EXTH Token: ${addresses.tokens.EXTH}`);
    console.log(`  USDT Token: ${addresses.tokens.USDT}`);
    console.log(`  StrategicSwap: ${addresses.core.StrategicSwap}`);
    console.log(`  Treasure: ${addresses.core.Treasure}`);

    if (!addresses.core.StrategicSwap) {
        console.error("\n❌ 错误：StrategicSwap 合约地址未配置");
        console.error("请先部署 StrategicSwap 合约并更新 contract-addresses.json");
        process.exit(1);
    }

    // --- 方案 1: 使用 Treasure 的 withdrawERC20 函数 ---
    console.log("\n=== 方案 1: 调用 Treasure.withdrawERC20 ===");

    const treasureABI = [
        "function withdrawERC20(address _tokenAddress, address _to, uint256 _amount)"
    ];

    const treasureInterface = new ethers.Interface(treasureABI);

    // 从 StrategicSwap 合约获取需要转移的 EXTH 数量
    // 这里需要你手动指定，或者读取链上数据
    // 请根据实际情况修改 EXTH 数量
    const exthAmount = ethers.parseUnits("50000000000", 6); //  万枚 EXTH (6 位小数)

    const calldata1 = treasureInterface.encodeFunctionData("withdrawERC20", [
        addresses.tokens.EXTH,           // ERC20 代币地址
        addresses.core.StrategicSwap,    // 目标地址 (StrategicSwap 合约)
        exthAmount                       // 转账金额
    ]);

    console.log("\n✅ Calldata 1 (Treasure.withdrawERC20):");
    console.log(calldata1);

    // --- 方案 2: 直接使用 EXTH 合约的 transferFrom + approve 组合 ---
    console.log("\n=== 方案 2: 调用 EXTH.approve (授权 StrategicSwap) ===");

    const erc20ABI = [
        "function approve(address spender, uint256 amount) returns (bool)",
        "function transferFrom(address sender, address recipient, uint256 amount) returns (bool)"
    ];

    const erc20Interface = new ethers.Interface(erc20ABI);

    const calldata2 = erc20Interface.encodeFunctionData("approve", [
        addresses.core.StrategicSwap,    // 被授权地址
        exthAmount                       // 授权金额
    ]);

    console.log("\n✅ Calldata 2 (EXTH.approve):");
    console.log(calldata2);

    // --- 输出提案所需的全部信息 ---
    console.log("\n" + "=".repeat(80));
    console.log("📝 提案信息汇总");
    console.log("=".repeat(80));

    console.log("\n【推荐】使用方案 1 - Treasure.withdrawERC20:");
    console.log(`  Target (目标合约): ${addresses.core.Treasure}`);
    console.log(`  Value (ETH 金额): 0`);
    console.log(`  Calldata: ${calldata1}`);
    console.log(`  Description: Approve strategic investment - Transfer ${ethers.formatUnits(exthAmount, 6)} EXTH to StrategicSwap contract`);

    console.log("\n【备选】使用方案 2 - EXTH.approve:");
    console.log(`  Target (目标合约): ${addresses.tokens.EXTH}`);
    console.log(`  Value (ETH 金额): 0`);
    console.log(`  Calldata: ${calldata2}`);
    console.log(`  Description: Approve strategic investment - Authorize StrategicSwap to use ${ethers.formatUnits(exthAmount, 6)} EXTH`);

    console.log("\n⚠️  注意事项:");
    console.log("1. 确保 Timelock 是 Treasure 合约的 authorizedCaller");
    console.log("2. 确保 Treasure 合约中有足够的 EXTH 余额");
    console.log("3. 如果 Treasure 没有 authorizedCaller 限制，可以直接用 transfer");

    // --- 保存结果到文件 ---
    const output = {
        timestamp: new Date().toISOString(),
        proposalInfo: {
            recommended: {
                method: "Treasure.withdrawERC20",
                target: addresses.core.Treasure,
                value: "0",
                calldata: calldata1,
                description: `Approve strategic investment - Transfer ${ethers.formatUnits(exthAmount, 18)} EXTH to StrategicSwap contract`
            },
            alternative: {
                method: "EXTH.approve",
                target: addresses.tokens.EXTH,
                value: "0",
                calldata: calldata2,
                description: `Approve strategic investment - Authorize StrategicSwap to use ${ethers.formatUnits(exthAmount, 18)} EXTH`
            }
        },
        parameters: {
            exthToken: addresses.tokens.EXTH,
            usdtToken: addresses.tokens.USDT,
            strategicSwap: addresses.core.StrategicSwap,
            treasure: addresses.core.Treasure,
            exthAmount: exthAmount.toString(),
            exthAmountFormatted: ethers.formatUnits(exthAmount, 18)
        }
    };

    const outputFile = path.join(__dirname, "strategic-swap-proposal.json");
    fs.writeFileSync(outputFile, JSON.stringify(output, null, 2));

    console.log(`\n💾 提案信息已保存到：${outputFile}`);
    console.log("\n=== 完成 ===");
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
