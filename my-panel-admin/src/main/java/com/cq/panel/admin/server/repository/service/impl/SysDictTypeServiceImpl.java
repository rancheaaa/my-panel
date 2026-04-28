package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.common.constant.UserConstants;
import com.cq.panel.admin.server.repository.domain.SysDictData;
import com.cq.panel.admin.server.repository.domain.SysDictType;
import com.cq.panel.admin.server.web.exception.ServiceException;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.repository.mapper.SysDictDataMapper;
import com.cq.panel.admin.server.repository.mapper.SysDictTypeMapper;
import com.cq.panel.admin.server.repository.service.ISysDictTypeService;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典 业务层处理
 * 
 * @author cq
 */
@Service
@CacheConfig(cacheNames = CacheConstants.SYS_DICT_KEY)
public class SysDictTypeServiceImpl implements ISysDictTypeService
{
    private final SysDictTypeMapper dictTypeMapper;

    private final SysDictDataMapper dictDataMapper;

    private final CacheManager cacheManager;

    public SysDictTypeServiceImpl(SysDictTypeMapper dictTypeMapper, SysDictDataMapper dictDataMapper, CacheManager cacheManager) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictDataMapper = dictDataMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * 项目启动时，初始化字典到缓存
     */
    @PostConstruct
    public void init()
    {
        loadingDictCache();
    }

    /**
     * 根据条件分页查询字典类型
     * 
     * @param dictType 字典类型信息
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictType> selectDictTypeList(SysDictType dictType)
    {
        return dictTypeMapper.selectDictTypeList(dictType);
    }

    /**
     * 根据所有字典类型
     * 
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictType> selectDictTypeAll()
    {
        return dictTypeMapper.selectDictTypeAll();
    }

    /**
     * 根据字典类型查询字典数据
     * 
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Override
    @Cacheable(key = "#dictType")
    public List<SysDictData> selectDictDataByType(String dictType)
    {
        List<SysDictData> dictData = dictDataMapper.selectDictDataByType(dictType);
        if (MyStringUtils.isNotEmpty(dictData))
        {
            return dictData;
        }
        return null;
    }

    /**
     * 根据字典类型ID查询信息
     * 
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    @Override
    public SysDictType selectDictTypeById(Long dictId)
    {
        return dictTypeMapper.selectDictTypeById(dictId);
    }

    /**
     * 根据字典类型查询信息
     * 
     * @param dictType 字典类型
     * @return 字典类型
     */
    @Override
    public SysDictType selectDictTypeByType(String dictType)
    {
        return dictTypeMapper.selectDictTypeByType(dictType);
    }

    /**
     * 批量删除字典类型信息
     * 
     * @param dictIds 需要删除的字典ID
     */
    @Override
    public void deleteDictTypeByIds(Long[] dictIds)
    {
        for (Long dictId : dictIds)
        {
            SysDictType dictType = selectDictTypeById(dictId);
            if (dictDataMapper.countDictDataByType(dictType.getDictType()) > 0)
            {
                throw new ServiceException(String.format("%1$s已分配,不能删除", dictType.getDictName()));
            }
            dictTypeMapper.deleteDictTypeById(dictId);
            Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
            if (cache != null)
            {
                cache.evict(dictType.getDictType());
            }
        }
    }

    /**
     * 加载字典缓存数据
     */
    @Override
    public void loadingDictCache()
    {
        SysDictData dictData = new SysDictData();
        dictData.setStatus("0");
        Map<String, List<SysDictData>> dictDataMap = dictDataMapper.selectDictDataList(dictData).stream().collect(Collectors.groupingBy(SysDictData::getDictType));
        Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
        if (cache != null)
        {
            for (Map.Entry<String, List<SysDictData>> entry : dictDataMap.entrySet())
            {
                cache.put(entry.getKey(), entry.getValue().stream().sorted(Comparator.comparing(SysDictData::getDictSort)).collect(Collectors.toList()));
            }
        }
    }

    /**
     * 清空字典缓存数据
     */
    @Override
    public void clearDictCache()
    {
        Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
        if (cache != null)
        {
            cache.clear();
        }
    }

    /**
     * 重置字典缓存数据
     */
    @Override
    public void resetDictCache()
    {
        clearDictCache();
        loadingDictCache();
    }

    /**
     * 新增保存字典类型信息
     * 
     * @param dict 字典类型信息
     * @return 结果
     */
    @Override
    public int insertDictType(SysDictType dict)
    {
        int row = dictTypeMapper.insertDictType(dict);
        if (row > 0)
        {
            Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
            if (cache != null)
            {
                cache.evict(dict.getDictType());
            }
        }
        return row;
    }

    /**
     * 修改保存字典类型信息
     * 
     * @param dict 字典类型信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateDictType(SysDictType dict)
    {
        SysDictType oldDict = dictTypeMapper.selectDictTypeById(dict.getDictId());
        dictDataMapper.updateDictDataType(oldDict.getDictType(), dict.getDictType());
        int row = dictTypeMapper.updateDictType(dict);
        if (row > 0)
        {
            Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
            if (cache != null)
            {
                cache.evict(oldDict.getDictType());
                cache.evict(dict.getDictType());
            }
        }
        return row;
    }

    /**
     * 校验字典类型称是否唯一
     * 
     * @param dict 字典类型
     * @return 结果
     */
    @Override
    public boolean checkDictTypeUnique(SysDictType dict)
    {
        long dictId = MyStringUtils.isNull(dict.getDictId()) ? -1L : dict.getDictId();
        SysDictType dictType = dictTypeMapper.checkDictTypeUnique(dict.getDictType());
        if (MyStringUtils.isNotNull(dictType) && dictType.getDictId() != dictId)
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }
}
