package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.ArchDiagram;
import com.cq.panel.admin.server.repository.domain.ArchDiagramVersion;
import com.cq.panel.admin.server.repository.domain.ArchDiagramVersionData;
import com.cq.panel.admin.server.repository.domain.ArchEdge;
import com.cq.panel.admin.server.repository.domain.ArchNode;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramMapper;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramVersionDataMapper;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramVersionMapper;
import com.cq.panel.admin.server.repository.mapper.ArchEdgeMapper;
import com.cq.panel.admin.server.repository.mapper.ArchNodeMapper;
import com.cq.panel.admin.server.repository.service.IArchDiagramVersionService;
import com.cq.panel.admin.server.common.utils.SecurityUtils;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 架构图版本管理Service实现
 *
 * @author
 */
@Slf4j
@Service
public class ArchDiagramVersionServiceImpl implements IArchDiagramVersionService {

    @Autowired
    private ArchDiagramMapper archDiagramMapper;

    @Autowired
    private ArchNodeMapper archNodeMapper;

    @Autowired
    private ArchEdgeMapper archEdgeMapper;

    @Autowired
    private ArchDiagramVersionMapper archDiagramVersionMapper;

    @Autowired
    private ArchDiagramVersionDataMapper archDiagramVersionDataMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersionSnapshot(Long diagramId, String version, String versionName, String changeSummary) {
        // 验证架构图存在
        ArchDiagram diagram = archDiagramMapper.selectArchDiagramById(diagramId);
        if (diagram == null) {
            throw new RuntimeException("架构图不存在");
        }

        // 生成唯一版本号
        if (StringUtils.isEmpty(version)) {
            version = "V" + System.currentTimeMillis();
        }

        try {
            // 1. 获取当前架构图的完整数据
            List<ArchNode> nodes = archNodeMapper.selectNodesByDiagramId(diagramId);
            List<ArchEdge> edges = archEdgeMapper.selectEdgesByDiagramId(diagramId);

            // 2. 创建版本记录
            ArchDiagramVersion versionEntity = new ArchDiagramVersion();
            versionEntity.setDiagramId(diagramId);
            versionEntity.setVersion(version);
            versionEntity.setVersionName(versionName);
            versionEntity.setChangeSummary(changeSummary);
            versionEntity.setCreateBy(SecurityUtils.getUsername());
            versionEntity.setIsCurrent("1");

            // 3. 保存版本记录
            archDiagramVersionMapper.insertArchDiagramVersion(versionEntity);
            Long versionId = versionEntity.getId();

            // 4. 保存节点数据
            ArchDiagramVersionData nodeData = new ArchDiagramVersionData();
            nodeData.setVersionId(versionId);
            nodeData.setDataType("nodes");
            nodeData.setDataContent(objectMapper.writeValueAsString(nodes));
            archDiagramVersionDataMapper.insertArchDiagramVersionData(nodeData);

            // 5. 保存连线数据
            ArchDiagramVersionData edgeData = new ArchDiagramVersionData();
            edgeData.setVersionId(versionId);
            edgeData.setDataType("edges");
            edgeData.setDataContent(objectMapper.writeValueAsString(edges));
            archDiagramVersionDataMapper.insertArchDiagramVersionData(edgeData);

            // 6. 更新其他版本的当前版本标记
            archDiagramVersionMapper.updateCurrentVersionFlag(diagramId, "0");

            return versionId;
        } catch (JsonProcessingException e) {
            log.error("创建版本快照失败", e);
            throw new RuntimeException("创建版本快照失败：" + e.getMessage());
        }
    }

