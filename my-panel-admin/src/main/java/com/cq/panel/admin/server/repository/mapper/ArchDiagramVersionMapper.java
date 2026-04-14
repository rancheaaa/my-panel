package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.ArchDiagramVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 架构图版本主表Mapper接口
 *
 * @author
 */
@Mapper
public interface ArchDiagramVersionMapper {
    /**
     * 查询架构图版本主表
     *
     * @param id 架构图版本主表主键
     * @return 架构图版本主表
     */
    public ArchDiagramVersion selectArchDiagramVersionById(Long id);

    /**
     * 查询架构图版本主表列表
     *
     * @param archDiagramVersion 架构图版本主表
     * @return 架构图版本主表集合
     */
    public List<ArchDiagramVersion> selectArchDiagramVersionList(ArchDiagramVersion archDiagramVersion);

    /**
     * 按架构图ID查询版本列表
     *
     * @param diagramId 架构图ID
     * @return 版本列表
     */
    public List<ArchDiagramVersion> selectVersionsByDiagramId(Long diagramId);

    /**
     * 新增架构图版本主表
     *
     * @param archDiagramVersion 架构图版本主表
     * @return 结果
     */
    public int insertArchDiagramVersion(ArchDiagramVersion archDiagramVersion);

    /**
     * 修改架构图版本主表
     *
     * @param archDiagramVersion 架构图版本主表
     * @return 结果
     */
    public int updateArchDiagramVersion(ArchDiagramVersion archDiagramVersion);

    /**
     * 删除架构图版本主表
     *
     * @param id 架构图版本主表主键
     * @return 结果
     */
    public int deleteArchDiagramVersionById(Long id);

    /**
     * 批量删除架构图版本主表
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteArchDiagramVersionByIds(Long[] ids);

    /**
     * 更新架构图的当前版本标记
     *
     * @param diagramId 架构图ID
     * @param isCurrent 是否当前版本
     * @return 结果
     */
    public int updateCurrentVersionFlag(@Param("diagramId") Long diagramId, @Param("isCurrent") String isCurrent);

    /**
     * 增加版本的恢复次数
     *
     * @param id 版本ID
     * @return 结果
     */
    public int incrementRestoreCount(Long id);
}