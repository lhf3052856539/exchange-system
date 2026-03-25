require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

// 添加调试输出，检查环境变量是否被正确加载
console.log("DEBUG: Loading environment variables...");
console.log("SEPOLIA_RPC_URL:", process.env.SEPOLIA_RPC_URL ? "Loaded" : "Not found");
console.log("INFURA_PROJECT_ID:", process.env.INFURA_PROJECT_ID ? "Loaded" : "Not found");
console.log("PRIVATE_KEY:", process.env.PRIVATE_KEY ? "Loaded (first 6 chars: " + process.env.PRIVATE_KEY.substring(0, 6) + ")" : "Not found");
console.log("ETHERSCAN_API_KEY:", process.env.ETHERSCAN_API_KEY ? "Loaded" : "Not found");

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
    solidity: {
        version: "0.8.21",
        settings: {
            optimizer: {
                enabled: true,
                runs: 200
            }
        }
    },
    networks: {
        sepolia: {
            url: process.env.SEPOLIA_RPC_URL || "https://sepolia.infura.io/v3/" + process.env.INFURA_PROJECT_ID,
            accounts: process.env.PRIVATE_KEY ? [process.env.PRIVATE_KEY] : [],
            chainId: 11155111,
            timeout: 120000
        },
        localhost: {
            url: "http://127.0.0.1:8545",
            chainId: 31337
        }
    },
    etherscan: {
        apiKey: process.env.ETHERSCAN_API_KEY
    },
    paths: {
        sources: "./contracts",
        tests: "./test",
        cache: "./cache",
        artifacts: "./artifacts"
    }
};
