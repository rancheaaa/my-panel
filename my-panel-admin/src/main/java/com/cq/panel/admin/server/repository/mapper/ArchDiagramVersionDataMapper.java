package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramVersionData;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 架构图版本数据表Mapper接口
 *
 * @author
 */
@Mapper
public interface ArchDiagramVersionDataMapper {
    /**
     * 查询架构图版本数据表
     *
     * @param id 架构图版本数据表主键
     * @return 架构图版本数据表
     */
    public ArchDiagramVersionData selectArchDiagramVersionDataById(Long id);

    /**
     * 查询架构图版本数据表列表
     *
     * @param archDiagramVersionData 架构图版本数据表
     * @return 架构图版本数据表集合
     */
    public List<ArchDiagramVersionData> selectArchDiagramVersionDataList(ArchDiagramVersionData archDiagramVersionData);

    /**
     * 按版本ID查询数据
     *
     * @param versionId 版本ID
     * @return 数据列表
     */
    public List<ArchDiagramVersionData> selectVersionDataByVersionId(Long versionId);

    /**
     * 新增架构图版本数据表
     *
     * @param archDiagramVersionData 架构图版本数据表
     * @return 结果
     */
    public int insertArchDiagramVersionData(ArchDiagramVersionData archDiagramVersionData);

    /**
     * 修改架构图版本数据表
     *
     * @param archDiagramVersionData 架构图版本数据表
     * @return 结果
     */
    public int updateArchDiagramVersionData(ArchDiagramVersionData archDiagramVersionData);

    /**
     * 删除架构图版本数据表
     *
     * @param id 架构图版本数据表主键
     * @return 结果
     */
    public int deleteArchDiagramVersionDataById(Long id);

    /**
     * 批量删除架构图版本数据表
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteArchDiagramVersionDataByIds(Long[] ids);

    /**
     * 按版本ID删除数据
     *
     * @param versionId 版本ID
     * @return 结果
     */
    public int deleteVersionDataByVersionId(Long versionId);
}