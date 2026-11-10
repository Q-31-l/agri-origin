package com.agri.trace.service;

import cn.hutool.crypto.digest.BCrypt;
import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.ResultCode;
import com.agri.trace.dto.UserDTO;
import com.agri.trace.dto.RegisterDTO;
import com.agri.trace.entity.BaseFarm;
import com.agri.trace.entity.BaseVillage;
import com.agri.trace.entity.SysRole;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.BaseFarmMapper;
import com.agri.trace.mapper.BaseVillageMapper;
import com.agri.trace.mapper.SysRoleMapper;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.PermissionCache;
import com.agri.trace.security.UserAuthCache;
import com.agri.trace.security.UserContext;
import com.agri.trace.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户与基层人员管理
 */
@Service
public class UserService {

    public static final String DEFAULT_PASSWORD = "123456";

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private BaseVillageMapper villageMapper;
    @Resource
    private BaseFarmMapper farmMapper;
    @Resource
    private PermissionCache permissionCache;
    @Resource
    private UserAuthCache userAuthCache;

    public PageResult<UserVO> page(long page, long size, String keyword, String roleCode, Long villageId) {
        LoginUser current = UserContext.require();
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(SysUser::getUsername, keyword).or()
                        .like(SysUser::getRealName, keyword).or()
                        .like(SysUser::getPhone, keyword))
                .eq(roleCode != null && !roleCode.isEmpty(), SysUser::getRoleCode, roleCode)
                .eq(villageId != null, SysUser::getVillageId, villageId)
                .orderByDesc(SysUser::getId);

        // 数据范围：村级管理员只能看本村
        if (Constants.ROLE_VILLAGE.equals(current.getRoleCode())) {
            wrapper.eq(SysUser::getVillageId, current.getVillageId());
        } else if (Constants.ROLE_FARMER.equals(current.getRoleCode())) {
            wrapper.eq(SysUser::getId, current.getUserId());
        }
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserVO> list = result.getRecords().stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
        return PageResult.of(list, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public List<UserVO> listByRole(String roleCode, Long villageId) {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, roleCode)
                .eq(villageId != null, SysUser::getVillageId, villageId)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        return users.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 用户下拉选项：只给 id、姓名、所属村。
     *
     * <p>农场档案要选"负责人"，但 /user/list 需要 user:list，会连带返回手机号、
     * 所属机构等字段。生产主体只需要知道"有哪些农户可选"，不该看到这些，
     * 所以单独开一个最小字段集的接口，权限挂在 farm:list 上（能管农场才需要选人）。
     */
    public List<Map<String, Object>> options(String roleCode, Long villageId) {
        LoginUser current = UserContext.require();
        // 生产主体与村管只能看到本村的人：选负责人/承包农户不能跨村，
        // 否则会把批次指派到别的村管辖区。管理员不受限。
        if (Constants.ROLE_PRODUCER.equals(current.getRoleCode())
                || Constants.ROLE_VILLAGE.equals(current.getRoleCode())) {
            villageId = current.getVillageId();
            if (villageId == null) {
                return new ArrayList<>();
            }
        }
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, roleCode)
                .eq(villageId != null, SysUser::getVillageId, villageId)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> list = new ArrayList<>(users.size());
        for (SysUser u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("realName", u.getRealName());
            m.put("villageId", u.getVillageId());
            list.add(m);
        }
        return list;
    }

