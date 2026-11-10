package com.agri.trace.controller;

import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.R;
import com.agri.trace.dto.FarmDTO;
import com.agri.trace.dto.ProductDTO;
import com.agri.trace.dto.UserDTO;
import com.agri.trace.entity.BaseFarm;
import com.agri.trace.entity.BaseProduct;
import com.agri.trace.entity.BaseVillage;
import com.agri.trace.entity.SysDict;
import com.agri.trace.entity.SysOperationLog;
import com.agri.trace.entity.SysUser;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.security.RequireRoles;
import com.agri.trace.service.BaseDataService;
import com.agri.trace.service.LogService;
import com.agri.trace.service.UserService;
import com.agri.trace.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Tag(name = "系统与基础数据")
@RestController
@RequestMapping("/")
public class SystemController {

    @Resource
    private UserService userService;
    @Resource
    private BaseDataService baseDataService;
    @Resource
    private LogService logService;

    // ---------------- 用户 ----------------
    @Operation(summary = "用户分页查询")
    @RequirePerm("user:list")
    @GetMapping("/user/page")
    public R<PageResult<UserVO>> userPage(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String roleCode,
                                          @RequestParam(required = false) Long villageId) {
        return R.ok(userService.page(page, size, keyword, roleCode, villageId));
    }

    @Operation(summary = "按角色查询用户（农户/检测机构等下拉）")
    @RequirePerm("user:list")
    @GetMapping("/user/list")
    public R<List<UserVO>> userList(@RequestParam String roleCode,
                                    @RequestParam(required = false) Long villageId) {
        return R.ok(userService.listByRole(roleCode, villageId));
    }

    @Operation(summary = "用户下拉选项（仅 id/姓名/所属村，供农场负责人选择器）")
    @RequirePerm("farm:list")
    @GetMapping("/user/options")
    public R<List<Map<String, Object>>> userOptions(@RequestParam String roleCode,
                                                    @RequestParam(required = false) Long villageId) {
        return R.ok(userService.options(roleCode, villageId));
    }

