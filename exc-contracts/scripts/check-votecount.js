const hre = require("hardhat");
const fs = require('fs');

async function main() {
    const addresses = JSON.parse(fs.readFileSync('contract-addresses.json', 'utf8'));

    const MultiSig = await ethers.getContractFactory("MultiSigWallet");
    const multiSig = MultiSig.attach(addresses.core.MultiSigWallet);

    const count = await multiSig.proposalCount();
    console.log('总提案数:', count.toString());
    console.log('========================================\n');

    for (let i = 0; i < count; i++) {
        const proposal = await multiSig.proposals(i);
        console.log(`提案 ${i}:`);
        console.log('  voteCount:', proposal.voteCount.toString());
        console.log('  executed:', proposal.executed);
        console.log('  rejected:', proposal.rejected);
        console.log('  accusedParty:', proposal.accusedParty);
        console.log('  victimParty:', proposal.victimParty);
        console.log('----------------------------------------');
    }
}

main()
    .then(() => process.exit(0))
    .catch((error) => {
        console.error(error);
        process.exit(1);
    });
