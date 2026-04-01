const { ethers } = require("ethers");
const addresses = require('./contract-addresses.json');

async function debugTreasure() {
    // 连接到你的区块链节点（替换为实际的 RPC）
    const provider = new ethers.JsonRpcProvider('https://eth-sepolia.g.alchemy.com/v2/eh_dEB9V7MnEnkpfNh5xq'); // 或者你的 Sepolia RPC

    // ✅ 从配置文件读取正确的地址（包含正确的大小写）
    const treasureAddress = addresses.core.Treasure;
    const exthAddress = addresses.tokens.EXTH;
    const timelockAddress = addresses.core.Timelock;

    console.log('=== Treasure 合约诊断 ===\n');
    console.log('Treasure 地址:', treasureAddress);
    console.log('EXTH 地址:', exthAddress);
    console.log('Timelock 地址:', timelockAddress);
    console.log();

    try {
        // 1. 查询 EXTH 余额
        const exthABI = [
            'function balanceOf(address) view returns (uint256)',
            'function decimals() view returns (uint8)'
        ];
        const exth = new ethers.Contract(exthAddress, exthABI, provider);

        const balance = await exth.balanceOf(treasureAddress);
        const decimals = await exth.decimals();

        console.log('1. Treasure 的 EXTH 余额:');
        console.log('   余额:', ethers.formatUnits(balance, decimals), 'EXTH');
        console.log('   需要提取：10000 EXTH');
        console.log('   余额是否足够？', balance >= ethers.parseUnits('10000', 6) ? '✅ 是' : '❌ 否');
        console.log('   原始余额 (wei):', balance.toString());
        console.log();

        // 2. 查询 Treasure 的 owner
        const treasureABI = ['function owner() view returns (address)'];
        const treasure = new ethers.Contract(treasureAddress, treasureABI, provider);
        const owner = await treasure.owner();

        console.log('2. Treasure 的 owner:', owner);
        console.log('   Timelock 地址:', timelockAddress);
        console.log('   Owner 是否是 Timelock?', owner.toLowerCase() === timelockAddress.toLowerCase() ? '✅ 是' : '❌ 否');
        console.log();

        // 3. 查询 Timelock 是否有 EXECUTOR_ROLE
        const timelockABI = [
            'function hasRole(bytes32, address) view returns (bool)',
            'function EXECUTOR_ROLE() view returns (bytes32)'
        ];
        const timelock = new ethers.Contract(timelockAddress, timelockABI, provider);

        const EXECUTOR_ROLE = await timelock.EXECUTOR_ROLE();
        const hasExecutorRole = await timelock.hasRole(EXECUTOR_ROLE, timelockAddress);

        console.log("3. Timelock 权限检查:");
        console.log("   EXECUTOR_ROLE:", EXECUTOR_ROLE);
        console.log("   Timelock 是否有 EXECUTOR_ROLE?", hasExecutorRole ? "✅ 是" : "❌ 否（正常，不需要）");
        console.log();

        // 4. 查询 DAO 是否有 PROPOSER_ROLE
        const PROPOSER_ROLE = ethers.keccak256(ethers.toUtf8Bytes("PROPOSER_ROLE"));
        const daoAddress = addresses.core.Dao;
        const hasProposerRole = await timelock.hasRole(PROPOSER_ROLE, daoAddress);

        console.log("4. DAO 权限检查:");
        console.log("   DAO 地址:", daoAddress);
        console.log("   PROPOSER_ROLE:", PROPOSER_ROLE);
        console.log("   DAO 是否有 PROPOSER_ROLE?", hasProposerRole ? "✅ 是" : "❌ 否");

        // ✅ 新增：检查 DAO 是否有 EXECUTOR_ROLE
        const hasExecutorRoleForDao = await timelock.hasRole(EXECUTOR_ROLE, daoAddress);
        console.log("   DAO 是否有 EXECUTOR_ROLE?", hasExecutorRoleForDao ? "✅ 是" : "❌ 否（需要修复）");
        console.log();

        if (hasProposerRole && hasExecutorRoleForDao) {
            console.log("✅ 权限配置正确！DAO 可以执行提案了");
        } else if (!hasExecutorRoleForDao) {
            console.log("⚠️ 警告：DAO 缺少 EXECUTOR_ROLE，需要授予");
            console.log("   运行以下命令修复:");
            console.log("   node scripts/fix-executor-role.js");
        }

        console.log();
        console.log("=== 诊断完成 ===");

    } catch (error) {
        console.error('❌ 诊断过程出错:', error.message);
        console.error('请检查：');
        console.error('  1. RPC 节点是否正确连接');
        console.error('  2. contract-addresses.json 中的地址是否正确');
        console.error('  3. 合约是否已部署到该网络');
    }
}

debugTreasure().catch(console.error);