    @Operation(summary = "新增用户（默认密码 123456）")
    @RequirePerm("user:create")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_VILLAGE})
    @PostMapping("/user")
    public R<Long> createUser(@Valid @RequestBody UserDTO dto) {
        return R.ok("用户创建成功", userService.create(dto));
    }

    @Operation(summary = "编辑用户")
    @RequirePerm("user:update")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_VILLAGE})
    @PutMapping("/user/{id}")
    public R<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        userService.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "重置密码")
    @RequirePerm("user:reset")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_VILLAGE})
    @PutMapping("/user/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id,
                                 @RequestParam(required = false) String password) {
        userService.resetPassword(id, password);
        return R.ok();
    }

    @Operation(summary = "启用 / 停用账号")
    @RequirePerm("user:disable")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_VILLAGE})
    @PutMapping("/user/{id}/status")
    public R<Void> toggleUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.toggleStatus(id, status);
        return R.<Void>ok(status != null && status == 1 ? "账号已启用" : "账号已停用", null);
    }

    @Operation(summary = "删除账号（仅系统管理员，逻辑删除并释放登录名）")
    @RequirePerm("user:delete")
    @RequireRoles(Constants.ROLE_ADMIN)
    @DeleteMapping("/user/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return R.<Void>ok("账号已删除", null);
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/user/me")
    public R<UserVO> me() {
        SysUser user = userService.getById(com.agri.trace.security.UserContext.userId());
        return R.ok(userService.toVO(user));
    }

    @Operation(summary = "待审核注册申请列表（仅系统管理员）")
    @RequirePerm("user:list")
    @RequireRoles(Constants.ROLE_ADMIN)
    @GetMapping("/user/pending")
    public R<List<UserVO>> pendingUsers() {
        return R.ok(userService.pendingRegistrations());
    }

    @Operation(summary = "审核注册申请（仅系统管理员），approve=true 通过 / false 拒绝")
    @RequirePerm("user:update")
    @RequireRoles(Constants.ROLE_ADMIN)
    @PostMapping("/user/{id}/audit")
    public R<Void> auditUser(@PathVariable Long id, @RequestParam boolean approve) {
        userService.auditRegistration(id, approve);
        return R.<Void>ok(approve ? "已通过，账号启用" : "已拒绝", null);
    }

    // ---------------- 行政村 ----------------
    @Operation(summary = "行政村列表")
    @RequirePerm("village:list")
    @GetMapping("/village/list")
    public R<List<BaseVillage>> villages() {
        return R.ok(baseDataService.villages());
    }

    @Operation(summary = "新增行政村")
    @RequirePerm("village:create")
    @RequireRoles(Constants.ROLE_ADMIN)
    @PostMapping("/village")
    public R<Long> createVillage(@RequestBody BaseVillage village) {
        return R.ok("行政村创建成功", baseDataService.createVillage(village));
    }

    // ---------------- 农场 ----------------
    @Operation(summary = "农场分页查询")
    @RequirePerm("farm:list")
    @GetMapping("/farm/page")
    public R<PageResult<BaseFarm>> farmPage(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long villageId) {
        return R.ok(baseDataService.farmPage(page, size, keyword, villageId));
    }

    @Operation(summary = "农场下拉列表")
    @RequirePerm("farm:list")
    @GetMapping("/farm/list")
    public R<List<BaseFarm>> farms(@RequestParam(required = false) Long villageId) {
        return R.ok(baseDataService.farms(villageId));
    }

    @Operation(summary = "新增农场")
    @RequirePerm("farm:create")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_VILLAGE, Constants.ROLE_PRODUCER})
    @PostMapping("/farm")
    public R<Long> createFarm(@Valid @RequestBody FarmDTO dto) {
        return R.ok("农场创建成功", baseDataService.createFarm(dto));
    }

    @Operation(summary = "编辑农场")
    @RequirePerm("farm:update")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_VILLAGE, Constants.ROLE_PRODUCER})
    @PutMapping("/farm/{id}")
    public R<Void> updateFarm(@PathVariable Long id, @Valid @RequestBody FarmDTO dto) {
        baseDataService.updateFarm(id, dto);
        return R.ok();
    }

    // ---------------- 产品 ----------------
    @Operation(summary = "产品分页查询")
    @RequirePerm("product:list")
    @GetMapping("/product/page")
    public R<PageResult<BaseProduct>> productPage(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String keyword) {
        return R.ok(baseDataService.productPage(page, size, keyword));
    }

    @Operation(summary = "产品下拉列表")
    @RequirePerm("product:list")
    @GetMapping("/product/list")
    public R<List<BaseProduct>> products() {
        return R.ok(baseDataService.products());
    }

    @Operation(summary = "新增产品")
    @RequirePerm("product:create")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_PRODUCER})
    @PostMapping("/product")
    public R<Long> createProduct(@Valid @RequestBody ProductDTO dto) {
        return R.ok("产品创建成功", baseDataService.createProduct(dto));
    }

    @Operation(summary = "编辑产品")
    @RequirePerm("product:update")
    @RequireRoles({Constants.ROLE_ADMIN, Constants.ROLE_PRODUCER})
    @PutMapping("/product/{id}")
    public R<Void> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO dto) {
        baseDataService.updateProduct(id, dto);
        return R.ok();
    }

    // ---------------- 字典与日志 ----------------
    @Operation(summary = "数据字典")
    @GetMapping("/dict/{type}")
    public R<List<SysDict>> dict(@PathVariable String type) {
        return R.ok(baseDataService.dict(type));
    }

    @Operation(summary = "操作日志查询")
    @RequirePerm("log:list")
    @GetMapping("/log/page")
    public R<PageResult<SysOperationLog>> logs(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) String module) {
        return R.ok(logService.page(page, size, username, module));
    }
}
