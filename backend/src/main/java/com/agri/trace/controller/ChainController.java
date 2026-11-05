package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.entity.ChainRecord;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.security.UserContext;
import com.agri.trace.service.BatchService;
import com.agri.trace.service.ChainService;
import com.agri.trace.sdk.TraceContractService;
import com.agri.trace.vo.ChainVerifyVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "链上存证与一致性校验")
@RestController
@RequestMapping("/chain")
public class ChainController {

    @Resource
    private ChainService chainService;
    @Resource
    private BatchService batchService;
    @Resource
    private TraceContractService contractService;

    @Operation(summary = "存证流水分页查询")
    @RequirePerm("chain:list")
    @GetMapping("/page")
    public R<PageResult<ChainRecord>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String batchNo,
                                           @RequestParam(required = false) String status) {
        return R.ok(chainService.page(page, size, batchNo, status));
    }

    @Operation(summary = "按批次号进行链上链下一致性校验")
    @RequirePerm("chain:verify")
    @GetMapping("/verify/{batchNo}")
    public R<ChainVerifyVO> verify(@PathVariable String batchNo) {
        batchService.getByNo(batchNo);
        return R.ok(chainService.verifyBatch(batchNo));
    }

    @Operation(summary = "链服务状态")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", contractService.isEnabled());
        map.put("ready", contractService.isReady());
        map.put("message", contractService.getChainMessage());
        map.put("contractAddress", contractService.getContractAddress());
        map.put("blockNumber", contractService.getBlockNumber());
        return R.ok(map);
    }

    @Operation(summary = "修复：将未上链的历史批次补注册到链上")
    @RequirePerm("chain:repair")
    @RequireRoles({Constants.ROLE_REGULATOR, Constants.ROLE_ADMIN})
    @PostMapping("/repair/batches")
    public R<Map<String, Object>> repairBatches() {
        LoginUser user = UserContext.require();
        return R.ok(batchService.repairUnchainedBatches(user.getRealName()));
    }
}
