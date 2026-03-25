const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("GBP Token Contract", function () {
    let GBP;
    let gbp;
    let owner;
    let addr1;

    const TOTAL_SUPPLY = ethers.parseUnits("10000", 6);

    beforeEach(async function () {
        [owner, addr1] = await ethers.getSigners();

        GBP = await ethers.getContractFactory("GBP");
        gbp = await GBP.deploy();
        await gbp.waitForDeployment();
    });

    it("应该正确设置名称和符号", async function () {
        expect(await gbp.name()).to.equal("GBP");
        expect(await gbp.symbol()).to.equal("GBP");
    });

    it("应该正确设置精度为6", async function () {
        expect(await gbp.decimals()).to.equal(6);
    });

    it("应该将总供应量铸造给部署者", async function () {
        expect(await gbp.balanceOf(owner.address)).to.equal(TOTAL_SUPPLY);
    });

    it("应该能转账", async function () {
        const amount = ethers.parseUnits("100", 6);
        await gbp.transfer(addr1.address, amount);
        expect(await gbp.balanceOf(addr1.address)).to.equal(amount);
    });
});

describe("RNB Token Contract", function () {
    let RNB;
    let rnb;
    let owner;
    let addr1;

    const TOTAL_SUPPLY = ethers.parseUnits("10000", 6);

    beforeEach(async function () {
        [owner, addr1] = await ethers.getSigners();

        RNB = await ethers.getContractFactory("RNB");
        rnb = await RNB.deploy();
        await rnb.waitForDeployment();
    });

    it("应该正确设置名称和符号", async function () {
        expect(await rnb.name()).to.equal("RNB");
        expect(await rnb.symbol()).to.equal("RNB");
    });

    it("应该正确设置精度为6", async function () {
        expect(await rnb.decimals()).to.equal(6);
    });

    it("应该将总供应量铸造给部署者", async function () {
        expect(await rnb.balanceOf(owner.address)).to.equal(TOTAL_SUPPLY);
    });

    it("应该能转账", async function () {
        const amount = ethers.parseUnits("100", 6);
        await rnb.transfer(addr1.address, amount);
        expect(await rnb.balanceOf(addr1.address)).to.equal(amount);
    });
});