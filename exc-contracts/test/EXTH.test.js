const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("EXTH Token", function () {
    let EXTH, exth, owner, addr1, addr2;

    beforeEach(async function () {
        [owner, addr1, addr2] = await ethers.getSigners();
        EXTH = await ethers.getContractFactory("EXTH");
        exth = await EXTH.deploy();
        await exth.waitForDeployment();
    });

    describe("部署", function () {
        it("应该设置正确的名称和符号", async function () {
            expect(await exth.name()).to.equal("EXTH");
            expect(await exth.symbol()).to.equal("EXTH");
        });

        it("应该设置正确的小数位数为6", async function () {
            expect(await exth.decimals()).to.equal(6);
        });

        it("应该将总供应量铸造给部署者", async function () {
            const totalSupply = await exth.totalSupply();
            expect(await exth.balanceOf(owner.address)).to.equal(totalSupply);
        });
    });

    describe("转账", function () {
        it("应该能够转账代币", async function () {
            await exth.transfer(addr1.address, 1000);
            expect(await exth.balanceOf(addr1.address)).to.equal(1000);
        });

        it("应该能够授权和转账", async function () {
            await exth.approve(addr1.address, 500);
            await exth.connect(addr1).transferFrom(owner.address, addr2.address, 500);
            expect(await exth.balanceOf(addr2.address)).to.equal(500);
        });
    });

    describe("投票功能", function () {
        it("应该记录投票权", async function () {
            // 转账给 addr1
            await exth.transfer(addr1.address, 1000 * 10**6);

            // ERC20Votes 需要委托才能记录投票权
            await exth.connect(addr1).delegate(addr1.address);

            // 等待一个区块，确保委托已确认
            await ethers.provider.send("evm_mine");

            // 获取当前区块
            const currentBlock = await ethers.provider.getBlockNumber();

            // 查询投票权（应该在委托后有值）
            const votingPower = await exth.getPastVotes(addr1.address, currentBlock - 1);
            expect(votingPower).to.equal(1000 * 10**6);
        });
    });
});