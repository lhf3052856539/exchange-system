const { ethers } = require("ethers");
const addresses = require('./contract-addresses.json'); // 确保地址文件路径正确

// Treasure 合约的 ABI，我们只需要 withdrawERC20 这一个函数的信息
const treasureABI = [
    "function withdrawERC20(address token, address to, uint256 amount)"
];

// 1. 创建一个 Interface 实例
const treasureInterface = new ethers.Interface(treasureABI);

// 2. 定义我们要调用的函数的参数值
// 从地址文件中获取
const exthTokenAddress = addresses.tokens.EXTH;
const airdropContractAddress = addresses.core.Airdrop;

// 准备转100万个EXTH (EXTH代币有6位小数)
const amountToTransfer = ethers.parseUnits("1000000", 6);

// 3. 使用 encodeFunctionData 进行编码
const calldata = treasureInterface.encodeFunctionData("withdrawERC20", [
    exthTokenAddress,
    airdropContractAddress,
    amountToTransfer
]);

console.log("✅ Proposal Call Data Generated (Should start with 0x3e33629b):");
console.log(calldata);