    /**
     * 自助注册（匿名可调用）。
     *
     * <p>与 {@link #create(UserDTO)} 分开的原因：
     * <ul>
     *   <li>create 依赖 UserContext，而注册时没有登录态</li>
     *   <li>create 允许任意角色；注册只放行<b>真正参与溯源流程</b>的五种身份，
     *       治理类角色（村管、监管、管理员）必须由管理员开通</li>
     *   <li>各角色的必填项不同：农户必须有村（否则登录后看不到任何批次），
     *       机构类必须有机构名（溯源页要写责任主体）</li>
     * </ul>
     *
     * <p>为什么偏偏是这五个：平台的数据就是沿着
     * 生产主体建批次 → 农户记农事/采收 → 检测机构出报告 → 物流企业运输 → 销售商核销
     * 这条链产生的。链条上每一个录入方都应该能自己注册，缺一环就得找管理员代开账号。
     * 而村管能管本村账号、监管能看全量数据、管理员能改配置——这三种权力一旦自助开放，
     * 等于把别人的数据处置权交给任意访客，所以只能线下实名开通。</p>
     */
    @Transactional
    public Long register(RegisterDTO dto) {
        String role = dto.getRoleCode();
        boolean farmer = Constants.ROLE_FARMER.equals(role);
        boolean producer = Constants.ROLE_PRODUCER.equals(role);
        boolean inspector = Constants.ROLE_INSPECTOR.equals(role);
        boolean logistics = Constants.ROLE_LOGISTICS.equals(role);
        boolean seller = Constants.ROLE_SELLER.equals(role);
        boolean villageAdmin = Constants.ROLE_VILLAGE.equals(role);
        boolean regulator = Constants.ROLE_REGULATOR.equals(role);
        boolean orgRole = producer || inspector || logistics || seller || regulator;
        // 村级管理员、监管人员掌握他人数据的处置权，自助注册后必须经系统管理员审核才能启用
        boolean audited = villageAdmin || regulator;

        if (!farmer && !orgRole && !villageAdmin) {
            throw BizException.forbidden("该身份不支持自助注册，请联系管理员开通");
        }

        // 农户/生产主体/村级管理员都必须落到一个真实存在的村：农户没村看不到批次，
        // 村级管理员没村就没有辖区。生产主体建地块时也要选村。
        Long villageId = null;
        if (farmer || producer || villageAdmin) {
            villageId = resolveVillage(dto);
        }

        // 机构类（含监管）：机构名是溯源展示的责任主体，不能为空
        if (orgRole && (dto.getOrgName() == null || dto.getOrgName().isEmpty())) {
            throw BizException.badRequest("请填写" + orgLabel(role) + "名称");
        }

        // 需要审核的身份必须实名（填身份证号），否则管理员无法核实"这人是谁"
        if (audited && (dto.getIdCard() == null || dto.getIdCard().isEmpty())) {
            throw BizException.badRequest("请填写真实身份证号（实名认证）");
        }

        // 密码不能与账号相同，避免 admin/admin 这类弱口令
        if (dto.getUsername().equals(dto.getPassword())) {
            throw BizException.badRequest("密码不能与账号相同");
        }

        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("登录账号已被注册");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setIdCard(dto.getIdCard());
        user.setRoleCode(role);
        user.setOrgName(orgRole ? dto.getOrgName() : null);
        user.setVillageId(villageId);
        // 村级管理员/监管人员：待审核（status=0，审核通过才启用）；其余身份直接启用
        user.setStatus(audited ? 0 : 1);
        user.setAuditStatus(audited ? Constants.AUDIT_PENDING : null);
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 解析注册时给的村：优先用已存在村的 id，定位/列表都拿不到时允许自填村名。
     *
     * <p>自填走"同名复用、无则新建"：行政村是行政区划基础数据，同名大概率是同一个村，
     * 直接复用避免重复建档；新建的村暂无村管，该村批次需管理员后续配村管才能核验。</p>
     */
    private Long resolveVillage(RegisterDTO dto) {
        if (dto.getVillageId() != null) {
            BaseVillage village = villageMapper.selectById(dto.getVillageId());
            if (village == null) {
                throw BizException.badRequest("所选村不存在，请重新选择");
            }
            return village.getId();
        }
        String name = dto.getVillageName();
        if (name == null || name.isEmpty()) {
            throw BizException.badRequest("请选择所属村，或手动填写所在村");
        }
        if (name.length() > 30) {
            throw BizException.badRequest("村名过长，请精简后填写");
        }
        BaseVillage existing = villageMapper.selectOne(Wrappers.<BaseVillage>lambdaQuery()
                .eq(BaseVillage::getVillageName, name).last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        BaseVillage v = new BaseVillage();
        v.setVillageCode("SELF" + (System.currentTimeMillis() % 1000000));
        v.setVillageName(name);
        v.setRegionName(dto.getRegionName());
        villageMapper.insert(v);
        return v.getId();
    }

    /** 机构名的提示词：不同角色叫法不一样，报错要说得具体 */
    private String orgLabel(String role) {
        switch (role) {
            case Constants.ROLE_PRODUCER:
                return "合作社 / 家庭农场";
            case Constants.ROLE_INSPECTOR:
                return "检测机构";
            case Constants.ROLE_LOGISTICS:
                return "物流企业";
            case Constants.ROLE_SELLER:
                return "销售机构";
            case Constants.ROLE_REGULATOR:
                return "监管部门";
            default:
                return "机构";
        }
    }

    @Transactional
    public Long create(UserDTO dto) {
        LoginUser current = UserContext.require();
        if (Constants.ROLE_VILLAGE.equals(current.getRoleCode())) {
            // 村级管理员只能创建本村农户
            if (!Constants.ROLE_FARMER.equals(dto.getRoleCode())) {
                throw BizException.forbidden("村级管理员仅可新增本村农户账号");
            }
            dto.setVillageId(current.getVillageId());
        }
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("登录账号已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        String pwd = dto.getPassword() == null || dto.getPassword().isEmpty() ? DEFAULT_PASSWORD : dto.getPassword();
        user.setPassword(BCrypt.hashpw(pwd, BCrypt.gensalt()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRoleCode(dto.getRoleCode());
        user.setOrgName(dto.getOrgName());
        user.setVillageId(dto.getVillageId());
        user.setFarmId(dto.getFarmId());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        userMapper.insert(user);
        return user.getId();
    }

    @Transactional
    public void update(Long id, UserDTO dto) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        LoginUser current = UserContext.require();
        if (Constants.ROLE_VILLAGE.equals(current.getRoleCode())
                && !current.getVillageId().equals(user.getVillageId())) {
            throw BizException.forbidden("无权管理其他村的用户");
        }
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        if (!Constants.ROLE_VILLAGE.equals(current.getRoleCode())) {
            user.setRoleCode(dto.getRoleCode());
            user.setOrgName(dto.getOrgName());
            user.setVillageId(dto.getVillageId());
            user.setFarmId(dto.getFarmId());
        }
        user.setStatus(dto.getStatus() == null ? user.getStatus() : dto.getStatus());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        }
        userMapper.updateById(user);
        // 状态/角色/所属村可能刚被改动，立即废弃该账号的身份缓存，
        // 让禁用与降级在下一次请求就生效，而不是等 60 秒 TTL
        userAuthCache.evict(id);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (Constants.ROLE_VILLAGE.equals(UserContext.roleCode())
                && !UserContext.require().getVillageId().equals(user.getVillageId())) {
            throw BizException.forbidden("无权重置其他村用户的密码");
        }
        user.setPassword(BCrypt.hashpw(password == null || password.isEmpty() ? DEFAULT_PASSWORD : password,
                BCrypt.gensalt()));
        userMapper.updateById(user);
        userAuthCache.evict(id);
    }

    public SysUser getByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).last("limit 1"));
    }

