package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.repository.domain.SysDictData;
import com.cq.panel.admin.server.repository.mapper.SysDictDataMapper;
import com.cq.panel.admin.server.repository.service.ISysDictDataService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 字典 业务层处理
 * 
 * @author cq
 */
@Service
public class SysDictDataServiceImpl implements ISysDictDataService
{
    private final SysDictDataMapper dictDataMapper;

    private final CacheManager cacheManager;

    public SysDictDataServiceImpl(SysDictDataMapper dictDataMapper, CacheManager cacheManager) {
        this.dictDataMapper = dictDataMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * 根据条件分页查询字典数据
     * 
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectDictDataList(SysDictData dictData)
    {
        return dictDataMapper.selectDictDataList(dictData);
    }

    @Override
    public List<SysDictData> selectDictDataByType(String dictType)
    {
        return dictDataMapper.selectDictDataByType(dictType);
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     * 
     * @param dictType 字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictType, String dictValue)
    {
        return dictDataMapper.selectDictLabel(dictType, dictValue);
    }

    /**
     * 根据字典数据ID查询信息
     * 
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public SysDictData selectDictDataById(Long dictCode)
    {
        return dictDataMapper.selectDictDataById(dictCode);
    }

    /**
     * 批量删除字典数据信息
     * 
     * @param dictCodes 需要删除的字典数据ID
     */
    @Override
    public void deleteDictDataByIds(Long[] dictCodes)
    {
        for (Long dictCode : dictCodes)
        {
            SysDictData data = selectDictDataById(dictCode);
            dictDataMapper.deleteDictDataById(dictCode);
            Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
            if (cache != null)
            {
                cache.evict(data.getDictType());
            }
        }
    }

    /**
     * 新增保存字典数据信息
     * 
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    public int insertDictData(SysDictData data)
    {
        int row = dictDataMapper.insertDictData(data);
        if (row > 0)
        {
            Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
            if (cache != null)
            {
                cache.evict(data.getDictType());
            }
        }
        return row;
    }

    /**
     * 修改保存字典数据信息
     * 
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    public int updateDictData(SysDictData data)
    {
        int row = dictDataMapper.updateDictData(data);
        if (row > 0)
        {
            Cache cache = cacheManager.getCache(CacheConstants.SYS_DICT_KEY);
            if (cache != null)
            {
                cache.evict(data.getDictType());
            }
        }
        return row;
    }
}
