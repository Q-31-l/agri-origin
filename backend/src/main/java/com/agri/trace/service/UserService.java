package com.agri.trace.service;

import cn.hutool.crypto.digest.BCrypt;
import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.PageResult;
import com.agri.trace.common.ResultCode;
import com.agri.trace.dto.UserDTO;
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
