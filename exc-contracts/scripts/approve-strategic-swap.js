// scripts/approve-strategic-swap.js
const hre = require("hardhat");
require('dotenv').config();
const fs = require('fs');

async function main() {
    // ✅ 直接使用战略投资者的私钥
    const privateKey = process.env.PRIVATE_KEY;
    if (!privateKey) {
        throw new Error("PRIVATE_KEY not set in .env");
    }

    const wallet = new ethers.Wallet(privateKey, hre.ethers.provider);
    console.log("=== StrategicSwap USDT 授权脚本 ===\n");
    console.log("当前账户:", await wallet.getAddress());
    console.log("账户 ETH 余额:", hre.ethers.formatEther(await hre.ethers.provider.getBalance(await wallet.getAddress())), "ETH\n");

    // 读取已部署的合约地址
    const addresses = JSON.parse(fs.readFileSync('contract-addresses.json', 'utf8'));

    const usdtAddress = addresses.tokens.USDT;
    const strategicSwapAddress = addresses.core.StrategicSwap;

    // 战略投资者地址
    const strategicInvestorAddress = "0x438388eB3158a7417b2362A52A17b442aE8c2FB2";

    console.log("USDT 合约地址:", usdtAddress);
    console.log("StrategicSwap 合约地址:", strategicSwapAddress);
    console.log("战略投资者地址:", strategicInvestorAddress);

    // ✅ 获取 USDT 合约实例（使用正确的 ethers v6 语法）
    const usdt = await hre.ethers.getContractAt("USDT", usdtAddress, wallet);

    // ✅ 获取 StrategicSwap 合约实例
    const strategicSwap = await hre.ethers.getContractAt("StrategicSwap", strategicSwapAddress, wallet);

    // 从合约读取规定的 USDT 数量
    const usdtAmount = await strategicSwap.usdtAmount();
    //const usdtAmount = 5000000000;
    console.log("\n📋 StrategicSwap 合约规定的 USDT 数量:", hre.ethers.formatUnits(usdtAmount, 6), "USDT");

    const approveAmount = usdtAmount;
    console.log("准备授权额度:", hre.ethers.formatUnits(approveAmount, 6), "USDT");

    // 检查战略投资者地址的余额是否足够
    const balance = await usdt.balanceOf(strategicInvestorAddress);
    console.log("战略投资者 USDT 余额:", hre.ethers.formatUnits(balance, 6), "USDT");

    if (balance < approveAmount) {
        console.log("\n❌ 错误：USDT 余额不足！");
        console.log("需要:", hre.ethers.formatUnits(approveAmount, 6), "USDT");
        console.log("当前余额:", hre.ethers.formatUnits(balance, 6), "USDT");
        console.log("请先转账足够的 USDT 到战略投资者地址:", strategicInvestorAddress);
        return;
    }

    console.log("\n🚀 正在执行授权交易...");
    const approveTx = await usdt.approve(strategicSwapAddress, approveAmount);
    console.log("交易哈希:", approveTx.hash);

    console.log("⏳ 等待交易确认...");
    await approveTx.wait();

    console.log("✅ 交易已确认！");

    // 验证授权结果
    const newAllowance = await usdt.allowance(strategicInvestorAddress, strategicSwapAddress);
    console.log("\n✅ 授权成功！");
    console.log("新授权额度:", hre.ethers.formatUnits(newAllowance, 6), "USDT");

    console.log("\n=== 授权完成 ===");
}

main()
    .then(() => process.exit(0))
    .catch((error) => {
        console.error(error);
        process.exit(1);
    });
