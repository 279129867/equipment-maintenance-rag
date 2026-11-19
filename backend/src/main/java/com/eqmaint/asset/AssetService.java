package com.eqmaint.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eqmaint.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AssetService {

    private final AssetMapper mapper;

    public AssetService(AssetMapper mapper) {
        this.mapper = mapper;
    }

    public Page<EqAsset> page(int pageNo, int size, String workshop, String assetType, String keyword) {
        LambdaQueryWrapper<EqAsset> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(workshop), EqAsset::getWorkshop, workshop)
          .eq(StringUtils.hasText(assetType), EqAsset::getAssetType, assetType)
          .and(StringUtils.hasText(keyword), w -> w.like(EqAsset::getAssetCode, keyword)
                  .or().like(EqAsset::getAssetName, keyword))
          .orderByAsc(EqAsset::getId);
        return mapper.selectPage(new Page<>(pageNo, size), qw);
    }

    public EqAsset get(Long id) {
        EqAsset a = mapper.selectById(id);
        if (a == null) {
            throw new BizException("设备不存在：" + id);
        }
        return a;
    }

    public EqAsset create(EqAsset a) {
        if (a.getCriticalLevel() == null) {
            a.setCriticalLevel(2);
        }
        if (a.getRunStatus() == null) {
            a.setRunStatus(1);
        }
        mapper.insert(a);
        return a;
    }

    public EqAsset update(Long id, EqAsset a) {
        get(id);
        a.setId(id);
        a.setDeleted(null);
        mapper.updateById(a);
        return get(id);
    }

    public void delete(Long id) {
        get(id);
        mapper.deleteById(id);
    }
}