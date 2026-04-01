// src/utils/merkleTree.js - 使用 merkle-output.json 的数据
import merkleOutput from '../../merkle-output.json'

const MERKLE_ROOT = merkleOutput.merkleRoot
const CLAIMS = merkleOutput.claims

/**
 * 生成用户的 Merkle Proof
 */
export function generateMerkleProof(userAddress, _whitelistData) {
    // 忽略传入的 whitelistData，直接使用 merkle-output.json 中的数据
    const normalizedAddress = userAddress.toLowerCase()

    // 在 claims 中查找用户（不区分大小写）
    const claimEntry = Object.entries(CLAIMS).find(
        ([addr]) => addr.toLowerCase() === normalizedAddress
    )

    if (!claimEntry) {
        throw new Error('Address not in whitelist')
    }

    const [address, claimData] = claimEntry

    console.log('🌳 Using Merkle Proof from merkle-output.json:', {
        root: MERKLE_ROOT,
        originalAddress: address,
        requestedAddress: userAddress,
        amount: claimData.amount,
        proofLength: claimData.proof.length,
        proof: claimData.proof
    })

    return {
        amount: claimData.amount,
        proof: claimData.proof
    }
}

/**
 * 获取 Merkle Root
 */
export function getMerkleRoot() {
    return MERKLE_ROOT
}
