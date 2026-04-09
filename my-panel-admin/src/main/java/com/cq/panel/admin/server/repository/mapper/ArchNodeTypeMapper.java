package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchNodeType;
import java.util.List;

/**
 * 节点类型 数据层
 * 
 * @author cq
 */
public interface ArchNodeTypeMapper
{
    /**
     * 查询节点类型集合
     * 
     * @param archNodeType 节点类型信息
     * @return 节点类型集合
     */
    public List<ArchNodeType> selectArchNodeTypeList(ArchNodeType archNodeType);

    /**
     * 查询所有节点类型
     * 
     * @return 节点类型列表
     */
    public List<ArchNodeType> selectArchNodeTypeAll();

    /**
     * 通过类型ID查询节点类型信息
     * 
     * @param id 类型ID
     * @return 节点类型对象信息
     */
    public ArchNodeType selectArchNodeTypeById(Long id);

    /**
     * 通过类型编码查询节点类型信息
     * 
     * @param typeCode 类型编码
     * @return 节点类型对象信息
     */
    public ArchNodeType selectArchNodeTypeByCode(String typeCode);

    /**
     * 通过类型ID删除节点类型信息
     * 
     * @param id 类型ID
     * @return 结果
     */
    public int deleteArchNodeTypeById(Long id);

    /**
     * 批量删除节点类型信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteArchNodeTypeByIds(Long[] ids);

    /**
     * 修改节点类型信息
     * 
     * @param archNodeType 节点类型信息
     * @return 结果
     */
    public int updateArchNodeType(ArchNodeType archNodeType);

    /**
     * 新增节点类型信息
     * 
     * @param archNodeType 节点类型信息
     * @return 结果
     */
    public int insertArchNodeType(ArchNodeType archNodeType);

    /**
     * 校验类型编码是否唯一
     * 
     * @param archNodeType 节点类型信息
     * @return 结果
     */
    public ArchNodeType checkTypeCodeUnique(ArchNodeType archNodeType);
}