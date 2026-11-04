// SPDX-License-Identifier: MIT
pragma solidity ^0.6.10;

import "./TraceLib.sol";

/**
 * @title Traceability
 * @notice 农产品质量安全溯源存证合约（FISCO-BCOS 2.9.x / Solidity 0.6.10）
 *
 * 设计要点：
 * 1. 链上只存"批次索引 + 业务摘要 + 文件摘要"，不存业务明细，兼顾隐私与性能；
 * 2. 批次一旦登记不可删除，状态只能向前流转（NORMAL -> ABNORMAL -> CLOSED）；
 * 3. 异常标记与处置均为追加写，保留完整历史；
 * 4. 写入地址白名单控制，未授权地址只能读，不能写。
 */
contract Traceability {
    using TraceLib for string;

    struct BatchInfo {
        string batchNo;
        string traceCode;
        string productName;
        string origin;
        string latestHash;
        string status;          // NORMAL / ABNORMAL / CLOSED
        string abnormalReason;
        uint256 createdAt;
        uint256 recordCount;
        bool exists;
    }

    struct Record {
        string bizType;         // PLANTING / HARVEST / QUALITY / LOGISTICS / SALES / ABNORMAL / DISPOSAL / CORRECTION
        string bizId;
        string dataHash;
        string fileHash;
        string operatorName;
        uint256 timestamp;
        bool abnormal;
    }

    address public owner;
    mapping(address => bool) public authorized;
    mapping(string => BatchInfo) private _batches;
    mapping(string => Record[]) private _records;
    string[] private _batchNos;

    event BatchRegistered(string batchNo, string traceCode, string dataHash, string operatorName, uint256 timestamp);
    event RecordAdded(string batchNo, string bizType, string bizId, string dataHash, string fileHash, string operatorName, uint256 timestamp);
    event BatchMarkedAbnormal(string batchNo, string reason, string dataHash, string operatorName, uint256 timestamp);
    event BatchDisposed(string batchNo, string result, string dataHash, string operatorName, uint256 timestamp);
    event BatchClosed(string batchNo, string dataHash, string operatorName, uint256 timestamp);
    event CorrectionAdded(string batchNo, string bizType, string bizId, string correctionHash, string operatorName, uint256 timestamp);
    event Authorized(address indexed account, bool enabled);

    modifier onlyOwner() {
        require(msg.sender == owner, "only owner");
        _;
    }

    modifier onlyAuthorized() {
        require(authorized[msg.sender], "not authorized");
        _;
    }

    modifier batchExists(string memory batchNo) {
        require(_batches[batchNo].exists, "batch not found");
        _;
    }

    constructor() public {
        owner = msg.sender;
        authorized[msg.sender] = true;
        emit Authorized(msg.sender, true);
    }

    // ------------------------------------------------------------------
    // 权限管理
    // ------------------------------------------------------------------
    function authorize(address account) public onlyOwner {
        require(account != address(0), "invalid address");
        authorized[account] = true;
        emit Authorized(account, true);
    }

    function revoke(address account) public onlyOwner {
        require(account != owner, "cannot revoke owner");
        authorized[account] = false;
        emit Authorized(account, false);
    }

    function transferOwnership(address newOwner) public onlyOwner {
        require(newOwner != address(0), "invalid address");
        owner = newOwner;
        authorized[newOwner] = true;
        emit Authorized(newOwner, true);
    }

    // ------------------------------------------------------------------
    // 批次
    // ------------------------------------------------------------------
    function registerBatch(
        string memory batchNo,
        string memory traceCode,
        string memory productName,
        string memory origin,
        string memory dataHash,
        string memory operatorName
    ) public onlyAuthorized {
        require(!batchNo.isEmpty(), "batchNo empty");
        require(!_batches[batchNo].exists, "batch already exists");
        require(!dataHash.isEmpty(), "dataHash empty");

        _batches[batchNo] = BatchInfo({
            batchNo: batchNo,
            traceCode: traceCode,
            productName: productName,
            origin: origin,
            latestHash: dataHash,
            status: "NORMAL",
            abnormalReason: "",
            createdAt: now,
            recordCount: 0,
            exists: true
        });
        _batchNos.push(batchNo);

        emit BatchRegistered(batchNo, traceCode, dataHash, operatorName, now);
    }

    function addRecord(
        string memory batchNo,
        string memory bizType,
        string memory bizId,
        string memory dataHash,
        string memory fileHash,
        string memory operatorName
    ) public onlyAuthorized batchExists(batchNo) {
        require(!dataHash.isEmpty(), "dataHash empty");

        BatchInfo storage info = _batches[batchNo];
        require(!TraceLib.equal(info.status, "CLOSED"), "batch closed");
        if (TraceLib.equal(info.status, "ABNORMAL")) {
            // 异常批次仅允许追加处置/更正类记录，禁止正常流转记录
            require(
                TraceLib.equal(bizType, "DISPOSAL") || TraceLib.equal(bizType, "CORRECTION"),
                "abnormal batch locked"
            );
        }

        _records[batchNo].push(Record({
            bizType: bizType,
            bizId: bizId,
            dataHash: dataHash,
            fileHash: fileHash,
            operatorName: operatorName,
            timestamp: now,
            abnormal: TraceLib.equal(info.status, "ABNORMAL")
        }));
        info.recordCount = info.recordCount + 1;
        info.latestHash = dataHash;

        emit RecordAdded(batchNo, bizType, bizId, dataHash, fileHash, operatorName, now);
    }

    function markAbnormal(
        string memory batchNo,
        string memory reason,
        string memory dataHash,
        string memory operatorName
    ) public onlyAuthorized batchExists(batchNo) {
        BatchInfo storage info = _batches[batchNo];
        require(!TraceLib.equal(info.status, "CLOSED"), "batch closed");
        require(!TraceLib.equal(info.status, "ABNORMAL"), "already abnormal");

        info.status = "ABNORMAL";
        info.abnormalReason = reason;
        info.latestHash = dataHash;

        _records[batchNo].push(Record({
            bizType: "ABNORMAL",
            bizId: batchNo,
            dataHash: dataHash,
            fileHash: "",
            operatorName: operatorName,
            timestamp: now,
            abnormal: true
        }));
        info.recordCount = info.recordCount + 1;

        emit BatchMarkedAbnormal(batchNo, reason, dataHash, operatorName, now);
    }

    function disposeAbnormal(
        string memory batchNo,
        string memory result,
        string memory dataHash,
        string memory operatorName
    ) public onlyAuthorized batchExists(batchNo) {
        BatchInfo storage info = _batches[batchNo];
        require(TraceLib.equal(info.status, "ABNORMAL"), "batch not abnormal");

        _records[batchNo].push(Record({
            bizType: "DISPOSAL",
            bizId: batchNo,
            dataHash: dataHash,
            fileHash: "",
            operatorName: operatorName,
            timestamp: now,
            abnormal: true
        }));
        info.recordCount = info.recordCount + 1;
        info.latestHash = dataHash;
        info.abnormalReason = result;

        emit BatchDisposed(batchNo, result, dataHash, operatorName, now);
    }

    function closeBatch(
        string memory batchNo,
        string memory dataHash,
        string memory operatorName
    ) public onlyAuthorized batchExists(batchNo) {
        BatchInfo storage info = _batches[batchNo];
        require(!TraceLib.equal(info.status, "CLOSED"), "already closed");
        info.status = "CLOSED";
        info.latestHash = dataHash;
        emit BatchClosed(batchNo, dataHash, operatorName, now);
    }

    function addCorrection(
        string memory batchNo,
        string memory bizType,
        string memory bizId,
        string memory correctionHash,
        string memory operatorName
    ) public onlyAuthorized batchExists(batchNo) {
        BatchInfo storage info = _batches[batchNo];
        _records[batchNo].push(Record({
            bizType: "CORRECTION",
            bizId: bizId,
            dataHash: correctionHash,
            fileHash: "",
            operatorName: operatorName,
            timestamp: now,
            abnormal: TraceLib.equal(info.status, "ABNORMAL")
        }));
        info.recordCount = info.recordCount + 1;
        emit CorrectionAdded(batchNo, bizType, bizId, correctionHash, operatorName, now);
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------
    function getBatch(string memory batchNo)
        public
        view
        returns (
            string memory traceCode,
            string memory productName,
            string memory origin,
            string memory latestHash,
            string memory status,
            string memory abnormalReason,
            uint256 createdAt,
            uint256 recordCount
        )
    {
        BatchInfo storage info = _batches[batchNo];
        require(info.exists, "batch not found");
        return (
            info.traceCode,
            info.productName,
            info.origin,
            info.latestHash,
            info.status,
            info.abnormalReason,
            info.createdAt,
            info.recordCount
        );
    }

    function getRecord(string memory batchNo, uint256 index)
        public
        view
        returns (
            string memory bizType,
            string memory bizId,
            string memory dataHash,
            string memory fileHash,
            string memory operatorName,
            uint256 timestamp,
            bool abnormal
        )
    {
        require(index < _records[batchNo].length, "index out of range");
        Record storage r = _records[batchNo][index];
        return (r.bizType, r.bizId, r.dataHash, r.fileHash, r.operatorName, r.timestamp, r.abnormal);
    }

    function getRecordCount(string memory batchNo) public view returns (uint256) {
        return _records[batchNo].length;
    }

    function getRecordDigest(string memory batchNo, uint256 index) public view returns (bytes32) {
        require(index < _records[batchNo].length, "index out of range");
        Record storage r = _records[batchNo][index];
        return TraceLib.recordDigest(batchNo, r.bizType, r.bizId, r.dataHash);
    }

    /// @notice 链上链下一致性校验：比对指定记录的 dataHash
    function verifyRecord(
        string memory batchNo,
        uint256 index,
        string memory dataHash
    ) public view returns (bool) {
        if (index >= _records[batchNo].length) {
            return false;
        }
        return TraceLib.equal(_records[batchNo][index].dataHash, dataHash);
    }

    function batchExistsOf(string memory batchNo) public view returns (bool) {
        return _batches[batchNo].exists;
    }

    function getBatchCount() public view returns (uint256) {
        return _batchNos.length;
    }

    function getBatchNoByIndex(uint256 index) public view returns (string memory) {
        require(index < _batchNos.length, "index out of range");
        return _batchNos[index];
    }
}
