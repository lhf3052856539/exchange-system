// src/api/airdrop.js
import request from '@/utils/request'
import web3 from '@/utils/web3'
import { ethers } from 'ethers'

const AIRDROP_CONTRACT_ADDRESS = import.meta.env.VITE_AIRDROP_CONTRACT_ADDRESS
const AIRDROP_ABI = [
    "function claim(uint256 amount, bytes32[] calldata merkleProof) external returns ()",
    "function hasClaimed(address user) external view returns (bool)",
    "function isClaimed(address user) external view returns (bool)"
]

/**
 * 领取空投（直接调用智能合约）
 * @param {string} address - 钱包地址
 * @param {string|number} amount - 领取数量
 * @param {string[]} merkleProof - 默克尔证明（十六进制字符串数组）
 */
export async function claimAirdrop(address, amount, merkleProof) {
    try {
        console.log(' Claiming airdrop directly on chain:', {
            address,
            amount,
            merkleProof
        })

        if (!web3.signer) {
            await web3.connect()
        }

        const airdropContract = new ethers.Contract(
            AIRDROP_CONTRACT_ADDRESS,
            AIRDROP_ABI,
            web3.signer
        )

        const txResponse = await airdropContract.claim(amount, merkleProof)
        console.log('Transaction sent:', txResponse.hash)

        const receipt = await txResponse.wait()
        console.log('Transaction confirmed:', receipt.transactionHash)

        return {
            success: true,
            txHash: receipt.transactionHash
        }
    } catch (error) {
        console.error(' Failed to claim airdrop:', error)
        throw error
    }
}


/**
 * 检查是否已领取空投（直接调用链上合约）
 * @param {string} address - 钱包地址
 */
export async function hasClaimed(address) {
    try {
        if (!web3.provider) {
            await web3.connect()
        }

        const airdropContract = new ethers.Contract(
            AIRDROP_CONTRACT_ADDRESS,
            AIRDROP_ABI,
            web3.provider
        )

        const claimed = await airdropContract.isClaimed(address)
        return {
            success: true,
            data: claimed
        }
    } catch (error) {
        console.error('Failed to check claimed status:', error)
        throw error
    }
}

/**
 * 获取空投信息
 * @param {string} address - 钱包地址
 */
export function getAirdropInfo(address) {
    return request({
        url: '/airdrop/claim-info',
        method: 'get',
        params: { address }
    })
}
