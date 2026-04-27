# 📜 Exchange System — Smart Contracts

> `exc-contracts/` — 去中心化兑换系统的智能合约层

本目录包含系统所有链上合约的 Solidity 源码、部署脚本与单元测试。

---

## 📋 目录

- [合约总览](#-合约总览)
- [架构关系](#-架构关系)
- [合约详解](#-合约详解)
- [快速开始](#-快速开始)
- [部署地址](#-部署地址)
- [安全说明](#-安全说明)

---

## 🗂 合约总览

### 核心业务合约

- **Exchange.sol** — P2P 兑换主合约，包含用户分级、交易匹配、手续费收取、争议处理与奖励减半机制
- **Dao.sol** — DAO 治理合约，负责提案创建、投票计票，通过后将指令发送给 Timelock 执行
- **Timelock.sol** — 时间锁合约，DAO 的安全执行官，所有治理操作需经过延迟期后才可执行
- **MultiSigWallet.sol** — 多签钱包仲裁合约，3 人委员会 2/3 签名即可执行争议裁决
- **Treasure.sol** — 金库合约，由 Timelock 控制，负责资金保管、赔偿支付和资产管理

### 代币合约

- **EXTH.sol** — 平台治理代币，支持 ERC20Votes（链上投票）和 ERC20Permit（无 Gas 授权），精度 6 位
- **USDT.sol** — 稳定币模拟合约，精度 6 位，总供应量 1000 亿
- **GBP.sol** — 英镑模拟代币，精度 6 位，总供应量 10,000
- **RNB.sol** — 人民币模拟代币，精度 6 位，总供应量 10,000

### 功能合约

- **Airdrop.sol** — 基于 Merkle Tree 的空投合约，支持白名单验证与未领取代币回收
- **StrategicSwap.sol** — 一次性战略投资原子交换合约，确保 EXTH ↔ USDT 的安全互换

---

## 🏗 架构关系

```
                         ┌──────────────┐
                         │   用户/前端   │
                         └──────┬───────┘
                                │
                    ┌───────────▼───────────┐
                    │    Exchange (交易)     │
                    │                       │
                    │ • 用户分级 (NEW/NORMAL/SEED)
                    │ • 交易匹配 & 分步确认  │
                    │ • 手续费收取 (万分之一) │
                    │ • 奖励减半 (每1亿UT)   │
                    │ • 黑名单管理           │
                    └──┬────────────┬───────┘
                       │            │
            争议仲裁 ──▼──          │
       ┌──────────────────┐        │
       │ MultiSigWallet   │        │
       │ (3人委员会/2签名) │        │
       │                  │        │
       │ • 创建裁决提案    │        │
       │ • 委员投票        │        │
       │ • 拉黑 + 赔偿     │        │
       └───────┬──────────┘        │
               │                   │
               ▼                   │ 治理
       ┌──────────────┐    ┌───────▼────────┐
       │   Treasure   │    │      Dao       │
       │   (金库)     │◄───│   (DAO 治理)    │
       │              │    │                │
       │ • 资金保管    │    │ • 提案/投票     │
       │ • 赔偿支付    │    │ • 快照防操纵    │
       │ • 资产增值    │    │ • 状态机管理    │
       └──────────────┘    └───────┬────────┘
                                   │
                           ┌───────▼────────┐
                           │   Timelock     │
                           │  (时间锁)      │
                           │               │
                           │ • 延迟执行     │
                           │ • 角色权限     │
                           │ • 交易队列     │
                           └───────────────┘

       ┌──────────┐  ┌──────┐  ┌──────┐  ┌──────┐
       │   EXTH   │  │ USDT │  │ GBP  │  │ RNB  │
       │ (治理币) │  │(稳定)│  │(模拟)│  │(模拟)│
       └──────────┘  └──────┘  └──────┘  └──────┘

       ┌──────────────┐    ┌─────────────────┐
       │   Airdrop    │    │  StrategicSwap  │
       │ (Merkle空投)  │    │  (战略投资交换)  │
       └──────────────┘    └─────────────────┘
```

### 合约间调用关系

1. **Dao** → **Timelock** — DAO 将通过的提案排队到 Timelock
2. **Timelock** → **Treasure** / **Exchange** / 任意合约 — 延迟期后执行治理操作
3. **MultiSigWallet** → **Exchange** — 调用 `blacklistUser()` 拉黑违规用户
4. **MultiSigWallet** → **Treasure** — 调用 `payCompensation()` 向受害方赔偿
5. **Exchange** → **EXTH** — 收取手续费、发放交易奖励
6. **Airdrop** → **EXTH** — 向白名单用户分发代币
7. **StrategicSwap** → **EXTH** + **USDT** — 原子化完成投资交换

---

## 📖 合约详解

### Exchange.sol — 交易主合约

> 继承：`Ownable`, `ReentrancyGuard`

**用户分级体系**

- 👶 **NEW (新用户)** — 初始状态，强制作为率先转账方 3 次
- 👤 **NORMAL (普通用户)** — 正常交易，额度 = max(EXTH余额/10⁶ × 10, 1)，上限 70 UT
- 🌟 **SEED (种子用户)** — 持有 ≥ 900 EXTH 自动升级，享受更高权限

**核心参数**

- `FEE_RATE` — 万分之一 (1/10000) EXTH 手续费
- `MIN_UT` / `MAX_UT` — 单笔交易额度 1–70 UT (1 UT = 100 USD)
- `INITIAL_REWARD` — 初始奖励 0.05 EXTH / 笔
- `REWARD_HALVING_INTERVAL` — 每 1 亿 UT 交易量，奖励减半

**核心函数**

- `requestMatch(amount)` — 用户发起匹配请求，触发链上事件供后端监听
- `createTradePair(partyA, partyB, amount)` — 后端调用，创建交易对
- `completeTrade(tradeId)` — 标记交易完成
- `collectFee(tradeId, feeAmount)` — 交易完成后收取双方手续费
- `disputeTrade(tradeId, disputedParty)` — 发起争议，提交仲裁
- `blacklistUser(user)` — 将违规用户永久拉黑（仅限授权调用者）

---

### Dao.sol — DAO 治理合约

**提案状态机**

```
Pending → Active → Succeeded → Queued → Executed
                 ↘ Defeated
       ↘ Canceled
```

- `Pending / Active` — 投票进行中
- `Succeeded` — 投票通过（赞成 > 反对），等待排队
- `Defeated` — 投票未通过
- `Queued` — 已进入 Timelock 等待期
- `Executed` — 已执行
- `Canceled` — 被提案人取消

**核心机制**

- **投票权重** — 基于 `ERC20Votes` 的 `getPastVotes()`，使用快照区块号防止闪电贷操纵
- **投票周期** — 默认 10 分钟（可通过治理修改）
- **提案人权限** — 只有提案创建者可以在 `Pending` 或 `Active` 状态下取消提案

**核心函数**

- `propose(target, value, callData, description)` — 创建提案
- `vote(proposalId, support)` — 投票（true = 赞成，false = 反对）
- `queue(proposalId)` — 将通过的提案发送到 Timelock 排队
- `execute(proposalId)` — 延迟期满后执行提案
- `cancel(proposalId)` — 提案人取消提案

---

### Timelock.sol — 时间锁合约

> 继承：`AccessControl`

**角色体系**

- `DEFAULT_ADMIN_ROLE` — 管理员，可授予/撤销其他角色
- `PROPOSER_ROLE` — 提案者（授予 Dao 合约），可排队和取消交易
- `EXECUTOR_ROLE` — 执行者（授予 `address(0)` 即任何人均可执行）

**核心函数**

- `queueTransaction(target, value, data)` — 排队交易，计算 ETA = 当前时间 + minDelay
- `executeTransaction(target, value, data, eta)` — ETA 到达后执行交易
- `cancelTransaction(target, value, data, eta)` — 取消已排队的交易
- `updateMinDelay(newMinDelay)` — 仅合约自身可调用（必须通过 DAO 提案）

---

### MultiSigWallet.sol — 多签仲裁合约

> 继承：`Ownable`, `ReentrancyGuard`

**仲裁参数**

- `MAX_COMMITTEE_SIZE` — 3 人委员会
- `REQUIRED_SIGNATURES` — 2/3 签名即可执行
- `VOTING_PERIOD` — 10 分钟投票窗口

**仲裁流程**

1. 委员会成员调用 `createProposal()` 发起裁决提案
2. 其他委员调用 `voteProposal()` 投票（任一票反对直接否决）
3. 达到 2 票赞成后自动执行：拉黑违规方 + 从金库赔偿受害方

---

### Treasure.sol — 金库合约

> 继承：`Ownable`

**资金管理**

- `withdrawETH(to, amount)` — 提取 ETH（仅 Owner / Timelock）
- `withdrawERC20(token, to, amount)` — 提取 ERC20 代币（仅 Owner / Timelock）
- `payCompensation(token, victim, amount)` — 赔偿支付（授权调用者，如 MultiSigWallet）
- `executeCall(target, value, data)` — 通用调用接口，可集成 DeFi 协议实现资产增值

**权限体系**

- `owner` — Timelock 合约地址
- `authorizedCallers` — 授权调用者映射（包括 MultiSigWallet）

---

### Airdrop.sol — 空投合约

> 继承：`Ownable`（Owner 为 Timelock）

- `claim(amount, merkleProof)` — 用户提交 Merkle 证明领取空投
- `reclaimTokens()` — DAO 通过提案回收未领取的代币
- `isClaimed(user)` — 查询领取状态
- 使用 `immutable` 变量存储 `merkleRoot` 和 `token`，部署后不可篡改

---

### StrategicSwap.sol — 战略投资交换合约

> 继承：`Ownable`（Owner 为 Timelock）

- **原子交换** — 投资者的 USDT 和 DAO 的 EXTH 在同一笔交易中完成互换
- `executeSwap()` — 投资者调用，前提：DAO 已存入 EXTH + 投资者已 approve USDT
- `reclaimDAOFunds()` — 安全后门，允许 DAO 取回长期未完成交换的 EXTH

---

### 模拟代币合约

- **USDT.sol** — 稳定币模拟，`decimals: 6`，总量 `100,000,000,000`
- **GBP.sol** — 英镑模拟，`decimals: 6`，总量 `10,000`
- **RNB.sol** — 人民币模拟，`decimals: 6`，总量 `10,000`

> ⚠️ 这些是测试用模拟代币，非真实资产，仅用于 Sepolia 测试网。

---

## 🚀 快速开始

### 环境要求

- **Node.js** `≥ 18.x`
- **Hardhat** `2.28.6`
- **Solidity** `^0.8.21`

### 安装

```bash
cd exc-contracts
npm install
```

### 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件：

```env
# 部署账户私钥（切勿使用主网私钥！）
PRIVATE_KEY=your_wallet_private_key

# Etherscan API Key（用于合约验证）
ETHERSCAN_API_KEY=your_etherscan_api_key

# RPC 节点地址
SEPOLIA_RPC_URL=https://sepolia.infura.io/v3/your_project_id
```

### 编译

```bash
npx hardhat compile
```

### 测试

```bash
# 运行全部测试
npx hardhat test

# 查看 Gas 消耗报告
REPORT_GAS=true npx hardhat test

# 运行特定测试文件
npx hardhat test test/Exchange.test.js
```

### 部署

```bash
# 部署到 Sepolia 测试网
npx hardhat run scripts/deploy.js --network sepolia

# 部署到本地 Hardhat 节点
npx hardhat node
npx hardhat run scripts/deploy.js --network localhost
```

### 合约验证

```bash
npx hardhat verify --network sepolia <CONTRACT_ADDRESS> <CONSTRUCTOR_ARGS>
```

---

## 📍 部署地址

### Sepolia 测试网

- **EXTH (治理代币)** — [`0xD74347dE964F59f40d55761e9e1e82aBB57F7533`](https://sepolia.etherscan.io/address/0xD74347dE964F59f40d55761e9e1e82aBB57F7533)
- **USDT (稳定币)** — [`0xaCc2AAC99533e857fBC7091821E74465cE019806`](https://sepolia.etherscan.io/address/0xaCc2AAC99533e857fBC7091821E74465cE019806)
- **Exchange (交易)** — [`0xD29A830C4CfC0D9fF017Fbb14B572a9a2F9b2930`](https://sepolia.etherscan.io/address/0xD29A830C4CfC0D9fF017Fbb14B572a9a2F9b2930)
- **DAO (治理)** — [`0xB5168879C3A8b71DD4645C22465D5f9106df433d`](https://sepolia.etherscan.io/address/0xB5168879C3A8b71DD4645C22465D5f9106df433d)
- **Timelock (时间锁)** — [`0xFd2F6B0467adb7a140c7a6ff0cB701EA3dA39e87`](https://sepolia.etherscan.io/address/0xFd2F6B0467adb7a140c7a6ff0cB701EA3dA39e87)
- **MultiSigWallet (多签)** — [`0x349EFC3c2f59dAdBaDbd5Fb011Ea92E701Fe3582`](https://sepolia.etherscan.io/address/0x349EFC3c2f59dAdBaDbd5Fb011Ea92E701Fe3582)
- **Treasure (金库)** — [`0x42D15000E8A7dCBA6a9416960864F3dD46Be2fE9`](https://sepolia.etherscan.io/address/0x42D15000E8A7dCBA6a9416960864F3dD46Be2fE9)
- **Airdrop (空投)** — [`0x0894c5c63D6239241945e502b86F82ab5a4E3A52`](https://sepolia.etherscan.io/address/0x0894c5c63D6239241945e502b86F82ab5a4E3A52)

> 部署者：`0xa6aB9fC9DD0f85B3659F8DE18b07989d3c7C238e`

---

## 🔐 安全说明

### 已采用的安全措施

- ✅ **ReentrancyGuard** — Exchange 和 MultiSigWallet 使用 OpenZeppelin 重入锁
- ✅ **Checks-Effects-Interactions** — Airdrop 的 `claim()` 严格遵循 CEI 模式
- ✅ **AccessControl / Ownable** — 关键函数设有权限控制
- ✅ **Immutable 变量** — Airdrop 和 StrategicSwap 核心参数部署后不可变
- ✅ **时间锁保护** — 所有治理操作须经过延迟期
- ✅ **快照投票** — DAO 使用 `getPastVotes()` 防止闪电贷操纵

### 已知风险与局限

- ⚠️ **未经专业审计** — 合约未通过第三方安全审计，可能存在未发现的漏洞
- ⚠️ **中心化风险** — `createTradePair()` 和 `completeTrade()` 依赖后端调用，存在单点信任
- ⚠️ **模拟代币** — USDT / GBP / RNB 为测试用模拟代币，无真实价值
- ⚠️ **投票周期短** — Dao 和 MultiSigWallet 默认投票期仅 10 分钟，仅适合测试环境
- ⚠️ **`setVotingPeriod()` 无权限控制** — Dao 合约中该函数任何人均可调用，生产环境需修复

> **⚠️ 本项目仅供学习研究，切勿用于真实资金交易。**

---

## 🛠 依赖

- **Solidity** `^0.8.21` — 合约语言
- **Hardhat** `2.28.6` — 开发与测试框架
- **OpenZeppelin Contracts** `5.1.0` — 安全合约库（ERC20, Ownable, AccessControl, ReentrancyGuard, MerkleProof）
- **Chainlink** `1.5.0` — 预言机服务
- **ethers.js** — Hardhat 内置，用于部署脚本和测试
