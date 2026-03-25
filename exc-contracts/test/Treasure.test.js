const { expect } = require("chai");
const { ethers } = require("hardhat");

// 创建一个简单的测试目标合约
const TestTargetFactory = {
    bytecode: "0x608060405234801561001057600080fd5b5061019f806100206000396000f3fe608060405234801561001057600080fd5b506004361061002b5760003560e01c8063a9059cbb14610030575b600080fd5b61004a600480360381019061004591906100f0565b610060565b604051610057919061014b565b60405180910390f35b60008173ffffffffffffffffffffffffffffffffffffffff1663a9059cbb846040518263ffffffff1660e01b815260040161009b9190610176565b6020604051808303816000875af11580156100ba573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906100de9190610125565b905092915050565b6000813590506100f581610152565b92915050565b600080604083850312156101075761010661014d565b5b6000610115858286016100e6565b9250506020610126858286016100fb565b9150509250929050565b60006020828403121561013b5761013a61014d565b5b6000610149848285016100fb565b91505092915050565b60008115159050919050565b6000819050919050565b61016d8161015e565b82525050565b600060208201905061018b6000830184610164565b92915050565b600080fd5b600080fd5b600080fd5b61015b8161015e565b811461016c57600080fd5b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fdfea2646970667358221220b5a3c3a5e1b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e5b5e64736f6c63430008120033",
    abi: [
        {
            inputs: [
                { internalType: "address", name: "token", type: "address" },
                { internalType: "uint256", name: "amount", type: "uint256" }
            ],
            name: "transferToken",
            outputs: [{ internalType: "bool", name: "", type: "bool" }],
            stateMutability: "nonpayable",
            type: "function"
        }
    ]
};

describe("Treasure", function () {
    let Treasure, treasure, EXTH, exth, owner, addr1;
    let TestTarget, testTarget;

    beforeEach(async function () {
        [owner, addr1] = await ethers.getSigners();

        Treasure = await ethers.getContractFactory("Treasure");
        treasure = await Treasure.deploy();
        await treasure.waitForDeployment();

        EXTH = await ethers.getContractFactory("EXTH");
        exth = await EXTH.deploy();
        await exth.waitForDeployment();
    });

    describe("部署", function () {
        it("应该设置正确的所有者", async function () {
            expect(await treasure.owner()).to.equal(owner.address);
        });
    });

    describe("接收ETH", function () {
        it("应该能够接收ETH", async function () {
            await expect(owner.sendTransaction({
                to: await treasure.getAddress(),
                value: ethers.parseEther("1.0")
            })).to.emit(treasure, "Deposit");

            expect(await ethers.provider.getBalance(await treasure.getAddress()))
                .to.equal(ethers.parseEther("1.0"));
        });
    });

    describe("提取ETH", function () {
        beforeEach(async function () {
            await owner.sendTransaction({
                to: await treasure.getAddress(),
                value: ethers.parseEther("1.0")
            });
        });

        it("所有者应该能够提取ETH", async function () {
            const balanceBefore = await ethers.provider.getBalance(addr1.address);

            await treasure.withdrawETH(addr1.address, ethers.parseEther("0.5"));

            const balanceAfter = await ethers.provider.getBalance(addr1.address);
            expect(balanceAfter - balanceBefore).to.equal(ethers.parseEther("0.5"));
        });

        it("非所有者不能提取ETH", async function () {
            await expect(treasure.connect(addr1).withdrawETH(addr1.address, ethers.parseEther("0.5")))
                .to.be.revertedWithCustomError;
        });
    });

    describe("提取ERC20", function () {
        beforeEach(async function () {
            await exth.transfer(await treasure.getAddress(), 1000);
        });

        it("所有者应该能够提取ERC20", async function () {
            await treasure.withdrawERC20(await exth.getAddress(), addr1.address, 500);

            expect(await exth.balanceOf(addr1.address)).to.equal(500);
            expect(await exth.balanceOf(await treasure.getAddress())).to.equal(500);
        });
    });

    describe("执行调用", function () {
        it("应该能够执行外部调用 - 简单转账", async function () {
            // 给treasure一些代币
            await exth.transfer(await treasure.getAddress(), 1000);

            // 创建一个简单的调用：从treasure转账给addr1
            const data = exth.interface.encodeFunctionData("transfer", [addr1.address, 500]);

            await treasure.executeCall(await exth.getAddress(), 0, data);

            // 验证调用成功
            expect(await exth.balanceOf(addr1.address)).to.equal(500);
            expect(await exth.balanceOf(await treasure.getAddress())).to.equal(500);
        });

        it("不应该能够执行无效调用", async function () {
            // 无效的调用数据
            const data = "0x12345678";

            await expect(treasure.executeCall(await exth.getAddress(), 0, data))
                .to.be.revertedWith("Treasure: External call failed");
        });
    });

    describe("转移所有权", function () {
        it("应该能够转移所有权到DAO", async function () {
            await treasure.transferOwnership(addr1.address);
            expect(await treasure.owner()).to.equal(addr1.address);
        });

        it("不能转移到零地址", async function () {
            await expect(treasure.transferOwnership(ethers.ZeroAddress))
                .to.be.revertedWith("新所有者不能为零地址！");
        });
    });
});