package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.repository.domain.ArchNodeType;
import java.util.List;

/**
 * 节点类型 服务层
 * 
 * @author cq
 */
public interface IArchNodeTypeService
{
    /**
     * 查询节点类型集合
     * 
     * @param archNodeType 节点类型信息
     * @return 节点类型集合
     */
    List<ArchNodeType> selectArchNodeTypeList(ArchNodeType archNodeType);

    /**
     * 查询所有节点类型
     * 
     * @return 节点类型列表
     */
    List<ArchNodeType> selectArchNodeTypeAll();

    /**
     * 通过类型ID查询节点类型信息
     * 
     * @param id 类型ID
     * @return 节点类型对象信息
     */
    ArchNodeType selectArchNodeTypeById(Long id);

    /**
     * 通过类型编码查询节点类型信息
     * 
     * @param typeCode 类型编码
     * @return 节点类型对象信息
     */
    ArchNodeType selectArchNodeTypeByCode(String typeCode);

    /**
     * 新增节点类型信息
     * 
     * @param archNodeType 节点类型信息
     * @return 结果
     */
    int insertArchNodeType(ArchNodeType archNodeType);

    /**
     * 修改节点类型信息
     * 
     * @param archNodeType 节点类型信息
     * @return 结果
     */
    int updateArchNodeType(ArchNodeType archNodeType);

    /**
     * 删除节点类型信息
     * 
     * @param id 类型ID
     * @return 结果
     */
    int deleteArchNodeTypeById(Long id);

    /**
     * 批量删除节点类型信息
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteArchNodeTypeByIds(Long[] ids);

    /**
     * 校验类型编码是否唯一
     * 
     * @param archNodeType 节点类型信息
     * @return 结果
     */
    boolean checkTypeCodeUnique(ArchNodeType archNodeType);
}