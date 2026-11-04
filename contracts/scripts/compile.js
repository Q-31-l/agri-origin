/**
 * Traceability 合约编译脚本
 * 输出：../abi/Traceability.abi、../bin/Traceability.bin
 * 使用：npm install && npm run compile
 */
const fs = require('fs');
const path = require('path');
const solc = require('solc');

const CONTRACT_DIR = path.resolve(__dirname, '../contracts');
const ABI_DIR = path.resolve(__dirname, '../abi');
const BIN_DIR = path.resolve(__dirname, '../bin');
const TARGET = 'Traceability.sol';

function readSources() {
  const sources = {};
  fs.readdirSync(CONTRACT_DIR)
    .filter((f) => f.endsWith('.sol'))
    .forEach((f) => {
      sources[f] = { content: fs.readFileSync(path.join(CONTRACT_DIR, f), 'utf8') };
    });
  return sources;
}

const input = {
  language: 'Solidity',
  sources: readSources(),
  settings: {
    optimizer: { enabled: true, runs: 200 },
    evmVersion: 'istanbul',
    outputSelection: {
      '*': { '*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object'] }
    }
  }
};

console.log('> compiling with solc ' + solc.version());
const output = JSON.parse(solc.compile(JSON.stringify(input), { import: (p) => {
  const file = path.join(CONTRACT_DIR, path.basename(p));
  return fs.existsSync(file) ? { contents: fs.readFileSync(file, 'utf8') } : { error: 'not found: ' + p };
}}));

if (output.errors) {
  let fatal = false;
  output.errors.forEach((e) => {
    console.log((e.severity === 'error' ? '[ERROR] ' : '[WARN ] ') + e.formattedMessage);
    if (e.severity === 'error') fatal = true;
  });
  if (fatal) process.exit(1);
}

const contract = output.contracts[TARGET].Traceability;
if (!contract) {
  console.error('Traceability contract not found');
  process.exit(1);
}

fs.mkdirSync(ABI_DIR, { recursive: true });
fs.mkdirSync(BIN_DIR, { recursive: true });
fs.writeFileSync(path.join(ABI_DIR, 'Traceability.abi'), JSON.stringify(contract.abi, null, 2));
fs.writeFileSync(path.join(BIN_DIR, 'Traceability.bin'), contract.evm.bytecode.object);
fs.writeFileSync(
  path.join(ABI_DIR, 'Traceability.json'),
  JSON.stringify({ contractName: 'Traceability', abi: contract.abi, bytecode: contract.evm.bytecode.object }, null, 2)
);

console.log('> ABI  -> abi/Traceability.abi');
console.log('> BIN  -> bin/Traceability.bin');
console.log('> bytecode size: ' + contract.evm.bytecode.object.length / 2 + ' bytes');
