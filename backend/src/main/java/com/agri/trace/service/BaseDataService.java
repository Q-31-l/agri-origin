package com.agri.trace.service;

import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.common.Db;
import com.agri.trace.common.PageResult;
import com.agri.trace.dto.FarmDTO;
import com.agri.trace.dto.ProductDTO;
import com.agri.trace.entity.BaseFarm;
import com.agri.trace.entity.BaseProduct;
import com.agri.trace.entity.BaseVillage;
import com.agri.trace.entity.SysDict;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.BaseFarmMapper;
import com.agri.trace.mapper.BaseProductMapper;
import com.agri.trace.mapper.BaseVillageMapper;
import com.agri.trace.mapper.SysDictMapper;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 基础档案：行政村、农场、产品品类、字典
 */
@Service
public class BaseDataService {

    @Resource
    private BaseVillageMapper villageMapper;
    @Resource
    private BaseFarmMapper farmMapper;
    @Resource
    private BaseProductMapper productMapper;
    @Resource
    private SysDictMapper dictMapper;
    @Resource
    private SysUserMapper userMapper;

    // ---------------- 行政村 ----------------
    public List<BaseVillage> villages() {
        LoginUser user = UserContext.require();
        LambdaQueryWrapper<BaseVillage> wrapper = Wrappers.<BaseVillage>lambdaQuery().orderByAsc(BaseVillage::getId);
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            wrapper.eq(BaseVillage::getId, user.getVillageId());
        }
        return villageMapper.selectList(wrapper);
    }

    @Transactional
    public Long createVillage(BaseVillage village) {
        Long exists = villageMapper.selectCount(Wrappers.<BaseVillage>lambdaQuery()
                .eq(BaseVillage::getVillageCode, village.getVillageCode()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("行政村编码已存在");
        }
        villageMapper.insert(village);
        return village.getId();
    }

    // ---------------- 农场 ----------------
    public PageResult<BaseFarm> farmPage(long page, long size, String keyword, Long villageId) {
        LoginUser user = UserContext.require();
        LambdaQueryWrapper<BaseFarm> wrapper = Wrappers.<BaseFarm>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(BaseFarm::getFarmName, keyword).or()
                        .like(BaseFarm::getFarmerName, keyword).or()
                        .like(BaseFarm::getFarmCode, keyword))
                .eq(villageId != null, BaseFarm::getVillageId, villageId)
                .orderByDesc(BaseFarm::getId);
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            wrapper.eq(BaseFarm::getVillageId, user.getVillageId());
        } else if (Constants.ROLE_FARMER.equals(user.getRoleCode())) {
            wrapper.eq(BaseFarm::getFarmerId, user.getUserId());
        }
        return PageResult.of(farmMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<BaseFarm> farms(Long villageId) {
        LoginUser user = UserContext.require();
        LambdaQueryWrapper<BaseFarm> wrapper = Wrappers.<BaseFarm>lambdaQuery()
                .eq(BaseFarm::getStatus, 1)
                .orderByAsc(BaseFarm::getId);
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())) {
            wrapper.eq(BaseFarm::getVillageId, user.getVillageId());
        } else if (Constants.ROLE_PRODUCER.equals(user.getRoleCode())) {
            // 生产主体只能看本村地块：建批次选地块不能跨村，与选农户同口径
            wrapper.eq(BaseFarm::getVillageId, user.getVillageId());
        } else if (villageId != null) {
            wrapper.eq(BaseFarm::getVillageId, villageId);
        }
        return farmMapper.selectList(wrapper);
    }

    @Transactional
    public Long createFarm(FarmDTO dto) {
        LoginUser user = UserContext.require();
        Long villageId = dto.getVillageId();
        // 村管与生产主体都只能在自己所属村建地块：生产主体自助注册后要能登记基地，
        // 但不能借这个入口给别的村造地块
        if (Constants.ROLE_VILLAGE.equals(user.getRoleCode())
                || Constants.ROLE_PRODUCER.equals(user.getRoleCode())) {
            villageId = user.getVillageId();
            if (villageId == null) {
                throw BizException.badRequest("当前账号未绑定行政村，请联系管理员补齐");
            }
        }
        if (villageId == null) {
            throw BizException.badRequest("请选择所属行政村");
        }
        BaseVillage village = villageMapper.selectById(villageId);
        if (village == null) {
            throw BizException.notFound("行政村不存在");
        }
        if (dto.getFarmerId() == null) {
            throw BizException.badRequest("请选择承包农户");
        }
        SysUser farmer = userMapper.selectById(dto.getFarmerId());
        if (farmer == null || !Constants.ROLE_FARMER.equals(farmer.getRoleCode())) {
            throw BizException.badRequest("承包农户不存在或角色不正确");
        }

        BaseFarm farm = new BaseFarm();
        farm.setFarmCode(generateFarmCode(village));
        farm.setFarmName(dto.getFarmName());
        farm.setFarmerId(dto.getFarmerId());
        farm.setFarmerName(farmer.getRealName());
        farm.setVillageId(villageId);
        farm.setVillageName(village.getVillageName());
        farm.setAddress(dto.getAddress());
        farm.setArea(dto.getArea());
        farm.setSoilType(dto.getSoilType());
        farm.setWaterSource(dto.getWaterSource());
        farm.setCertNo(dto.getCertNo());
        farm.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        farmMapper.insert(farm);
        return farm.getId();
    }

    @Transactional
    public void updateFarm(Long id, FarmDTO dto) {
        BaseFarm farm = farmMapper.selectById(id);
        if (farm == null) {
            throw BizException.notFound("农场不存在");
        }
        if (Constants.ROLE_VILLAGE.equals(UserContext.roleCode())
                && !UserContext.require().getVillageId().equals(farm.getVillageId())) {
            throw BizException.forbidden("无权管理其他村的农场");
        }
        if (Constants.ROLE_PRODUCER.equals(UserContext.roleCode())
                && !farm.getVillageId().equals(UserContext.require().getVillageId())) {
            throw BizException.forbidden("无权管理其他村的农场");
        }
        farm.setFarmName(dto.getFarmName());
        farm.setAddress(dto.getAddress());
        farm.setArea(dto.getArea());
        farm.setSoilType(dto.getSoilType());
        farm.setWaterSource(dto.getWaterSource());
        farm.setCertNo(dto.getCertNo());
        farm.setStatus(dto.getStatus() == null ? farm.getStatus() : dto.getStatus());
        Db.updated(farmMapper.updateById(farm));
    }

    private String generateFarmCode(BaseVillage village) {
        String prefix = "FARM-" + (village.getVillageCode() == null ? "000000" : village.getVillageCode());
        long count = farmMapper.selectCount(Wrappers.<BaseFarm>lambdaQuery()
                .likeRight(BaseFarm::getFarmCode, prefix));
        return prefix + "-" + String.format("%03d", count + 1);
    }

    // ---------------- 产品 ----------------
    public List<BaseProduct> products() {
        return productMapper.selectList(Wrappers.<BaseProduct>lambdaQuery().orderByAsc(BaseProduct::getId));
    }

    public PageResult<BaseProduct> productPage(long page, long size, String keyword) {
        LambdaQueryWrapper<BaseProduct> wrapper = Wrappers.<BaseProduct>lambdaQuery()
                .like(keyword != null && !keyword.isEmpty(), BaseProduct::getProductName, keyword)
                .orderByDesc(BaseProduct::getId);
        return PageResult.of(productMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Transactional
    public Long createProduct(ProductDTO dto) {
        BaseProduct product = new BaseProduct();
        product.setProductCode("P-" + System.currentTimeMillis() % 1000000);
        product.setProductName(dto.getProductName());
        product.setCategory(dto.getCategory());
        product.setVariety(dto.getVariety());
        product.setUnit(dto.getUnit());
        product.setShelfLifeDays(dto.getShelfLifeDays());
        product.setOriginRegion(dto.getOriginRegion());
        product.setDescription(dto.getDescription());
        productMapper.insert(product);
        return product.getId();
    }

    @Transactional
    public void updateProduct(Long id, ProductDTO dto) {
        BaseProduct product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("产品不存在");
        }
        product.setProductName(dto.getProductName());
        product.setCategory(dto.getCategory());
        product.setVariety(dto.getVariety());
        product.setUnit(dto.getUnit());
        product.setShelfLifeDays(dto.getShelfLifeDays());
        product.setOriginRegion(dto.getOriginRegion());
        product.setDescription(dto.getDescription());
        productMapper.updateById(product);
    }

    // ---------------- 字典 ----------------
    public List<SysDict> dict(String type) {
        return dictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSortNo));
    }
}
