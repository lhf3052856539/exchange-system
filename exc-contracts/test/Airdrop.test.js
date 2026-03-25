const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("Airdrop", function () {
    let EXTH, exth, Airdrop, airdrop, owner, addr1, addr2, addr3;
    const FIXED_AMOUNT = 100;

    beforeEach(async function () {
        [owner, addr1, addr2, addr3] = await ethers.getSigners();

        EXTH = await ethers.getContractFactory("EXTH");
        exth = await EXTH.deploy();
        await exth.waitForDeployment();

        Airdrop = await ethers.getContractFactory("Airdrop");
        airdrop = await Airdrop.deploy(await exth.getAddress(), FIXED_AMOUNT);
        await airdrop.waitForDeployment();
    });

    describe("部署", function () {
        it("应该设置正确的代币地址和固定金额", async function () {
            expect(await airdrop.token()).to.equal(await exth.getAddress());
            expect(await airdrop.fixedAmount()).to.equal(FIXED_AMOUNT);
        });
    });

    describe("空投资金", function () {
        it("应该能够存入空投代币", async function () {
            await exth.approve(await airdrop.getAddress(), 1000);
            await airdrop.fundAirdrop(1000);

            expect(await airdrop.totalAirdropAmount()).to.equal(1000);
            expect(await exth.balanceOf(await airdrop.getAddress())).to.equal(1000);
        });
    });

    describe("领取空投", function () {
        beforeEach(async function () {
            // 存入 250，刚好够两个人领取，第三个人不够
            await exth.approve(await airdrop.getAddress(), 250);
            await airdrop.fundAirdrop(250);
        });

        it("应该能够领取空投", async function () {
            await airdrop.connect(addr1).claimAirdrop();

            expect(await airdrop.hasClaimed(addr1.address)).to.be.true;
            expect(await airdrop.claimedAmount()).to.equal(FIXED_AMOUNT);
            expect(await exth.balanceOf(addr1.address)).to.equal(FIXED_AMOUNT);
        });

        it("不能重复领取", async function () {
            await airdrop.connect(addr1).claimAirdrop();
            await expect(airdrop.connect(addr1).claimAirdrop()).to.be.revertedWith("已领取！");
        });

        it("总空投数量不足时不能领取", async function () {
            await airdrop.connect(addr1).claimAirdrop(); // 用掉100
            await airdrop.connect(addr2).claimAirdrop(); // 用掉100，还剩50

            // 第三次领取应该失败（总空投数量不足，因为 claimedAmount + fixedAmount = 250 > totalAirdropAmount）
            await expect(airdrop.connect(addr3).claimAirdrop()).to.be.revertedWith("总空投数量不足！");
        });
    });

    describe("提取剩余代币", function () {
        it("应该能够提取剩余代币", async function () {
            await exth.approve(await airdrop.getAddress(), 1000);
            await airdrop.fundAirdrop(1000);

            // 先领取一部分
            await airdrop.connect(addr1).claimAirdrop();

            // 提取剩余
            await airdrop.withdrawRemaining();

            // 验证余额为0
            expect(await exth.balanceOf(await airdrop.getAddress())).to.equal(0);
        });
    });
});