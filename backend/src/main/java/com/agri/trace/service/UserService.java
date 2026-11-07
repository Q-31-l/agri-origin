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
        boolean orgRole = producer || inspector || logistics || seller;

        if (!farmer && !orgRole) {
            throw BizException.forbidden("该身份不支持自助注册，请联系管理员开通");
        }

        // 农户：必须选一个真实存在的村——数据范围按 farmer_id 收窄，但村的归属决定
        // 后续批次能落到哪个村管辖区；生产主体同理，建地块时要选村。
        if (farmer || producer) {
            if (dto.getVillageId() == null) {
                throw BizException.badRequest("请选择所属村");
            }
            BaseVillage village = villageMapper.selectById(dto.getVillageId());
            if (village == null) {
                throw BizException.badRequest("所选村不存在，请重新选择");
            }
        }

        // 机构类：机构名是溯源展示的责任主体，不能为空
        if (orgRole && (dto.getOrgName() == null || dto.getOrgName().isEmpty())) {
            throw BizException.badRequest("请填写" + orgLabel(role) + "名称");
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
        user.setRoleCode(role);
        user.setOrgName(orgRole ? dto.getOrgName() : null);
        user.setVillageId(farmer || producer ? dto.getVillageId() : null);
        // farmId 留空：农户的数据范围按 farmerId 隔离，不要求必须有农场才能登录。
        // 首次建档时再选择或新建农场即可；生产主体注册后在"农场档案"里建自己的地块。
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
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
        vo.setAvatar(user.getAvatar());
        vo.setRoleCode(user.getRoleCode());
        SysRole role = permissionCache.role(user.getRoleCode());
        vo.setRoleName(role == null ? user.getRoleCode() : role.getRoleName());
        vo.setDataScope(role == null ? "SELF" : role.getDataScope());
        vo.setOrgName(user.getOrgName());
        vo.setVillageId(user.getVillageId());
        vo.setFarmId(user.getFarmId());
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

    public SysUserMapper getMapper() {
        return userMapper;
    }
}
