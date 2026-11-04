// SPDX-License-Identifier: MIT
pragma solidity ^0.6.10;

/**
 * @title TraceLib
 * @notice 溯源合约公共工具库：字符串判等、空值校验、哈希拼接。
 *         以 internal 函数方式内联，不单独部署，降低链上调用开销。
 */
library TraceLib {
    function isEmpty(string memory s) internal pure returns (bool) {
        return bytes(s).length == 0;
    }

    function equal(string memory a, string memory b) internal pure returns (bool) {
        return keccak256(abi.encodePacked(a)) == keccak256(abi.encodePacked(b));
    }

    /**
     * @dev 生成记录级摘要：keccak256(batchNo|bizType|bizId|dataHash)
     *      用于链上二次校验，防止单字段被替换。
     */
    function recordDigest(
        string memory batchNo,
        string memory bizType,
        string memory bizId,
        string memory dataHash
    ) internal pure returns (bytes32) {
        return keccak256(abi.encodePacked(batchNo, "|", bizType, "|", bizId, "|", dataHash));
    }
}
