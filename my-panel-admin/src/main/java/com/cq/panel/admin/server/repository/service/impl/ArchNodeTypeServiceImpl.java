package com.cq.panel.admin.server.repository.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchNodeTypeMapper;
import com.cq.panel.admin.server.repository.domain.ArchNodeType;
import com.cq.panel.admin.server.repository.service.IArchNodeTypeService;

/**
 * 节点类型 服务实现
 * 
 * @author cq
 */
@Service
public class ArchNodeTypeServiceImpl implements IArchNodeTypeService
{
    @Autowired
    private ArchNodeTypeMapper archNodeTypeMapper;

    @Override
    public List<ArchNodeType> selectArchNodeTypeList(ArchNodeType archNodeType)
    {
        return archNodeTypeMapper.selectArchNodeTypeList(archNodeType);
    }

    @Override
    public List<ArchNodeType> selectArchNodeTypeAll()
    {
        return archNodeTypeMapper.selectArchNodeTypeAll();
    }

    @Override
    public ArchNodeType selectArchNodeTypeById(Long id)
    {
        return archNodeTypeMapper.selectArchNodeTypeById(id);
    }

    @Override
    public ArchNodeType selectArchNodeTypeByCode(String typeCode)
    {
        return archNodeTypeMapper.selectArchNodeTypeByCode(typeCode);
    }

    @Override
    public int insertArchNodeType(ArchNodeType archNodeType)
    {
        return archNodeTypeMapper.insertArchNodeType(archNodeType);
    }

    @Override
    public int updateArchNodeType(ArchNodeType archNodeType)
    {
        return archNodeTypeMapper.updateArchNodeType(archNodeType);
    }

    @Override
    public int deleteArchNodeTypeById(Long id)
    {
        return archNodeTypeMapper.deleteArchNodeTypeById(id);
    }

    @Override
    public int deleteArchNodeTypeByIds(Long[] ids)
    {
        return archNodeTypeMapper.deleteArchNodeTypeByIds(ids);
    }

    @Override
    public boolean checkTypeCodeUnique(ArchNodeType archNodeType)
    {
        ArchNodeType unique = archNodeTypeMapper.checkTypeCodeUnique(archNodeType);
        return unique == null || unique.getId().equals(archNodeType.getId());
    }
}