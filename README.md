# 🔄 去中心化数字货币兑换系统

> Decentralized P2P Digital Currency Exchange System

基于区块链的 P2P 数字货币兑换平台,集成 DAO 治理、争议仲裁和经济激励模型。

![Solidity](https://img.shields.io/badge/Solidity-^0.8.21-363636?logo=solidity)![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.0-6DB33F?logo=springboot)![Vue](https://img.shields.io/badge/Vue-3.4.0-4FC08D?logo=vuedotjs)![License](https://img.shields.io/badge/License-MIT-blue)![Network](https://img.shields.io/badge/Network-Sepolia%20Testnet-7B3FE4)

---

## 📋 目录

- [项目简介](#-项目简介)
- [技术架构](#-技术架构)
- [核心功能](#-核心功能)
- [快速开始](#-快速开始)
- [智能合约部署](#-智能合约部署)
- [项目结构](#-项目结构)
- [核心 API 接口](#-核心-api-接口)
- [技术栈](#-技术栈)
- [安全说明](#-安全说明)
- [License](#-license)

---

## 📖 项目简介

这是一个全栈去中心化应用 (DApp),实现了任意两个陌生用户之间的 P2P 数字货币兑换功能。系统通过智能合约保证交易安全,引入仲裁机制处理争议,并设计了通证经济模型激励用户参与。

### 核心特点

- 🔗 **P2P 交易匹配** — 点对点兑换,无需中心化撮合
- 📊 **用户分级制度** — 根据持币量动态调整交易额度
- ✅ **双重确认机制** — 交易双方分步确认,降低欺诈风险
- ⚖️ **争议仲裁系统** — 多签钱包 + 投票处理纠纷
- 💰 **经济激励模型** — EXTH 代币奖励,早期参与者红利
- 🎁 **Merkle 空投** — 基于 Merkle Tree 证明的空投分发

---

## 🏗 技术架构

### 系统整体架构

### 智能合约架构图

```mermaid
graph TB
    User[用户/前端] --> Exchange
    
    subgraph CoreContracts [核心业务合约]
        Exchange[Exchange<br/>交易主合约]
        Dao[Dao<br/>DAO治理]
        Timelock[Timelock<br/>时间锁]
        MultiSig[MultiSigWallet<br/>多签仲裁]
        Treasure[Treasure<br/>金库]
    end
    
    subgraph TokenContracts [代币合约]
        EXTH[EXTH<br/>治理代币]
        USDT[USDT<br/>稳定币]
        GBP[GBP<br/>英镑模拟]
        RNB[RNB<br/>人民币模拟]
    end
    
    subgraph FeatureContracts [功能合约]
        Airdrop[Airdrop<br/>Merkle空投]
        StrategicSwap[StrategicSwap<br/>战略投资交换]
    end
    
    Exchange -->|争议仲裁| MultiSig
    Exchange -->|收取手续费/发放奖励| EXTH
    
    MultiSig -->|拉黑违规用户| Exchange
    MultiSig -->|赔偿支付| Treasure
    
    Dao -->|提案排队| Timelock
    Timelock -->|延迟执行| Treasure
    Timelock -->|延迟执行| Exchange
    Timelock -->|管理权限| Airdrop
    Timelock -->|管理权限| StrategicSwap
    
    Treasure -->|资金保管| EXTH
    Treasure -->|资金保管| USDT
    
    Airdrop -->|分发代币| EXTH
    StrategicSwap -->|原子交换| EXTH
    StrategicSwap -->|原子交换| USDT
    
    style Exchange fill:#e1f5ff
    style Dao fill:#fff4e1
    style Timelock fill:#ffe1e1
    style MultiSig fill:#e1ffe1
    style Treasure fill:#f0e1ff


数据流向

用户发起交易 → 前端调用后端 API → 后端创建交易请求并保存到数据库 → 等待匹配

交易匹配成功 → 匹配引擎自动撮合 → 发送 MQ 消息通知双方 → 用户执行转账操作 → 监听链上确认事件

争议处理 → 用户发起争议 → 创建多签仲裁提案 → 委员会成员投票 → 达到阈值后自动执行 → DAO 提案执行赔偿/驳回

DAO 治理 → 用户创建提案 → 社区投票 → 投票通过后进入公示期 → Timelock 延时执行 → 自动同步链上状态到数据库

空投领取 → 部署 Merkle 空投合约 → 用户提交证明领取 → 监听链上领取事件 → 更新数据库白名单状态 → 发送通知

事件同步机制 → GraphQL 订阅链上事件 → 定时任务轮询同步 → 发送到 RabbitMQ → 监听器处理业务逻辑 → Redis 防重 → 更新数据库

🔧 核心功能

1. 交易模块
交易请求 — 用户发布买卖需求,设置交易金额 (1–70 UT)
智能匹配 — 后端匹配交易对手,考虑用户等级和新手限制
分步确认 — PartyA 先转账 → PartyB 确认后转账 → PartyA 最终确认
手续费 — 交易完成后收取万分之一 EXTH 手续费
黑名单机制 — 违规用户永久禁止交易

2. 用户体系
👶 新用户 (NEW) — 初始状态,强制作为率先转账方 3 次,培养信用
👤 普通用户 (NORMAL) — 完成 3 次交易后升级,额度 = min(EXTH×10, 70UT)
🌟 种子用户 (SEED) — 持有 ≥ 900 EXTH

3. DAO 治理
提案机制 — 任何 EXTH 持有者可发起治理提案
投票权重 — 1 EXTH = 1 票,快照机制防止操纵
时间锁 — 通过的提案需等待 24 小时才能执行
多签钱包 — 3 人委员会,2/3 签名即可执行仲裁操作

4. 空投系统
基于 Merkle Tree 证明的空投分发
用户可查询地址是否在白名单
支持未领取代币回收机制

🚀 快速开始
环境要求
Node.js ≥ 18.x
Java 17
Maven ≥ 3.8
MySQL ≥ 8.0
Redis ≥ 6.0
RabbitMQ ≥ 3.10
MetaMask 浏览器插件

1. 克隆项目
git clone https://github.com/lhf3052856539/exchange-system.git
cd exchange-system

2. 智能合约部署
cd exc-contracts

# 安装依赖
npm install

# 配置环境变量
cp .env.example .env
# 编辑 .env,填入 PRIVATE_KEY 和 ETHERSCAN_API_KEY

# 编译合约
npx hardhat compile

# 运行测试
npx hardhat test

# 部署到 Sepolia 测试网
npx hardhat run scripts/deploy.js --network sepolia
部署成功后会输出所有合约地址,请记录这些地址用于后续配置。

3. 数据库初始化
# 创建数据库
mysql -u root -p -e "CREATE DATABASE exchange_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入初始化脚本(需要先创建 docs/sql/init.sql 文件)
# 或者手动执行以下 SQL:
# - 创建用户表、交易表、提案表等核心业务表
# - 插入初始数据(如代币信息、系统配置等)

4. 后端启动
cd exc-backend

# 修改配置文件 apis/src/main/resources/application.yml
# 需要配置以下内容:
# - spring.datasource: 数据库连接信息
# - spring.redis: Redis 连接信息
# - spring.rabbitmq: RabbitMQ 连接信息
# - web3j.node-url: Alchemy 或 Infura 节点 URL
# - web3j.private-key: 部署者私钥(用于后端与合约交互)
# - contract.*: 各合约地址(从步骤 2 获取)

# Maven 构建
mvn clean install -DskipTests

# 启动服务
java -jar apis/target/apis-1.0.0.jar

# 或使用 Maven 直接运行
mvn spring-boot:run -pl apis
后端访问地址:http://localhost:8093/apis

关键配置说明
application.yml 主要配置项:
# 服务器端口
server:
  port: 8093

# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/exchange_db?...
    username: root
    password: your_password

# Redis 配置
  redis:
    host: 127.0.0.1
    port: 6379
    password: your_redis_password

# RabbitMQ 配置
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest

# Web3j 区块链配置
web3j:
  node-url: https://eth-sepolia.g.alchemy.com/v2/YOUR_API_KEY
  network-id: 11155111  # Sepolia 测试网 ID
  private-key: YOUR_DEPLOYER_PRIVATE_KEY

# 合约地址配置
contract:
  exchange:
    address: '0x...'
  exth:
    address: '0x...'
  # ... 其他合约地址

  5. 前端启动
  cd exchange-frontend

# 安装依赖
npm install

# 配置环境变量
# 开发环境:.env.development
# 生产环境:.env.production

# 开发模式
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview
前端访问地址:http://localhost:3000

前端环境变量配置
.env.development 示例:
# API 基础路径
VITE_API_BASE_URL=http://localhost:8093/apis

# 区块链配置
VITE_CHAIN_ID=11155111
VITE_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/YOUR_API_KEY
VITE_EXPLORER_URL=https://sepolia.etherscan.io

# 合约地址(与后端配置保持一致)
VITE_CONTRACT_EXCHANGE=0x...
VITE_CONTRACT_EXTH=0x...
VITE_CONTRACT_USDT=0x...
VITE_CONTRACT_AIRDROP=0x...
📜 智能合约部署
Sepolia 测试网地址
⚠️ 注意: 以下为示例地址,实际部署后请使用你自己的合约地址

EXTH (治理代币) — 0xD74347dE964F59f40d55761e9e1e82aBB57F7533
USDT (模拟代币) — 0xaCc2AAC99533e857fBC7091821E74465cE019806
GBP (英镑模拟) — 0x...
RNB (人民币模拟) — 0x...
Exchange (交易) — 0xD29A830C4CfC0D9fF017Fbb14B572a9a2F9b2930
DAO (治理) — 0xB5168879C3A8b71DD4645C22465D5f9106df433d
Timelock (时间锁) — 0xFd2F6B0467adb7a140c7a6ff0cB701EA3dA39e87
MultiSigWallet (多签) — 0x349EFC3c2f59dAdBaDbd5Fb011Ea92E701Fe3582
Treasure (金库) — 0x42D15000E8A7dCBA6a9416960864F3dD46Be2fE9
Airdrop (空投) — 0x0894c5c63D6239241945e502b86F82ab5a4E3A52
部署者地址:0xa6aB9fC9DD0f85B3659F8DE18b07989d3c7C238e


📁 项目结构
exchange-system/
├── exc-backend/                # 后端服务 (Spring Boot)
│   ├── apis/                   # API 接口层(Controller + 启动类)
│   │   ├── src/main/java/      # Controller、配置类
│   │   ├── src/main/resources/ # application.yml 配置文件
│   │   └── pom.xml
│   ├── core/                   # 业务逻辑层(Service + Manager)
│   │   ├── src/main/java/      # Service 实现、业务逻辑
│   │   └── pom.xml
│   ├── common/                 # 通用工具类
│   │   ├── src/main/java/      # 工具类、常量、异常定义
│   │   └── pom.xml
│   ├── domain/                 # 领域模型(Entity / DTO / VO)
│   │   ├── src/main/java/      # 实体类、数据传输对象
│   │   └── pom.xml
│   ├── blockchain/             # 区块链交互模块(web3j)
│   │   ├── src/main/java/      # 合约封装、事件监听
│   │   ├── src/main/solidity/  # Solidity 源码备份
│   │   └── pom.xml
│   └── pom.xml                 # 父 POM,统一管理依赖版本
│
├── exc-contracts/              # 智能合约 (Hardhat)
│   ├── contracts/              # Solidity 源码
│   │   ├── Exchange.sol        # 交易合约
│   │   ├── Dao.sol             # 治理合约
│   │   ├── Timelock.sol        # 时间锁合约
│   │   ├── MultiSigWallet.sol  # 多签钱包
│   │   ├── Treasure.sol        # 金库合约
│   │   ├── Airdrop.sol         # 空投合约
│   │   ├── EXTH.sol            # 治理代币
│   │   ├── USDT.sol            # USDT 模拟代币
│   │   ├── GBP.sol             # GBP 模拟代币
│   │   ├── RNB.sol             # RNB 模拟代币
│   │   └── StrategicSwap.sol   # 战略投资交换
│   ├── scripts/                # 部署脚本
│   │   ├── deploy.js           # 主部署脚本
│   │   ├── generate-merkle.js  # 生成 Merkle Tree
│   │   └── ...                 # 其他辅助脚本
│   ├                
│   ├── hardhat.config.js       # Hardhat 配置
│   └── package.json
│
├── exchange-frontend/          # 前端应用 (Vue 3 + Vite)
│   ├── src/
│   │   ├── api/                # API 封装(Axios)
│   │   │   ├── trade.js        # 交易相关 API
│   │   │   ├── dao.js          # DAO 治理 API
│   │   │   ├── airdrop.js      # 空投 API
│   │   │   └── user.js         # 用户 API
│   │   ├── assets/             # 静态资源
│   │   │   └── styles/         # 全局样式
│   │   ├── components/         # 通用组件
│   │   │   ├── common/         # 通用组件
│   │   │   ├── dao/            # DAO 相关组件
│   │   │   └── wallet/         # 钱包相关组件
│   │   ├── composables/        # Composition API
│   │   │   ├── useWeb3.js      # Web3 交互
│   │   │   ├── useTrade.js     # 交易逻辑
│   │   │   ├── useDao.js       # DAO 逻辑
│   │   │   └── useAirdrop.js   # 空投逻辑
│   │   ├── config/             # 配置文件
│   │   │   ├── constants.js    # 常量定义
│   │   │   └── web3Config.js   # Web3 配置
│   │   ├── layouts/            # 布局组件
│   │   ├── router/             # 路由配置
│   │   ├── stores/             # Pinia 状态管理
│   │   │   ├── modules/        # 模块化 Store
│   │   │   └── index.js        # Store 入口
│   │   ├── utils/              # 工具函数
│   │   │   ├── web3.js         # Web3 工具
│   │   │   ├── request.js      # HTTP 请求封装
│   │   │   ├── auth.js         # 认证工具
│   │   │   └── merkleTree.js   # Merkle Tree 工具
│   │   ├── views/              # 页面组件
│   │   │   ├── home/           # 首页
│   │   │   ├── trade/          # 交易页面
│   │   │   ├── dao/            # DAO 治理页面
│   │   │   ├── airdrop/        # 空投页面
│   │   │   ├── auth/           # 认证页面
│   │   │   └── ...             # 其他页面
│   │   ├── App.vue             # 根组件
│   │   └── main.js             # 入口文件
│   ├── .env.development        # 开发环境变量
│   ├── .env.production         # 生产环境变量
│   ├── vite.config.js          # Vite 配置
│   └── package.json
│
├── docs/                       # 项目文档(待创建)
│   └── sql/                    # 数据库初始化脚本(待创建)
├── .gitignore                  # Git 忽略配置
└── README.md                   # 项目说明文档

🔌 核心 API 接口

认证相关
POST   /apis/auth/register       # 用户注册
POST   /apis/auth/login          # 用户登录
GET    /apis/auth/info           # 获取用户信息
POST   /apis/auth/logout         # 退出登录

交易相关
POST   /apis/trade/request-match     # 请求交易匹配
POST   /apis/trade/create-pair       # 创建交易对
POST   /apis/trade/confirm-party-a   # PartyA 确认转账
POST   /apis/trade/confirm-party-b   # PartyB 确认转账
POST   /apis/trade/final-confirm     # 最终确认
POST   /apis/trade/dispute           # 发起争议
GET    /apis/trade/list              # 获取交易列表
GET    /apis/trade/detail/:id        # 获取交易详情

DAO 治理
POST   /apis/dao/proposal            # 创建提案
POST   /apis/dao/vote                # 投票
GET    /apis/dao/proposals           # 提案列表
GET    /apis/dao/proposal/:id        # 提案详情
GET    /apis/dao/my-votes            # 我的投票记录

空投
GET    /apis/airdrop/check           # 检查空投资格
POST   /apis/airdrop/claim           # 领取空投
GET    /apis/airdrop/status          # 空投状态查询

用户相关
GET    /apis/user/profile            # 获取个人资料
PUT    /apis/user/profile            # 更新个人资料
GET    /apis/user/statistics         # 用户统计数据

通知相关
GET    /apis/notification/list       # 获取通知列表
PUT    /apis/notification/read/:id   # 标记已读
PUT    /apis/notification/read-all   # 全部标记已读

🛠 技术栈

后端
Spring Boot 2.7.0 — Web 框架
MyBatis-Plus 3.5.2 — ORM 框架
Redisson 3.17.5 — Redis 客户端 / 分布式锁
RabbitMQ 2.4.6 — 消息队列
web3j 4.10.0 — 以太坊交互
JWT 0.11.5 — 身份认证
Druid 1.2.8 — 数据库连接池
Lombok — 简化 Java 代码

前端
Vue 3 3.4.0 — 渐进式框架
Vite 5.0.8 — 构建工具
ethers.js 6.9.2 — Web3 库
Element Plus 2.4.4 — UI 组件库
Pinia 2.1.7 — 状态管理
Vue Router 4.x — 路由管理
Tailwind CSS 3.x — 原子化 CSS
Axios 1.x — HTTP 客户端

智能合约
Solidity ^0.8.21 — 合约语言
Hardhat 2.28.6 — 开发环境
OpenZeppelin 5.1.0 — 安全合约库

基础设施
MySQL 8.0+ — 关系型数据库
Redis 6.0+ — 缓存 / 会话存储
RabbitMQ 3.10+ — 消息队列
Alchemy/Infura — 以太坊节点服务

⚠️ 安全说明
⚠️ 重要提示:本项目仅供学习参考,请勿用于真实资金交易!

测试网部署 — 本项目使用 Sepolia 测试网部署,切勿在主网使用示例私钥
敏感信息保护 — .env 文件中的敏感信息请勿提交到版本控制,实际部署时请更换所有密钥
合约审计 — 智能合约 未经过专业安全审计,可能存在潜在漏洞
私钥管理 — 生产环境应使用硬件钱包或专业的密钥管理服务(如 AWS KMS、HashiCorp Vault)
权限控制 — 确保多签钱包的签名者地址安全可靠,定期轮换密钥
输入验证 — 前后端都应进行严格的输入验证,防止注入攻击
速率限制 — 生产环境应启用 API 速率限制,防止 DDoS 攻击

最佳实践
✅ 使用环境变量管理敏感配置
✅ 启用 HTTPS 加密传输
✅ 定期更新依赖包版本
✅ 实施日志监控和告警机制
✅ 进行压力测试和安全扫描
❌ 不要在代码中硬编码私钥或 API Key
❌ 不要将 .env 文件提交到 Git
❌ 不要在生产环境使用默认密码
🤝 贡献指南
欢迎提交 Issue 和 Pull Request!

Fork 本仓库
创建特性分支 (git checkout -b feature/AmazingFeature)
提交更改 (git commit -m 'Add some AmazingFeature')
推送到分支 (git push origin feature/AmazingFeature)
开启 Pull Request
📞 联系方式
项目 Issues: GitHub Issues
Email: 3052856539@qq.com
📄 License
本项目基于 MIT License 开源。

Copyright © 2026 Exchange System Team

🙏 致谢
感谢以下开源项目:

OpenZeppelin Contracts
Hardhat
Spring Boot
Vue.js
Ethers.js
Made with ❤️ by the Exchange System Team

