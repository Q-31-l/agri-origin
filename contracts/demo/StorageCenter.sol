// 指定 Solidity 编译器版本
pragma solidity 0.5.2;

contract StorageCenter {
    // 定义合约的拥有者地址
    address public owner;

    // 定义两个内部映射，用于存储数据的哈希值与数据信息之间的映射关系
    mapping (string => string) internal hashToDataInfoMap;
    mapping (string => string) internal hashToDataMap;

    // 定义两个映射，用于记录数据的访问次数和最后访问时间
    mapping (string => uint256) internal dataAccessCount;
    mapping (string => uint256) internal dataLastAccessedTime;

    // 定义两个事件，用于记录数据的访问和修改
    event writeRecord(address writer, string method, string firstParam, uint256 time);
    event dataAccessed(string hash, address accessor, uint256 accessTime);

    // 构造函数，初始化合约的拥有者地址
    constructor() public {
        owner = msg.sender;
    }

    // 定义一个修改器onlyOwner，用于限制某些函数只能由合约拥有者调用
    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner can call this function."); // 检查调用者是否为合约拥有者
        _; // 如果是拥有者，则继续执行函数
    }

    // 哈希生成
    // 入参: 无
    // 返回值: 无
    function genHash() public onlyOwner {
        emit writeRecord(msg.sender, "genHash", "", block.timestamp);
    }

    // 写入数据到合约
    // 入参: hash - 数据的哈希值, data - 数据内容, dataInfo - 数据的附加信息
    // 返回值: 无
    function writeData(string memory hash, string memory data, string memory dataInfo) public onlyOwner {

        // 将数据存储到映射中
        hashToDataMap[hash] = data;
        hashToDataInfoMap[hash] = dataInfo;

        // 记录写入操作
        emit writeRecord(msg.sender, "writeData", hash, now);
    }

    // 获取数据访问次数
    // 入参: hash - 数据的哈希值
    // 返回值: 数据的访问次数
    function getDataAccessCount(string memory hash) public view returns (uint256) {
        return dataAccessCount[hash];
    }

    // 获取数据的最后访问时间
    // 入参: hash - 数据的哈希值
    // 返回值: 数据的最后访问时间
    function getDataLastAccessedTime(string memory hash) public view returns (uint256) {
        return dataLastAccessedTime[hash];
    }

    // 访问数据并记录访问信息
    // 入参: hash - 数据的哈希值
    // 返回值: 数据内容, 数据信息, 访问次数, 最后访问时间
    function accessData(string memory hash) public returns (string memory, string memory, uint256, uint256) {
        // 获取数据内容
        string memory data = hashToDataMap[hash];
        // 获取数据信息
        string memory dataInfo = hashToDataInfoMap[hash];

        // 更新访问次数
        dataAccessCount[hash]++;
        // 更新最后访问时间
        dataLastAccessedTime[hash] = now;

        // 记录访问操作
        emit dataAccessed(hash, msg.sender, now);

        // 返回数据内容, 数据信息, 访问次数, 最后访问时间
        return (data, dataInfo, dataAccessCount[hash], dataLastAccessedTime[hash]);
    }

    // 删除数据
    // 入参: hash - 数据的哈希值
    // 返回值: 无
    function deleteData(string memory hash) public onlyOwner {
        // 删除数据
        delete hashToDataMap[hash];
        delete hashToDataInfoMap[hash];
        delete dataAccessCount[hash];
        delete dataLastAccessedTime[hash];
    }

    // 通过哈希值获取数据信息
    // 入参: hash - 数据的哈希值
    // 返回值: 数据内容, 数据信息, 访问次数, 最后访问时间
    function getDataInfoByHash(string memory hash) public view returns (string memory, string memory, uint256, uint256) {
        return (
            hashToDataMap[hash],
            hashToDataInfoMap[hash],
            dataAccessCount[hash],
            dataLastAccessedTime[hash]
        );
    }
}
