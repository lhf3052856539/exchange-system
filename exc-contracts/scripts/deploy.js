// scripts/deploy.js
const hre = require("hardhat");
const fs = require('fs');


async function main() {
    const [deployer] = await ethers.getSigners();
    console.log("Deploying contracts with account:", deployer.address);
    console.log("Account balance:", (await ethers.provider.getBalance(deployer.address)).toString());

    // 1. 部署 EXTH 平台代币
    console.log("\n1. Deploying EXTH token...");
    const EXTH = await ethers.getContractFactory("EXTH");
    const exth = await EXTH.deploy();
    await exth.waitForDeployment();
    console.log("EXTH deployed to:", await exth.getAddress());

    // 2. 部署 GBP 代币
    console.log("\n2. Deploying GBP token...");
    const GBP = await ethers.getContractFactory("GBP");
    const gbp = await GBP.deploy();
    await gbp.waitForDeployment();
    console.log("GBP deployed to:", await gbp.getAddress());

    // 3. 部署 RNB 代币
    console.log("\n3. Deploying RNB token...");
    const RNB = await ethers.getContractFactory("RNB");
    const rnb = await RNB.deploy();
    await rnb.waitForDeployment();
    console.log("RNB deployed to:", await rnb.getAddress());

    // 4. 部署 USDT 稳定币
    console.log("\n4. Deploying USDT token...");
    const USDT = await ethers.getContractFactory("USDT");
    const usdt = await USDT.deploy();
    await usdt.waitForDeployment();
    console.log("USDT deployed to:", await usdt.getAddress());

    // 5. 部署 Timelock 合约（_minDelay: 3 分钟，_proposer: 部署者地址）
    console.log("\n5. Deploying Timelock contract...");
    const minDelay = 180; // 3 分钟 = 180 秒
    const Timelock = await ethers.getContractFactory("Timelock");
    const timelock = await Timelock.deploy(minDelay);
    await timelock.waitForDeployment();
    console.log("Timelock deployed to:", await timelock.getAddress());

    // 6. 部署 Airdrop 合约
    console.log("\n6. Deploying Airdrop contract...");
    const merkleRoot = "0x21cc9da97e72090d0d4198fc7e5ef56c47578bb76c2c2d6212c13a730a56185b";
    const Airdrop = await ethers.getContractFactory("Airdrop");
    const airdrop = await Airdrop.deploy(merkleRoot, await exth.getAddress(), await timelock.getAddress());
    await airdrop.waitForDeployment();
    console.log("Airdrop deployed to:", await airdrop.getAddress());
    console.log("Merkle Root:", merkleRoot);


    // 7. 部署 Dao 合约
    console.log("\n6. Deploying Dao contract...");
    const Dao = await ethers.getContractFactory("Dao");
    const dao = await Dao.deploy(await exth.getAddress(), await timelock.getAddress());
    await dao.waitForDeployment();
    console.log("Dao deployed to:", await dao.getAddress());

    // 从 Timelock 合约中读取 PROPOSER_ROLE 的哈希值
    const PROPOSER_ROLE = await timelock.DEFAULT_ADMIN_ROLE();
    const daoAddress = await dao.getAddress();

    // 授予 Dao 合约 PROPOSER_ROLE 角色
    console.log(`Granting PROPOSER_ROLE to Dao contract at ${daoAddress}...`);
    const grantTx = await timelock.grantRole(PROPOSER_ROLE, daoAddress);
    await grantTx.wait(1); // 等待交易被确认
    console.log("-> PROPOSER_ROLE granted to Dao contract.");


    // 8. 部署 Exchange 合约
    console.log("\n7. Deploying Exchange contract...");
    const Exchange = await ethers.getContractFactory("Exchange");
    const exchange = await Exchange.deploy(
        await exth.getAddress(),
        await usdt.getAddress()
    );
    await exchange.waitForDeployment();
    console.log("Exchange deployed to:", await exchange.getAddress());

    // 9. 部署 Treasure 合约
    console.log("\n8. Deploying Treasure contract...");
    const Treasure = await ethers.getContractFactory("Treasure");
    const treasure = await Treasure.deploy();
    await treasure.waitForDeployment();
    console.log("Treasure deployed to:", await treasure.getAddress());

    // 10. 部署 StrategicSwap 合约
    console.log("\n7. Deploying StrategicSwap contract...");
    const StrategicSwap = await ethers.getContractFactory("StrategicSwap");
    const strategicSwap = await StrategicSwap.deploy(
        await exth.getAddress(),
        await usdt.getAddress(),
        "0x438388eB3158a7417b2362A52A17b442aE8c2FB2",   //投资者地址
        await treasure.getAddress(),
        ethers.parseUnits("50000000000", 6),
        ethers.parseUnits("50000000000", 6),
        await timelock.getAddress()
    );
    await strategicSwap.waitForDeployment();
    console.log("StrategicSwap deployed to:", await strategicSwap.getAddress());
    console.log("Investor:", "0x438388eB3158a7417b2362A52A17b442aE8c2FB2");
    console.log("EXTH Amount:", "50000000000");
    console.log("USDT Amount:", "50000000000");

    // 11. 调用 EXTH 合约的 mint 函数，铸造 150,000,000,000 代币给 Treasure 合约
    console.log("\n9. Minting 150,000,000,000 EXTH tokens to Treasure contract...");
    const mintAmount = ethers.parseUnits("150000000000", 6);
    const mintTx = await exth.mint(await treasure.getAddress(), mintAmount);
    await mintTx.wait();
    console.log("Minted EXTH tokens to Treasure:", await treasure.getAddress());

    // 12. 将 EXTH 合约的所有权转移给 Timelock 合约
    console.log("\n10. Transferring EXTH ownership to Timelock...");
    const transferExthTx = await exth.transferOwnership(await timelock.getAddress());
    await transferExthTx.wait();
    console.log("EXTH ownership transferred to Timelock");

    // 13. 调用 Treasure 合约的 withdrawERC20 函数，向两个地址分发 900 EXTH
    console.log("\n11. Distributing 900 EXTH to test addresses via Treasure.withdrawERC20...");
    const withdrawAmount = ethers.parseUnits("900", 6);
    const testAddresses = [
        "0x31DC8e70A43f761a75229484975E416Ea53dcDC6",
        "0x652A5bdA0138EfeC56EDAA0458428b7e604B7849"
    ];

    for (const addr of testAddresses) {
        const withdrawTx = await treasure.withdrawERC20(await exth.getAddress(), addr, withdrawAmount);
        await withdrawTx.wait();
        console.log(`Withdrawn 900 EXTH to ${addr}`);
    }

    // 14. 将 Exchange 合约的所有权转移给 Timelock 合约
    console.log("\n12. Transferring Exchange ownership to Timelock...");
    const transferExchangeTx = await exchange.transferOwnership(await timelock.getAddress());
    await transferExchangeTx.wait();
    console.log("Exchange ownership transferred to Timelock");

    // 15. 将 Treasure 合约的所有权转移给 Timelock 合约
    console.log("\n13. Transferring Treasure ownership to Timelock...");
    const transferTreasureTx = await treasure.transferOwnership(await timelock.getAddress());
    await transferTreasureTx.wait();
    console.log("Treasure ownership transferred to Timelock");

    // 16. 放弃部署者对 Timelock 合约的管理权限
    console.log("\n14. Renouncing deployer's admin role on Timelock...");
    const DEFAULT_ADMIN_ROLE = "0x0000000000000000000000000000000000000000000000000000000000000000";
    const renounceTx = await timelock.renounceRole(DEFAULT_ADMIN_ROLE, deployer.address);
    await renounceTx.wait();
    console.log("Deployer's admin role on Timelock renounced");

    // 保存所有合约地址到文件
    const addresses = {
        tokens: {
            EXTH: await exth.getAddress(),
            GBP: await gbp.getAddress(),
            RNB: await rnb.getAddress(),
            USDT: await usdt.getAddress()
        },
        core: {
            Timelock: await timelock.getAddress(),
            Dao: await dao.getAddress(),
            Exchange: await exchange.getAddress(),
            Treasure: await treasure.getAddress(),
            Airdrop: await airdrop.getAddress(),
            StrategicSwap: await strategicSwap.getAddress()
        },
        deployer: deployer.address,
        timestamp: new Date().toISOString()
    };

    fs.writeFileSync('contract-addresses.json', JSON.stringify(addresses, null, 2));
    console.log("\n✅ All contract addresses saved to contract-addresses.json");

    console.log("\n✅ All contracts deployed and initialized successfully!");
    console.log("\n📋 Deployment Summary:");
    console.log("1. ✅ EXTH token deployed");
    console.log("2. ✅ GBP token deployed");
    console.log("3. ✅ RNB token deployed");
    console.log("4. ✅ USDT token deployed");
    console.log("5. ✅ Timelock deployed (3min delay, proposer: deployer)");
    console.log("6. ✅ Airdrop deployed (with Merkle Root)");
    console.log("7. ✅ StrategicSwap deployed (for investor exchange)");
    console.log("8. ✅ Dao deployed");
    console.log("9. ✅ Exchange deployed (by 0xa6aB9fC9DD0f85B3659F8DE18b07989d3c7C238e)");
    console.log("10. ✅ Treasure deployed (by 0xa6aB9fC9DD0f85B3659F8DE18b07989d3c7C238e)");
    console.log("11. ✅ Minted 150B EXTH to Treasure");
    console.log("12. ✅ EXTH ownership → Timelock");
    console.log("13. ✅ Distributed 900 EXTH to 2 test addresses");
    console.log("14. ✅ Exchange ownership → Timelock");
    console.log("15. ✅ Treasure ownership → Timelock");
    console.log("16. ✅ Deployer renounced Timelock admin role");
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
