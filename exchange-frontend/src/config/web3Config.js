// src/config/web3Config.js
export const WEB3_CONFIG = {
    // 网络配置
    networks: {
        localhost: {
            chainId: '31337',
            chainName: 'Localhost',
            rpcUrl: 'http://127.0.0.1:8545',
            blockExplorerUrl: '',
            nativeCurrency: {
                name: 'ETH',
                symbol: 'ETH',
                decimals: 18
            }
        },
        sepolia: {
            chainId: '0xaa36a7',
            chainName: 'Sepolia',
            rpcUrl: 'https://sepolia.infura.io/v3/YOUR_INFURA_KEY',
            blockExplorerUrl: 'https://sepolia.etherscan.io',
            nativeCurrency: {
                name: 'ETH',
                symbol: 'ETH',
                decimals: 18
            }
        },
        goerli: {
            chainId: '0x5',
            chainName: 'Goerli Testnet',
            rpcUrl: 'https://goerli.infura.io/v3/YOUR_INFURA_KEY',
            blockExplorerUrl: 'https://goerli.etherscan.io',
            nativeCurrency: {
                name: 'ETH',
                symbol: 'ETH',
                decimals: 18
            }
        }
    },

    // Gas 配置
    gas: {
        gasLimit: 300000,
        maxFeePerGas: undefined,
        maxPriorityFeePerGas: undefined
    },

    // 合约地址配置（需要根据实际部署填写）
    contractAddresses: {
        Dao: import.meta.env.VITE_DAO_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000',
        Exchange: import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000',
        Airdrop: import.meta.env.VITE_AIRDROP_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000',
        EXTH: import.meta.env.VITE_EXTH_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000',
        USDT: import.meta.env.VITE_USDT_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000',
        Timelock: import.meta.env.VITE_TIMELOCK_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000',
        Treasure: import.meta.env.VITE_TREASURE_CONTRACT_ADDRESS || '0x0000000000000000000000000000000000000000'
    },

    // 合约 ABI（应该从 artifacts 导入，而不是路径字符串）
    // 示例：import AirdropABI from '@/artifacts/Airdrop.json'
    abiPaths: {
        Airdrop: '/artifacts/contracts/Airdrop.sol/Airdrop.json',
        Exchange: '/artifacts/contracts/Exchange.sol/Exchange.json',
        USDT: '/artifacts/contracts/USDT.sol/USDT.json',
        EXTH: '/artifacts/contracts/EXTH.sol/EXTH.json',
        Dao: '/artifacts/contracts/Dao.sol/Dao.json',
        Timelock: '/artifacts/contracts/Timelock.sol/Timelock.json',
        Treasure: '/artifacts/contracts/Treasure.sol/Treasure.json'
    },

    // RPC 超时配置
    rpcTimeout: 30000, // 30 秒

    // 重试配置
    retryConfig: {
        maxRetries: 3,
        retryDelay: 1000
    }
}

export default WEB3_CONFIG