    /**
     * 启用 / 停用账号。
     *
     * <p>与"删除"分开：停用保留全部历史与关联（批次责任人、审核留痕都还指得着），
     * 只是登录被拒；适用于人员离职、账号被盗等需要临时切断访问的场景。
     * 停用后 {@link UserAuthCache} 立即失效，令牌当次请求即被 401 拦下，
     * 不必等待 12 小时令牌自然过期。</p>
     */
    @Transactional
    public void toggleStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.badRequest("状态值无效，仅支持 1 启用 / 0 停用");
        }
        LoginUser current = UserContext.require();
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在或已被删除");
        }
        // 村管只能启停本村农户，口径与"编辑用户"保持一致；
        // 若放开到机构账号，村管就能把检测机构停掉，越过了辖区边界
        if (Constants.ROLE_VILLAGE.equals(current.getRoleCode())) {
            if (!Constants.ROLE_FARMER.equals(user.getRoleCode())
                    || !java.util.Objects.equals(current.getVillageId(), user.getVillageId())) {
                throw BizException.forbidden("村级管理员仅可启停本村农户账号");
            }
        }
        if (status == 0 && id.equals(current.getUserId())) {
            throw BizException.forbidden("不能停用当前登录的账号");
        }
        if (status == 0 && Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            assertNotLastAdmin(user, "停用");
        }
        if (java.util.Objects.equals(user.getStatus(), status)) {
            return;
        }
        user.setStatus(status);
        userMapper.updateById(user);
        userAuthCache.evict(id);
    }

    /**
     * 删除建错的账号。
     *
     * <p>采用逻辑删除而非物理删除：批次表冗余了责任人姓名、各环节留痕里也记着操作人，
     * 物理删行会让这些历史字段失去可追溯性。删除后该账号立即无法登录，
     * 且登录名会被释放（改为 {@code 原名#del{id}}），同名账号可以重新创建。</p>
     *
     * <p>唯一键 {@code uk_username} 不含删除标记，若不改名，重新注册同名账号会撞唯一索引，
     * 表现为"提示账号已存在却查不到这个人"。</p>
     */
    @Transactional
    public void delete(Long id) {
        LoginUser current = UserContext.require();
        if (id.equals(current.getUserId())) {
            throw BizException.forbidden("不能删除当前登录的账号");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在或已被删除");
        }
        if (Constants.ROLE_ADMIN.equals(user.getRoleCode())) {
            assertNotLastAdmin(user, "删除");
        }
        // 先释放登录名，再逻辑删除：updateById 带 deleted=0 条件，顺序反过来就改不到了
        String freedName = releaseUsername(user);
        if (freedName != null) {
            user.setUsername(freedName);
            userMapper.updateById(user);
        }
        userMapper.deleteById(id);
        userAuthCache.evict(id);
    }

    /** 释放登录名：追加删除标记并裁剪到列宽 64 以内 */
    private String releaseUsername(SysUser user) {
        String suffix = "#del" + user.getId();
        String base = user.getUsername() == null ? "user" : user.getUsername();
        if (base.endsWith(suffix)) {
            return null;
        }
        int keep = Math.max(1, 64 - suffix.length());
        if (base.length() > keep) {
            base = base.substring(0, keep);
        }
        return base + suffix;
    }

    /**
     * 系统管理员是唯一的"兜底"角色：停用或删除最后一个可用管理员后，
     * 没人能再审注册申请、改配置、处理违规，系统只能改库恢复。
     */
    private void assertNotLastAdmin(SysUser target, String action) {
        if (!Integer.valueOf(1).equals(target.getStatus())) {
            return;
        }
        Long remain = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleCode, Constants.ROLE_ADMIN)
                .eq(SysUser::getStatus, 1)
                .ne(SysUser::getId, target.getId()));
        if (remain == null || remain == 0) {
            throw BizException.forbidden("系统至少需要保留一个启用的管理员，无法" + action + "该账号");
        }
    }

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setIdCard(user.getIdCard());
        vo.setAvatar(user.getAvatar());
        vo.setRoleCode(user.getRoleCode());
        SysRole role = permissionCache.role(user.getRoleCode());
        vo.setRoleName(role == null ? user.getRoleCode() : role.getRoleName());
        vo.setDataScope(role == null ? "SELF" : role.getDataScope());
        vo.setOrgName(user.getOrgName());
        vo.setVillageId(user.getVillageId());
        vo.setFarmId(user.getFarmId());
        vo.setStatus(user.getStatus());
        vo.setAuditStatus(user.getAuditStatus());
        if (user.getVillageId() != null) {
            BaseVillage village = villageMapper.selectById(user.getVillageId());
            vo.setVillageName(village == null ? null : village.getVillageName());
        }
        if (user.getFarmId() != null) {
            BaseFarm farm = farmMapper.selectById(user.getFarmId());
            vo.setFarmName(farm == null ? null : farm.getFarmName());
        }
        return vo;
    }

    /** 待系统管理员审核的注册申请列表（村级管理员/监管人员） */
    public List<UserVO> pendingRegistrations() {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getAuditStatus, Constants.AUDIT_PENDING)
                .orderByAsc(SysUser::getId));
        return users.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 系统管理员审核自助注册申请（村级管理员/监管人员）。
     * 通过 → 启用账号；拒绝 → 保持停用。审核后立即废弃身份缓存，让结果即时生效。
     */
    @Transactional
    public void auditRegistration(Long id, boolean approve) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (!Constants.AUDIT_PENDING.equals(user.getAuditStatus())) {
            throw BizException.conflict("该申请已处理，请勿重复操作");
        }
        user.setAuditStatus(approve ? Constants.AUDIT_APPROVED : Constants.AUDIT_REJECTED);
        user.setStatus(approve ? 1 : 0);
        userMapper.updateById(user);
        userAuthCache.evict(id);
    }

    public SysUserMapper getMapper() {
        return userMapper;
    }
}