    @Override
    public List<ArchDiagramVersion> getVersionHistory(Long diagramId) {
        // 验证架构图存在
        ArchDiagram diagram = archDiagramMapper.selectArchDiagramById(diagramId);
        if (diagram == null) {
            throw new RuntimeException("架构图不存在");
        }

        // 查询版本历史，按创建时间倒序
        return archDiagramVersionMapper.selectVersionsByDiagramId(diagramId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long restoreVersion(Long versionId) {
        // 1. 验证版本存在
        ArchDiagramVersion version = archDiagramVersionMapper.selectArchDiagramVersionById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }

        Long diagramId = version.getDiagramId();

        try {
            // 2. 获取版本数据
            List<ArchDiagramVersionData> versionDataList = archDiagramVersionDataMapper
                    .selectVersionDataByVersionId(versionId);
            List<ArchNode> nodes = new ArrayList<>();
            List<ArchEdge> edges = new ArrayList<>();

            for (ArchDiagramVersionData data : versionDataList) {
                if ("nodes".equals(data.getDataType())) {
                    nodes = objectMapper.readValue(data.getDataContent(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ArchNode.class));
                } else if ("edges".equals(data.getDataType())) {
                    edges = objectMapper.readValue(data.getDataContent(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ArchEdge.class));
                }
            }

            // 3. 清空当前架构图的节点和连线数据
            List<ArchNode> existingNodes = archNodeMapper.selectNodesByDiagramId(diagramId);
            if (!existingNodes.isEmpty()) {
                Long[] nodeIds = existingNodes.stream().map(ArchNode::getId).toArray(Long[]::new);
                archNodeMapper.deleteArchNodeByIds(nodeIds);
            }

            List<ArchEdge> existingEdges = archEdgeMapper.selectEdgesByDiagramId(diagramId);
            if (!existingEdges.isEmpty()) {
                Long[] edgeIds = existingEdges.stream().map(ArchEdge::getId).toArray(Long[]::new);
                archEdgeMapper.deleteArchEdgeByIds(edgeIds);
            }

            // 4. 插入版本数据并建立旧ID到新ID的映射
            Map<Long, Long> nodeIdMap = new HashMap<>();
            for (ArchNode node : nodes) {
                node.setDiagramId(diagramId);
                // 保存旧ID
                Long oldNodeId = node.getId();
                // 清空ID，让数据库生成新ID
                node.setId(null);
                archNodeMapper.insertArchNode(node);
                // 记录旧ID到新ID的映射
                nodeIdMap.put(oldNodeId, node.getId());
            }

            // 5. 更新边缘中的节点ID并插入
            for (ArchEdge edge : edges) {
                edge.setDiagramId(diagramId);
                // 清空ID，让数据库生成新ID
                edge.setId(null);
                // 更新源节点ID
                if (edge.getSourceNodeId() != null) {
                    edge.setSourceNodeId(nodeIdMap.get(edge.getSourceNodeId()));
                }
                // 更新目标节点ID
                if (edge.getTargetNodeId() != null) {
                    edge.setTargetNodeId(nodeIdMap.get(edge.getTargetNodeId()));
                }
                archEdgeMapper.insertArchEdge(edge);
            }

            // 5. 更新版本标记
            archDiagramVersionMapper.updateCurrentVersionFlag(diagramId, "0");
            version.setIsCurrent("1");
            archDiagramVersionMapper.updateArchDiagramVersion(version);

            // 6. 增加恢复次数
            archDiagramVersionMapper.incrementRestoreCount(versionId);

            return diagramId;
        } catch (JsonProcessingException e) {
            log.error("恢复版本失败", e);
            throw new RuntimeException("恢复版本失败：" + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getVersionDetail(Long versionId) {
        // 验证版本存在
        ArchDiagramVersion version = archDiagramVersionMapper.selectArchDiagramVersionById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("versionInfo", version);

            // 获取版本数据
            List<ArchDiagramVersionData> versionDataList = archDiagramVersionDataMapper
                    .selectVersionDataByVersionId(versionId);

            for (ArchDiagramVersionData data : versionDataList) {
                if ("nodes".equals(data.getDataType())) {
                    List<ArchNode> nodes = objectMapper.readValue(data.getDataContent(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ArchNode.class));
                    result.put("nodes", nodes);
                } else if ("edges".equals(data.getDataType())) {
                    List<ArchEdge> edges = objectMapper.readValue(data.getDataContent(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ArchEdge.class));
                    result.put("edges", edges);
                }
            }

            return result;
        } catch (JsonProcessingException e) {
            log.error("获取版本详情失败", e);
            throw new RuntimeException("获取版本详情失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVersion(Long versionId) {
        // 验证版本存在
        ArchDiagramVersion version = archDiagramVersionMapper.selectArchDiagramVersionById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }

        // 确保不是当前版本
        if ("1".equals(version.getIsCurrent())) {
            throw new RuntimeException("不能删除当前版本");
        }

        // 删除版本数据
        archDiagramVersionDataMapper.deleteVersionDataByVersionId(versionId);

        // 删除版本记录
        int result = archDiagramVersionMapper.deleteArchDiagramVersionById(versionId);
        return result > 0;
    }

    @Override
    public ArchDiagramVersion selectVersionById(Long id) {
        return archDiagramVersionMapper.selectArchDiagramVersionById(id);
    }

    @Override
    public List<ArchDiagramVersionData> selectVersionDataByVersionId(Long versionId) {
        return archDiagramVersionDataMapper.selectVersionDataByVersionId(versionId);
    }
}