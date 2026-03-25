const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("Timelock", function () {
    let Timelock, timelock, EXTH, exth, owner, addr1;
    const DELAY = 3600; // 1小时
    const GRACE_PERIOD = 14 * 24 * 3600; // 14天

    beforeEach(async function () {
        [owner, addr1] = await ethers.getSigners();

        Timelock = await ethers.getContractFactory("Timelock");
        timelock = await Timelock.deploy(DELAY);
        await timelock.waitForDeployment();

        EXTH = await ethers.getContractFactory("EXTH");
        exth = await EXTH.deploy();
        await exth.waitForDeployment();
    });

    describe("部署", function () {
        it("应该设置正确的延迟时间", async function () {
            expect(await timelock.delay()).to.equal(DELAY);
        });

        it("应该设置正确的宽限期", async function () {
            expect(await timelock.GRACE_PERIOD()).to.equal(GRACE_PERIOD);
        });
    });

    describe("设置延迟", function () {
        it("管理员应该能够修改延迟时间", async function () {
            await timelock.setDelay(7200);
            expect(await timelock.delay()).to.equal(7200);
        });

        it("非管理员不能修改延迟时间", async function () {
            await expect(timelock.connect(addr1).setDelay(7200))
                .to.be.revertedWithCustomError;
        });
    });

    describe("交易队列", function () {
        it("应该能够将交易加入队列", async function () {
            const target = await exth.getAddress();
            const value = 0;
            const data = "0x";
            const timestamp = (await ethers.provider.getBlock("latest")).timestamp + DELAY + 100;

            const tx = await timelock.queueTransaction(target, value, data, timestamp);
            const receipt = await tx.wait();

            const txId = await timelock.getTransactionId(target, value, data, timestamp);
            expect(await timelock.isQueued(txId)).to.be.true;
        });

        it("不能加入时间戳过早的交易", async function () {
            const target = await exth.getAddress();
            const value = 0;
            const data = "0x";
            const timestamp = (await ethers.provider.getBlock("latest")).timestamp;

            await expect(timelock.queueTransaction(target, value, data, timestamp))
                .to.be.revertedWith("时间戳过早！");
        });
    });

    describe("执行交易", function () {
        it("应该在延迟后执行交易", async function () {
            // 先给timelock一些EXTH用于转账
            await exth.transfer(await timelock.getAddress(), 1000);

            const target = await exth.getAddress();
            const value = 0;
            // 正确的调用数据：从timelock转账给addr1
            const data = exth.interface.encodeFunctionData("transfer", [addr1.address, 500]);
            const timestamp = (await ethers.provider.getBlock("latest")).timestamp + DELAY + 100;

            await timelock.queueTransaction(target, value, data, timestamp);

            // 快进时间
            await ethers.provider.send("evm_increaseTime", [DELAY + 200]);
            await ethers.provider.send("evm_mine");

            await timelock.executeTransaction(target, value, data, timestamp);

            const txId = await timelock.getTransactionId(target, value, data, timestamp);
            expect(await timelock.isQueued(txId)).to.be.false;

            // 验证转账成功
            expect(await exth.balanceOf(addr1.address)).to.equal(500);
        });

        it("不能过早执行交易", async function () {
            const target = await exth.getAddress();
            const value = 0;
            const data = "0x";
            const timestamp = (await ethers.provider.getBlock("latest")).timestamp + DELAY + 100;

            await timelock.queueTransaction(target, value, data, timestamp);

            await expect(timelock.executeTransaction(target, value, data, timestamp))
                .to.be.revertedWith("时间戳过早！");
        });
    });

    describe("取消交易", function () {
        it("应该能够取消队列中的交易", async function () {
            const target = await exth.getAddress();
            const value = 0;
            const data = "0x";
            const timestamp = (await ethers.provider.getBlock("latest")).timestamp + DELAY + 100;

            await timelock.queueTransaction(target, value, data, timestamp);

            const txId = await timelock.getTransactionId(target, value, data, timestamp);
            await timelock.cancelTransaction(target, value, data, timestamp);

            expect(await timelock.isQueued(txId)).to.be.false;
        });
    });
});