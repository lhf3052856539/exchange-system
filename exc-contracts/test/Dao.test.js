const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("DAO", function () {
    let EXTH, exth, USDT, usdt, AirdropToken, airdropToken, Dao, dao, owner, addr1, addr2;
    const TIMELOCK_DELAY = 3600;
    const FIXED_AMOUNT = 100;
    const VOTING_PERIOD = 3 * 24 * 3600; // 3天

    // 提案状态枚举
    const ProposalState = {
        Pending: 0,
        Active: 1,
        Succeeded: 2,
        Failed: 3,
        Queued: 4,
        Executed: 5,
        Cancelled: 6
    };

    beforeEach(async function () {
        [owner, addr1, addr2] = await ethers.getSigners();

        EXTH = await ethers.getContractFactory("EXTH");
        exth = await EXTH.deploy();
        await exth.waitForDeployment();

        USDT = await ethers.getContractFactory("USDT");
        usdt = await USDT.deploy();
        await usdt.waitForDeployment();

        // 部署空投代币（复用EXTH作为示例）
        airdropToken = await EXTH.deploy();
        await airdropToken.waitForDeployment();

        Dao = await ethers.getContractFactory("Dao");
        dao = await Dao.deploy(
            TIMELOCK_DELAY,
            await exth.getAddress(),
            await usdt.getAddress(),
            await airdropToken.getAddress(),
            FIXED_AMOUNT
        );
        await dao.waitForDeployment();
    });

    describe("部署", function () {
        it("应该部署所有子合约", async function () {
            expect(await dao.timelock()).to.not.equal(ethers.ZeroAddress);
            expect(await dao.exth()).to.equal(await exth.getAddress());
            expect(await dao.exchange()).to.not.equal(ethers.ZeroAddress);
            expect(await dao.airdrop()).to.not.equal(ethers.ZeroAddress);
            expect(await dao.treasure()).to.not.equal(ethers.ZeroAddress);
        });

        it("应该设置正确的投票周期", async function () {
            expect(await dao.votingPeriod()).to.equal(VOTING_PERIOD);
        });
    });

    describe("创建提案", function () {
        it("应该能够创建提案", async function () {
            const description = "测试提案";
            const target = await dao.getAddress();
            const value = 0;
            const data = "0x";

            await dao.createProposal(description, target, value, data);

            const proposal = await dao.proposals(0);
            expect(proposal.description).to.equal(description);
            expect(proposal.targetContract).to.equal(target);
            expect(proposal.deadline).to.be.gt(0);
        });
    });

    describe("投票", function () {
        let proposalId;

        beforeEach(async function () {
            // 先给 addr1 一些 EXTH 用于投票
            await exth.transfer(addr1.address, 1000 * 10**6);

            // ERC20Votes 需要委托才能记录投票权
            await exth.connect(addr1).delegate(addr1.address);

            // 等待一个区块，让委托生效
            await ethers.provider.send("evm_mine");

            // 创建提案（快照会记录当前区块的投票权）
            await dao.createProposal("测试提案", await dao.getAddress(), 0, "0x");
            proposalId = 0;
        });

        it("持有 EXTH 的用户应该能够投票", async function () {
            await dao.connect(addr1).vote(proposalId, true);

            const proposal = await dao.proposals(proposalId);
            expect(proposal.yesVotes).to.equal(1000 * 10**6);
        });

        it("不能重复投票", async function () {
            await dao.connect(addr1).vote(proposalId, true);
            await expect(dao.connect(addr1).vote(proposalId, true))
                .to.be.revertedWith("已经投过票了！");
        });
        it("投票结束后不能投票", async function () {
            // 快进时间超过投票期
            await ethers.provider.send("evm_increaseTime", [VOTING_PERIOD + 1000]);
            await ethers.provider.send("evm_mine");

            await expect(dao.connect(addr1).vote(proposalId, true))
                .to.be.revertedWith("投票时间已过！");
        });
    });

    describe("提案状态", function () {
        let proposalId;

        beforeEach(async function () {
            // 先给 addr1 一些 EXTH 并委托
            await exth.transfer(addr1.address, 1000 * 10**6);
            await exth.connect(addr1).delegate(addr1.address);
            await ethers.provider.send("evm_mine");

            await dao.createProposal("测试提案", await dao.getAddress(), 0, "0x");
            proposalId = 0;
        });

        it("应该返回正确的提案状态", async function () {
            // 初始状态应该是 Active
            let state = await dao.getProposalState(proposalId);
            expect(state).to.equal(ProposalState.Active);

            // 快进时间到投票结束
            await ethers.provider.send("evm_increaseTime", [VOTING_PERIOD + 100]);
            await ethers.provider.send("evm_mine");

            // 没有投票，应该失败
            state = await dao.getProposalState(proposalId);
            expect(state).to.equal(ProposalState.Failed);
        });

        it("投票通过后状态应为 Succeeded", async function () {
            // addr1 已经有 EXTH 并委托，直接创建新提案
            await dao.createProposal("测试提案 2", await dao.getAddress(), 0, "0x");
            const newProposalId = 1;

            // addr1 在提案创建时已经持有 EXTH 并委托，可以投票
            await dao.connect(addr1).vote(newProposalId, true);

            await ethers.provider.send("evm_increaseTime", [VOTING_PERIOD + 100]);
            await ethers.provider.send("evm_mine");

            const state = await dao.getProposalState(newProposalId);
            expect(state).to.equal(ProposalState.Succeeded);
        });
    });

    describe("公示期", function () {
        let proposalId;

        beforeEach(async function () {
            // 先给 addr1 一些 EXTH 并委托
            await exth.transfer(addr1.address, 1000 * 10**6);
            await exth.connect(addr1).delegate(addr1.address);
            await ethers.provider.send("evm_mine");

            // 创建提案
            await dao.createProposal("测试提案", await dao.getAddress(), 0, "0x");
            proposalId = 0;

            // 投票
            await dao.connect(addr1).vote(proposalId, true);

            // 快进时间到投票结束
            await ethers.provider.send("evm_increaseTime", [VOTING_PERIOD + 100]);
            await ethers.provider.send("evm_mine");
        });

        it("应该能够将提案加入公示期", async function () {
            await dao.queueProposal(proposalId);

            const proposal = await dao.proposals(proposalId);
            expect(proposal.queued).to.be.true;

            const state = await dao.getProposalState(proposalId);
            expect(state).to.equal(ProposalState.Queued);
        });
    });
});