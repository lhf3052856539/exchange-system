// Auto-generated Treasure USDT approval calldata
export const TREASURE_USDT_APPROVAL_PROPOSAL = {
    target: "0xA2D892A1D38C904e7fA841404AAD0328c09aEe72",
    value: "0",
    data: "0x095ea7b3000000000000000000000000e9623736a015c867b96a8b24b3b0efd06d162ce0ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
    description: "授权 MultiSigWallet 使用 Treasure 中的 USDT 进行仲裁赔偿",
    parameters: {
        caller: "0xA2D892A1D38C904e7fA841404AAD0328c09aEe72",
        token: "0x4220667A323494d203ff2cd39839b709c75B35c3",
        spender: "0xE9623736a015c867b96a8b24B3b0Efd06d162cE0",
        amount: "115792089237316195423570985008687907853269984665640564039457584007913129639935"
    },
    steps: [
        "1. 创建 DAO 提案",
        "2. 委员会投票（需要 2/3 票）",
        "3. 投票期后执行提案",
        "4. MultiSigWallet 将有权使用 Treasure 中的 USDT"
    ]
};
