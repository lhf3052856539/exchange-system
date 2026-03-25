// test/USDT.test.js
// USDT 稳定币代币测试

const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("USDT 代币合约", function () {
    let usdt;
    let deployer, addr1, addr2;

    beforeEach(async function () {
        [deployer, addr1, addr2] = await ethers.getSigners();

        const USDT = await ethers.getContractFactory("USDT");
        usdt = await USDT.deploy();
        await usdt.waitForDeployment();
    });

    describe("部署测试", function () {
        it("应该正确设置代币名称和符号", async function () {
            expect(await usdt.name()).to.equal("USDT");
            expect(await usdt.symbol()).to.equal("USDT");
        });

        it("应该有正确的精度", async function () {
            expect(await usdt.decimals()).to.equal(6);
        });

        it("应该铸造正确的总供应量", async function () {
            const totalSupply = await usdt.totalSupply();
            expect(totalSupply).to.equal(ethers.parseUnits("100000000000", 6));
        });

        it("部署者应该拥有全部代币", async function () {
            const balance = await usdt.balanceOf(deployer.address);
            expect(balance).to.equal(await usdt.totalSupply());
        });
    });

    describe("ERC20 标准功能测试", function () {
        it("应该可以正常转账", async function () {
            const amount = ethers.parseUnits("1000", 6);
            await expect(usdt.transfer(addr1.address, amount))
                .to.emit(usdt, "Transfer")
                .withArgs(deployer.address, addr1.address, amount);

            expect(await usdt.balanceOf(addr1.address)).to.equal(amount);
        });

        it("应该可以授权和转账从", async function () {
            const amount = ethers.parseUnits("500", 6);

            await usdt.approve(addr1.address, amount);
            expect(await usdt.allowance(deployer.address, addr1.address))
                .to.equal(amount);

            await expect(
                usdt.connect(addr1).transferFrom(
                    deployer.address,
                    addr2.address,
                    amount
                )
            ).to.emit(usdt, "Transfer");

            expect(await usdt.balanceOf(addr2.address)).to.equal(amount);
        });

        it("不能转账超过余额", async function () {
            const totalSupply = await usdt.totalSupply();
            await expect(
                usdt.transfer(addr1.address, totalSupply + 1n)
            ).to.be.reverted;
        });

        it("不能转账到零地址", async function () {
            const amount = ethers.parseUnits("100", 6);
            await expect(
                usdt.transfer(ethers.ZeroAddress, amount)
            ).to.be.reverted;
        });
    });

    describe("多用户转账测试", function () {
        beforeEach(async function () {
            const amount = ethers.parseUnits("10000", 6);
            await usdt.transfer(addr1.address, amount);
            await usdt.transfer(addr2.address, amount);
        });

        it("addr1 可以转账给 addr2", async function () {
            const amount = ethers.parseUnits("100", 6);
            await usdt.connect(addr1).transfer(addr2.address, amount);

            expect(await usdt.balanceOf(addr1.address)).to.equal(
                ethers.parseUnits("9900", 6)
            );
            expect(await usdt.balanceOf(addr2.address)).to.equal(
                ethers.parseUnits("10100", 6)
            );
        });

        it("可以多次小额转账", async function () {
            const smallAmount = ethers.parseUnits("10", 6);

            for (let i = 0; i < 5; i++) {
                await usdt.connect(addr1).transfer(addr2.address, smallAmount);
            }

            expect(await usdt.balanceOf(addr1.address)).to.equal(
                ethers.parseUnits("9950", 6)
            );
        });
    });

    describe("事件测试", function () {
        it("转账应该触发 Transfer 事件", async function () {
            const amount = ethers.parseUnits("100", 6);
            await expect(usdt.transfer(addr1.address, amount))
                .to.emit(usdt, "Transfer")
                .withArgs(deployer.address, addr1.address, amount);
        });

        it("授权应该触发 Approval 事件", async function () {
            const amount = ethers.parseUnits("50", 6);
            await expect(usdt.approve(addr1.address, amount))
                .to.emit(usdt, "Approval")
                .withArgs(deployer.address, addr1.address, amount);
        });
    });
});
