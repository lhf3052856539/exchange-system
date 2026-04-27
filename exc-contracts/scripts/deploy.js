// scripts/deploy.js
const hre = require("hardhat");
const fs = require('fs');


async function main() {
    const [deployer] = await ethers.getSigners();
    console.log("Deploying contracts with account:", deployer.address);
    console.log("Account balance:", (await ethers.provider.getBalance(deployer.address)).toString());

    //1. 部署所有代币合约
    console.log("\n1. Deploying EXTH token...");
    const EXTH = await ethers.getContractFactory("EXTH");
    const exth = await EXTH.deploy();
    await exth.waitForDeployment();
    console.log("-> EXTH deployed to:", await exth.getAddress());

    console.log("\n2. Deploying GBP token...");
    const GBP = await ethers.getContractFactory("GBP");
    const gbp = await GBP.deploy();
    await gbp.waitForDeployment();
    console.log("-> GBP deployed to:", await gbp.getAddress());

    console.log("\n3. Deploying RNB token...");
    const RNB = await ethers.getContractFactory("RNB");
    const rnb = await RNB.deploy();
    await rnb.waitForDeployment();
    console.log("-> RNB deployed to:", await rnb.getAddress());

    console.log("\n4. Deploying USDT token...");
    const USDT = await ethers.getContractFactory("USDT");
    const usdt = await USDT.deploy();
    await usdt.waitForDeployment();
    console.log("-> USDT deployed to:", await usdt.getAddress());


    //2. 部署核心治理合约 (Timelock & DAO)
    console.log("\n5. Deploying Timelock contract...");
    const minDelay = 180; // 3 分钟
    const Timelock = await ethers.getContractFactory("Timelock");
    const timelock = await Timelock.deploy(minDelay);
    await timelock.waitForDeployment();
    console.log("-> Timelock deployed to:", await timelock.getAddress());

    console.log("\n6. Deploying Dao contract...");
    const Dao = await ethers.getContractFactory("Dao");
    const dao = await Dao.deploy(await exth.getAddress(), await timelock.getAddress());
    await dao.waitForDeployment();
    console.log("-> Dao deployed to:", await dao.getAddress());

    //3. 配置治理角色
    console.log("\n7. Granting roles to Dao contract...");
    const PROPOSER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("PROPOSER_ROLE"));
    const EXECUTOR_ROLE = ethers.keccak256(ethers.toUtf8Bytes("EXECUTOR_ROLE"));

    await (await timelock.grantRole(PROPOSER_ROLE, await dao.getAddress())).wait();
    console.log("-> PROPOSER_ROLE granted to Dao contract.");

    await (await timelock.grantRole(EXECUTOR_ROLE, await dao.getAddress())).wait();
    console.log("-> EXECUTOR_ROLE granted to Dao contract.");

    //4. 部署应用层核心合约
    console.log("\n8. Deploying Exchange contract...");
    const Exchange = await ethers.getContractFactory("Exchange");
    const exchange = await Exchange.deploy(await exth.getAddress(), await usdt.getAddress());
    await exchange.waitForDeployment();
    console.log("-> Exchange deployed to:", await exchange.getAddress());

    console.log("\n9. Deploying Treasure contract...");
    const Treasure = await ethers.getContractFactory("Treasure");
    const treasure = await Treasure.deploy();
    await treasure.waitForDeployment();
    console.log("-> Treasure deployed to:", await treasure.getAddress());

    // 设置 Exchange 合约中的金库合约地址
    console.log("\n9.5 Setting Treasure address in Exchange...");
    const setTreasureTx = await exchange.setTreasure(await treasure.getAddress());
    await setTreasureTx.wait();
    console.log("-> Exchange contract is now connected to Treasure contract.");

    //部署多签钱包合约 ️
    console.log("\n10. Deploying MultiSigWallet (Arbitration) contract...");

    const committeeMembers = [
        "0x31DC8e70A43f761a75229484975E416Ea53dcDC6",
        "0x652A5bdA0138EfeC56EDAA0458428b7e604B7849",
        deployer.address // 将部署者作为第三个成员用于测试
    ];
    const MultiSigWallet = await ethers.getContractFactory("MultiSigWallet");
    const multiSigWallet = await MultiSigWallet.deploy(
        await treasure.getAddress(),
        await usdt.getAddress(),
        await exth.getAddress(),
        committeeMembers
    );
    await multiSigWallet.waitForDeployment();
    console.log(`-> MultiSigWallet deployed to: ${await multiSigWallet.getAddress()}`);
    console.log(`-> With initial committee: ${committeeMembers.join(', ')}`);

    //初始化多签钱包，连接 Exchange 合约
    console.log("\n11. Initializing MultiSigWallet...");
    const setExchangeTx = await multiSigWallet.setExchangeContract(await exchange.getAddress());
    await setExchangeTx.wait();
    console.log("-> MultiSigWallet connected to Exchange contract.");

    // 授权 MultiSigWallet 调用 Exchange 的黑名单功能
    console.log("\n11.2 Authorizing MultiSigWallet in Exchange...");
    const authorizeMultiSigTx = await exchange.setAuthorizedCaller(await multiSigWallet.getAddress(), true);
    await authorizeMultiSigTx.wait();
    console.log("-> MultiSigWallet is now authorized to blacklist users in Exchange contract.");

    //将多签钱包加入金库白名单
    console.log("\n11.5 Authorizing MultiSigWallet in Treasure...");

    // 由于此时 deployer 还是 Treasure 的 owner，可以直接调用
    const setMultiSigTx = await treasure.setMultiSigWallet(await multiSigWallet.getAddress());
    await setMultiSigTx.wait();
    console.log("-> MultiSigWallet is now authorized in Treasure contract.");

    // 授权 Exchange 合约从金库提取 EXTH 发放奖励
    console.log("\n11.6 Authorizing Exchange in Treasure...");
    const authorizeExchangeTx = await treasure.addAuthorizedCaller(await exchange.getAddress());
    await authorizeExchangeTx.wait();
    console.log("-> Exchange is now authorized to withdraw EXTH from Treasure.");


    //5. 部署其他辅助合约
    console.log("\n12. Deploying Airdrop contract...");
    const merkleRoot = "0x644932194348b4219f7d47b00668f7f91ee537e4e4dc632aebc7edbcfaba8f8c";
    const Airdrop = await ethers.getContractFactory("Airdrop");
    const airdrop = await Airdrop.deploy(
        merkleRoot,
        await exth.getAddress(),
        await exchange.getAddress(),
        await timelock.getAddress()
    );
    await airdrop.waitForDeployment();
    console.log("-> Airdrop deployed to:", await airdrop.getAddress());

    console.log("\n13. Deploying StrategicSwap contract...");
    const StrategicSwap = await ethers.getContractFactory("StrategicSwap");
    const strategicSwap = await StrategicSwap.deploy(
        await exth.getAddress(), await usdt.getAddress(), "0x438388eB3158a7417b2362A52A17b442aE8c2FB2",
        await treasure.getAddress(), ethers.parseUnits("5000000000", 6), ethers.parseUnits("5000000000", 6),
        await timelock.getAddress()
    );
    await strategicSwap.waitForDeployment();
    console.log("-> StrategicSwap deployed to:", await strategicSwap.getAddress());

    //6. 执行初始设置和铸币
    console.log("\n14. Minting initial EXTH tokens to Treasure contract...");
    const mintAmount = ethers.parseUnits("150000000000", 6);
    await (await exth.mint(await treasure.getAddress(), mintAmount)).wait();
    console.log("-> Minted 150B EXTH tokens to Treasure.");

    console.log("\n15. Initializing seed users...");
    const seedAmount = ethers.parseUnits("900", 6);

    // 定义种子用户地址和对应的私钥变量名
    const seedUsersConfig = [
        {
            addr: "0x31DC8e70A43f761a75229484975E416Ea53dcDC6",
            keyEnv: "SEED_USER_1_PRIVATE_KEY"
        },
        {
            addr: "0x652A5bdA0138EfeC56EDAA0458428b7e604B7849",
            keyEnv: "SEED_USER_2_PRIVATE_KEY"
        }
    ];

    for (const config of seedUsersConfig) {
        console.log(`\n-> Processing seed user: ${config.addr}`);

        let signer;
        // 尝试从环境变量获取私钥
        if (process.env[config.keyEnv]) {
            signer = new ethers.Wallet(process.env[config.keyEnv], ethers.provider);
            console.log(`   Using private key from env for ${config.addr}`);
        } else {
            // 如果没有配置私钥，且是本地网络，则使用 impersonate
            if (hre.network.name === "localhost" || hre.network.name === "hardhat") {
                signer = await ethers.getImpersonatedSigner(config.addr);
                console.log(`   Using impersonated signer for local network`);
            } else {
                // 如果是测试网且没私钥，只能用 deployer 代注册（前提是合约支持 registerUserFor）
                console.log(`   ⚠ No private key found. Attempting admin registration...`);
                signer = deployer;
            }
        }

        // 注册账户
        const userInfoBefore = await exchange.users(config.addr);
        const isRegistered = userInfoBefore.newUserTradeCount > 0 || userInfoBefore.userType !== 0;

        if (!isRegistered) {
            console.log(`   Registering user in Exchange...`);
            // 如果 signer 是 deployer，确保合约里有 registerUserFor 或者 registerUser(address)
            const tx = await exchange.connect(signer).registerUser(config.addr, { gasLimit: 300000 });
            await tx.wait();
            console.log(`   ✓ User registered successfully`);
        } else {
            console.log(`   ℹ User already registered, skipping`);
        }

        // 转账 EXTH
        console.log(`   Transferring 900 EXTH to user...`);
        const transferTx = await treasure.connect(deployer).withdrawERC20(await exth.getAddress(), config.addr, seedAmount);
        await transferTx.wait();
        console.log(`   ✓ Transferred 900 EXTH`);
    }

    // 批量设置为种子用户
    console.log(`\n   Setting users as SEED type in Exchange...`);
    const seedAddrs = seedUsersConfig.map(u => u.addr);
    const initSeedTx = await exchange.connect(deployer).initSeedUsers(seedAddrs);
    await initSeedTx.wait();
    console.log(`   ✓ All seed users initialized successfully`);


    //7. 移交所有权, 完成去中心化
    console.log("\n16. Transferring ownership of all contracts to Timelock...");
    await (await exth.transferOwnership(await timelock.getAddress())).wait();
    console.log("-> EXTH ownership transferred to Timelock.");

    await (await exchange.transferOwnership(await timelock.getAddress())).wait();
    console.log("-> Exchange ownership transferred to Timelock.");

    await (await treasure.transferOwnership(await timelock.getAddress())).wait();
    console.log("-> Treasure ownership transferred to Timelock.");

    //移交多签钱包的管理权给 Timelock️
    await (await multiSigWallet.transferOwnership(await timelock.getAddress())).wait();
    console.log("-> MultiSigWallet ownership transferred to Timelock.");


    //8. 撤销部署者权限
    console.log("\n17. Renouncing deployer's admin role on Timelock...");
    const DEFAULT_ADMIN_ROLE = "0x0000000000000000000000000000000000000000000000000000000000000000";
    await (await timelock.renounceRole(DEFAULT_ADMIN_ROLE, deployer.address)).wait();
    console.log("-> Deployer's admin role on Timelock renounced. Decentralization complete.");

    //9. 保存地址并总结
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
            MultiSigWallet: await multiSigWallet.getAddress(),
            Airdrop: await airdrop.getAddress(),
            StrategicSwap: await strategicSwap.getAddress()
        },
        deployer: deployer.address,
        timestamp: new Date().toISOString()
    };

    fs.writeFileSync('contract-addresses.json', JSON.stringify(addresses, null, 2));
    console.log("\n All contract addresses saved to contract-addresses.json");

    console.log("\n\n Deployment complete! ");

    console.log("\n\n=================================================================");
    console.log("=================================================================\n");
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
