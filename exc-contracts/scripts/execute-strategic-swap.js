// scripts/execute-strategic-swap.js
const hre = require("hardhat");
require('dotenv').config();
const fs = require('fs');

async function main() {
    // 使用战略投资者的私钥
    const privateKey = process.env.PRIVATE_KEY;
    if (!privateKey) {
        throw new Error("PRIVATE_KEY not set in .env");
    }

    const wallet = new ethers.Wallet(privateKey, hre.ethers.provider);
    console.log("=== Strategic Swap Execution ===\n");
    console.log("Current account:", await wallet.getAddress());
    console.log(" baETHlance:", hre.ethers.formatEther(await hre.ethers.provider.getBalance(await wallet.getAddress())), "ETH\n");

    const addresses = JSON.parse(fs.readFileSync('contract-addresses.json', 'utf8'));

    const exthAddress = addresses.tokens.EXTH;
    const usdtAddress = addresses.tokens.USDT;
    const strategicSwapAddress = addresses.core.StrategicSwap;
    const investorAddress = "0x438388eB3158a7417b2362A52A17b442aE8c2FB2";

    console.log("EXTH Token:", exthAddress);
    console.log("USDT Token:", usdtAddress);
    console.log("StrategicSwap:", strategicSwapAddress);
    console.log("Investor:", investorAddress);

    // 获取合约实例
    const StrategicSwap = await hre.ethers.getContractAt("StrategicSwap", strategicSwapAddress, wallet);
    const EXTH = await hre.ethers.getContractAt("USDT", exthAddress, wallet);
    const USDT = await hre.ethers.getContractAt("USDT", usdtAddress, wallet);

    // 检查前置条件
    console.log("\n📋 Checking prerequisites...");

    // 1. 检查 StrategicSwap 合约的 EXTH 余额
    const swapExthBalance = await EXTH.balanceOf(strategicSwapAddress);
    console.log("StrategicSwap EXTH balance:", hre.ethers.formatUnits(swapExthBalance, 6));

    // 2. 检查战略投资者的 USDT 余额
    const investorUsdtBalance = await USDT.balanceOf(investorAddress);
    console.log("Investor USDT balance:", hre.ethers.formatUnits(investorUsdtBalance, 6));

    // 3. 检查战略投资者是否已授权
    const allowance = await USDT.allowance(investorAddress, strategicSwapAddress);
    console.log("Investor USDT allowance to StrategicSwap:", hre.ethers.formatUnits(allowance, 6));

    const requiredAmount = await StrategicSwap.usdtAmount();
    console.log("Required USDT amount:", hre.ethers.formatUnits(requiredAmount, 6));

    if (swapExthBalance < await StrategicSwap.exthAmount()) {
        console.log("\n❌ Error: StrategicSwap doesn't have enough EXTH!");
        return;
    }

    if (investorUsdtBalance < requiredAmount) {
        console.log("\n❌ Error: Investor doesn't have enough USDT!");
        return;
    }

    if (allowance < requiredAmount) {
        console.log("\n❌ Error: Investor hasn't approved StrategicSwap to spend USDT!");
        console.log("Please run: node scripts/approve-strategic-swap.js");
        return;
    }

    // 执行交换
    console.log("\n🚀 Executing swap...");
    const tx = await StrategicSwap.executeSwap();
    console.log("Transaction Hash:", tx.hash);

    console.log("⏳ Waiting for confirmation...");
    const receipt = await tx.wait();

    console.log("\n✅ Swap executed successfully!");
    console.log("Gas used:", receipt.gasUsed.toString());

    // 验证结果
    console.log("\n📊 Final State:");
    const investorExthBalance = await EXTH.balanceOf(investorAddress);
    const treasureUsdtBalance = await USDT.balanceOf(addresses.core.Treasure);

    console.log("Investor EXTH balance:", hre.ethers.formatUnits(investorExthBalance, 6), "EXTH");
    console.log("Treasure USDT balance:", hre.ethers.formatUnits(treasureUsdtBalance, 6), "USDT");
}

main()
    .then(() => process.exit(0))
    .catch((error) => {
        console.error(error);
        process.exit(1);
    });
