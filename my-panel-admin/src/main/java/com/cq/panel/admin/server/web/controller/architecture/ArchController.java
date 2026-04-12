package com.cq.panel.admin.server.web.controller.architecture;

import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.repository.domain.ArchDiagram;
import com.cq.panel.admin.server.repository.domain.ArchNode;
import com.cq.panel.admin.server.repository.domain.ArchEdge;
import com.cq.panel.admin.server.repository.domain.ArchNodeType;
import com.cq.panel.admin.server.repository.service.IArchDiagramService;
import com.cq.panel.admin.server.repository.service.IArchNodeService;
import com.cq.panel.admin.server.repository.service.IArchEdgeService;
import com.cq.panel.admin.server.repository.service.IArchNodeTypeService;
import com.cq.panel.admin.server.web.converter.architecture.ArchConverter;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchNodeDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchEdgeDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchNodeVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchEdgeVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 架构编排 Controller - 实时持久化架构图操作
 * 
 * @author cq
 */
@Slf4j
@RestController
@RequestMapping("/arch")
@RequiredArgsConstructor
@Tag(name = "架构编排", description = "架构图实时操作相关接口")
public class ArchController extends BaseController {
    
    private final IArchDiagramService diagramService;
    private final IArchNodeService nodeService;
    private final IArchEdgeService edgeService;
    private final IArchNodeTypeService nodeTypeService;
    private final ArchConverter converter;
    private final ObjectMapper objectMapper;

    /**
     * 查询架构图列表
     */
    @Operation(summary = "查询架构图列表", description = "分页查询架构图列表")
    @GetMapping("/diagram/list")
    public Result<PageInfo<ArchDiagramVO>> listDiagram(
            @Parameter(description = "查询参数") ArchDiagramDTO dto) {
        startPage();
        ArchDiagram diagram = converter.toEntity(dto);
        List<ArchDiagram> list = diagramService.selectArchDiagramList(diagram);
        PageInfo<ArchDiagram> entityPageInfo = new PageInfo<>(list);
        List<ArchDiagramVO> voList = converter.toDiagramVOList(list);
        PageInfo<ArchDiagramVO> voPageInfo = new PageInfo<>(voList);
        voPageInfo.setTotal(entityPageInfo.getTotal());
        return Result.success(voPageInfo);
    }

    /**
     * 获取架构图详细信息
     */
    @Operation(summary = "获取架构图详细信息", description = "根据ID获取架构图详细信息")
    @GetMapping("/diagram/{id}")
    public Result<ArchDiagramVO> getDiagram(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long id) {
        return Result.success(converter.toVO(diagramService.selectArchDiagramById(id)));
    }

    /**
     * 新增架构图
     */
    @Operation(summary = "新增架构图", description = "创建新的架构图")
    @PostMapping("/diagram")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDiagramVO> createDiagram(
            @Valid @RequestBody ArchDiagramDTO dto) {
        ArchDiagram diagram = converter.toEntity(dto);
        diagramService.insertArchDiagram(diagram);
        log.info("架构图已创建: id={}, name={}", diagram.getId(), diagram.getDiagramName());
        return Result.success(converter.toVO(diagram));
    }

    /**
     * 修改架构图
     */
    @Operation(summary = "修改架构图", description = "修改架构图基本信息")
    @PutMapping("/diagram")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDiagramVO> updateDiagram(
            @Valid @RequestBody ArchDiagramDTO dto) {
        ArchDiagram diagram = converter.toEntity(dto);
        diagramService.updateArchDiagram(diagram);
        log.info("架构图已更新: id={}", diagram.getId());
        return Result.success(converter.toVO(diagramService.selectArchDiagramById(diagram.getId())));
    }

    /**
     * 发布架构图
     */
    @Operation(summary = "发布架构图", description = "发布架构图")
    @PostMapping("/diagram/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchDiagramVO> publishDiagram(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long id) {
        ArchDiagram diagram = new ArchDiagram();
        diagram.setId(id);
        diagramService.publishArchDiagram(diagram);
        log.info("架构图已发布: id={}", id);
        return Result.success(converter.toVO(diagramService.selectArchDiagramById(id)));
    }

    /**
     * 删除架构图
     */
    @Operation(summary = "删除架构图", description = "批量删除架构图")
    @DeleteMapping("/diagram/{ids}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteDiagram(
            @Parameter(description = "架构图ID数组", required = true) @PathVariable Long[] ids) {
        diagramService.deleteArchDiagramByIds(ids);
        log.info("架构图已删除: ids={}", Arrays.toString(ids));
        return Result.success();
    }

    /**
     * 加载架构图完整数据（包含所有节点和边缘）
     */
    @Operation(summary = "加载架构图完整数据", description = "加载架构图包含所有节点和边缘")
    @GetMapping("/diagram/{id}/data")
    public Result<Map<String, Object>> loadDiagramData(
            @Parameter(description = "架构图ID", required = true) @PathVariable Long id) {
        List<ArchNode> nodes = nodeService.selectNodesByDiagramId(id);
        List<ArchEdge> edges = edgeService.selectEdgesByDiagramId(id);
        Map<String, Object> data = new HashMap<>();
        data.put("nodes", converter.toNodeVOList(nodes));
        data.put("edges", converter.toEdgeVOList(edges));
        log.info("加载架构图数据: id={}, nodes={}, edges={}", id, nodes.size(), edges.size());
        return Result.success(data);
    }

    // ==================== 节点实时操作（核心功能） ====================

    /**
     * 新增节点 - 实时创建新节点并持久化
     */
    @Operation(summary = "新增节点", description = "实时创建新节点并持久化")
    @PostMapping("/node")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchNodeVO> createNode(@Valid @RequestBody ArchNodeDTO dto) {
        processNodeDTO(dto);
        ArchNode node = converter.toEntity(dto);
        nodeService.insertArchNode(node);
        log.info("节点已创建并持久化: nodeId={}, diagramId={}", node.getId(), node.getDiagramId());
        return Result.success(converter.toVO(node));
    }

    /**
     * 批量新增节点 - 实时批量创建节点并持久化
     */
    @Operation(summary = "批量新增节点", description = "实时批量创建节点并持久化")
    @PostMapping("/node/batch")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<ArchNodeVO>> batchCreateNode(@RequestBody List<ArchNodeDTO> dtoList) {
        dtoList.forEach(this::processNodeDTO);
        List<ArchNode> nodes = converter.toNodeEntityList(dtoList);
        nodeService.batchInsertArchNode(nodes);
        log.info("批量节点已创建并持久化: count={}", nodes.size());
        return Result.success(converter.toNodeVOList(nodes));
    }

    /**
     * 修改节点 - 实时修改节点信息并持久化
     */
    @Operation(summary = "修改节点", description = "实时修改节点信息并持久化")
    @PutMapping("/node")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchNodeVO> updateNode(@Valid @RequestBody ArchNodeDTO dto) {
        processNodeDTO(dto);
        ArchNode node = converter.toEntity(dto);
        nodeService.updateArchNode(node);
        log.info("节点已更新并持久化: nodeId={}", node.getId());
        return Result.success(converter.toVO(nodeService.selectArchNodeById(node.getId())));
    }

    /**
     * 拖拽节点 - 实时保存节点拖拽位置并持久化
     */
    @Operation(summary = "拖拽节点", description = "实时保存节点拖拽位置并持久化")
    @PutMapping("/node/position")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<ArchNodeVO>> updateNodePosition(@RequestBody List<ArchNodeDTO> dtoList) {
        List<ArchNode> nodes = converter.toNodeEntityList(dtoList);
        nodeService.batchUpdateArchNodePosition(nodes);
        log.info("节点位置已更新并持久化: count={}", nodes.size());
        return Result.success(converter.toNodeVOList(nodes));
    }

    /**
     * 删除节点 - 实时删除节点及其关联的边缘并持久化
     */
    @Operation(summary = "删除节点", description = "实时删除节点及其关联的边缘并持久化")
    @DeleteMapping("/node/{ids}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteNode(
            @Parameter(description = "节点ID数组", required = true) @PathVariable Long[] ids) {
        for (Long id : ids) {
            // 删除节点时，同时删除关联的边缘
            List<ArchEdge> allEdges = edgeService.selectArchEdgeList(new ArchEdge());
            List<Long> edgeIdsToDelete = allEdges.stream()
                    .filter(e -> e.getSourceNodeId().equals(id) || e.getTargetNodeId().equals(id))
                    .map(ArchEdge::getId)
                    .toList();
            if (!edgeIdsToDelete.isEmpty()) {
                edgeService.deleteArchEdgeByIds(edgeIdsToDelete.toArray(new Long[0]));
                log.info("已删除关联边缘: count={}", edgeIdsToDelete.size());
            }
            nodeService.deleteArchNodeById(id);
        }
        log.info("节点已删除并持久化: ids={}", Arrays.toString(ids));
        return Result.success();
    }

    // ==================== 边缘实时操作（核心功能） ====================

    /**
     * 新增边缘 - 实时创建新边缘并持久化
     */
    @Operation(summary = "新增边缘", description = "实时创建新边缘并持久化")
    @PostMapping("/edge")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchEdgeVO> createEdge(@Valid @RequestBody ArchEdgeDTO dto) {
        processEdgeDTO(dto);
        ArchEdge edge = converter.toEntity(dto);
        edgeService.insertArchEdge(edge);
        log.info("边缘已创建并持久化: edgeId={}, diagramId={}", edge.getId(), edge.getDiagramId());
        return Result.success(converter.toVO(edge));
    }

    /**
     * 批量新增边缘 - 实时批量创建边缘并持久化
     */
    @Operation(summary = "批量新增边缘", description = "实时批量创建边缘并持久化")
    @PostMapping("/edge/batch")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<ArchEdgeVO>> batchCreateEdge(@RequestBody List<ArchEdgeDTO> dtoList) {
        dtoList.forEach(this::processEdgeDTO);
        List<ArchEdge> edges = converter.toEdgeEntityList(dtoList);
        edgeService.batchInsertArchEdge(edges);
        log.info("批量边缘已创建并持久化: count={}", edges.size());
        return Result.success(converter.toEdgeVOList(edges));
    }

    /**
     * 修改边缘 - 实时修改边缘信息并持久化
     */
    @Operation(summary = "修改边缘", description = "实时修改边缘信息并持久化")
    @PutMapping("/edge")
    @Transactional(rollbackFor = Exception.class)
    public Result<ArchEdgeVO> updateEdge(@Valid @RequestBody ArchEdgeDTO dto) {
        processEdgeDTO(dto);
        ArchEdge edge = converter.toEntity(dto);
        edgeService.updateArchEdge(edge);
        log.info("边缘已更新并持久化: edgeId={}", edge.getId());
        return Result.success(converter.toVO(edgeService.selectArchEdgeById(edge.getId())));
    }

    /**
     * 删除边缘 - 实时删除边缘并持久化
     */
    @Operation(summary = "删除边缘", description = "实时删除边缘并持久化")
    @DeleteMapping("/edge/{ids}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteEdge(
            @Parameter(description = "边缘ID数组", required = true) @PathVariable Long[] ids) {
        edgeService.deleteArchEdgeByIds(ids);
        log.info("边缘已删除并持久化: ids={}", Arrays.toString(ids));
        return Result.success();
    }

    // ==================== 批量操作（事务保证一致性） ====================

    /**
     * 批量保存架构图数据 - 在事务中批量保存节点和边缘，确保一致性
     */
    @Operation(summary = "批量保存架构图数据", description = "在事务中批量保存节点和边缘，确保一致性")
    @PostMapping("/diagram/{id}/batch-save")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchSaveDiagramData(
            @PathVariable Long id,
            @RequestBody Map<String, List<ArchNodeDTO>> data) {
        log.info("开始批量保存架构图数据: diagramId={}", id);
        
        // 保存节点
        List<ArchNodeDTO> nodeList = data.get("nodes");
        if (nodeList != null && !nodeList.isEmpty()) {
            List<ArchNode> nodes = converter.toNodeEntityList(nodeList);
            nodeService.batchInsertArchNode(nodes);
        }
        
        // 返回最新数据
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", converter.toNodeVOList(nodeService.selectNodesByDiagramId(id)));
        result.put("edges", converter.toEdgeVOList(edgeService.selectEdgesByDiagramId(id)));
        
        log.info("批量保存完成: diagramId={}", id);
        return Result.success(result);
    }

    /**
     * 全量更新架构图 - 替换架构图所有节点和边缘
     */
    @Operation(summary = "全量更新架构图", description = "替换架构图所有节点和边缘")
    @PutMapping("/diagram/{id}/replace")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> replaceDiagramData(
            @PathVariable Long id,
            @RequestBody Map<String, Object> data) {
        log.info("开始全量更新架构图: diagramId={}", id);
        
        // 1. 清空现有边缘
        List<ArchEdge> existingEdges = edgeService.selectEdgesByDiagramId(id);
        if (!existingEdges.isEmpty()) {
            Long[] edgeIds = existingEdges.stream().map(ArchEdge::getId).toArray(Long[]::new);
            edgeService.deleteArchEdgeByIds(edgeIds);
        }
        
        // 2. 清空现有节点
        List<ArchNode> existingNodes = nodeService.selectNodesByDiagramId(id);
        if (!existingNodes.isEmpty()) {
            Long[] nodeIds = existingNodes.stream().map(ArchNode::getId).toArray(Long[]::new);
            nodeService.deleteArchNodeByIds(nodeIds);
        }
        
        // 3. 插入新节点并建立 ID 映射
        Map<String, Long> nodeIdMap = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodeDataList = (List<Map<String, Object>>) data.get("nodes");
        if (nodeDataList != null && !nodeDataList.isEmpty()) {
            for (Map<String, Object> nodeMap : nodeDataList) {
                // 记录前端使用的ID
                Object frontIdObj = nodeMap.get("frontId");
                String frontId = frontIdObj != null ? String.valueOf(frontIdObj) : null;
                
                ArchNodeDTO nodeDto = objectMapper.convertValue(nodeMap, ArchNodeDTO.class);
                processNodeDTO(nodeDto);
                ArchNode node = converter.toEntity(nodeDto);
                nodeService.insertArchNode(node); // 逐个插入以获取生成的 ID
                
                if (frontId != null) {
                    nodeIdMap.put(frontId, node.getId());
                }
            }
        }
        
        // 4. 插入新边缘
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edgeDataList = (List<Map<String, Object>>) data.get("edges");
        if (edgeDataList != null && !edgeDataList.isEmpty()) {
            for (Map<String, Object> edgeMap : edgeDataList) {
                Object sFrontIdObj = edgeMap.get("sourceFrontId");
                Object tFrontIdObj = edgeMap.get("targetFrontId");
                String sourceFrontId = sFrontIdObj != null ? String.valueOf(sFrontIdObj) : null;
                String targetFrontId = tFrontIdObj != null ? String.valueOf(tFrontIdObj) : null;
                
                ArchEdgeDTO edgeDto = objectMapper.convertValue(edgeMap, ArchEdgeDTO.class);
                
                // 使用映射后的 ID
                if (sourceFrontId != null && nodeIdMap.containsKey(sourceFrontId)) {
                    edgeDto.setSourceNodeId(nodeIdMap.get(sourceFrontId));
                }
                if (targetFrontId != null && nodeIdMap.containsKey(targetFrontId)) {
                    edgeDto.setTargetNodeId(nodeIdMap.get(targetFrontId));
                }
                
                if (edgeDto.getSourceNodeId() == null || edgeDto.getTargetNodeId() == null) {
                    log.warn("跳过无效边缘：diagramId={}, sourceFrontId={}, targetFrontId={}, sourceNodeId={}, targetNodeId={}",
                            id,
                            sourceFrontId,
                            targetFrontId,
                            edgeDto.getSourceNodeId(),
                            edgeDto.getTargetNodeId());
                    continue;
                }

                processEdgeDTO(edgeDto);
                ArchEdge edge = converter.toEntity(edgeDto);
                edgeService.insertArchEdge(edge);
            }
        }
        
        // 返回最新数据
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", converter.toNodeVOList(nodeService.selectNodesByDiagramId(id)));
        result.put("edges", converter.toEdgeVOList(edgeService.selectEdgesByDiagramId(id)));
        
        log.info("全量更新完成: diagramId={}", id);
        return Result.success(result);
    }

    /**
     * 处理节点DTO，将前端传参映射到后端字段
     */
    private void processNodeDTO(ArchNodeDTO dto) {
        // 1. 处理位置别名
        if (dto.getPositionX() != null) dto.setXPosition(dto.getPositionX());
        if (dto.getPositionY() != null) dto.setYPosition(dto.getPositionY());

        // 2. 处理节点类型编码 -> ID
        if (dto.getNodeType() != null) {
            ArchNodeType type = nodeTypeService.selectArchNodeTypeByCode(dto.getNodeType());
            if (type != null) {
                dto.setNodeTypeId(type.getId());
            }
        }

        // 3. 处理节点属性 -> JSON
        if (dto.getIp() != null || dto.getPort() != null || dto.getConfig() != null) {
            Map<String, Object> props = new LinkedHashMap<>();
            if (dto.getIp() != null) props.put("ip", dto.getIp());
            if (dto.getPort() != null) props.put("port", dto.getPort());
            if (dto.getConfig() != null) props.put("config", dto.getConfig());
            try {
                dto.setNodeProperties(objectMapper.writeValueAsString(props));
            } catch (JsonProcessingException e) {
                log.error("序列化节点属性失败", e);
            }
        }
    }

    /**
     * 处理边缘DTO，将前端传参映射到后端字段
     */
    private void processEdgeDTO(ArchEdgeDTO dto) {
        // 1. 处理锚点别名
        if (dto.getSourceHandle() != null) dto.setSourceAnchor(dto.getSourceHandle());
        if (dto.getTargetHandle() != null) dto.setTargetAnchor(dto.getTargetHandle());

        // 2. 处理连线样式 -> JSON
        boolean hasEdgeStyle = dto.getEdgeStyle() != null && !dto.getEdgeStyle().isBlank();
        boolean hasStyleAlias = dto.getColor() != null || dto.getWeight() != null;
        if (hasEdgeStyle || hasStyleAlias) {
            Map<String, Object> style = new LinkedHashMap<>();

            if (hasEdgeStyle) {
                try {
                    Object styleObj = objectMapper.readValue(dto.getEdgeStyle(), Object.class);
                    if (styleObj instanceof Map<?, ?> styleMap) {
                        for (Map.Entry<?, ?> entry : styleMap.entrySet()) {
                            if (entry.getKey() != null) {
                                style.put(String.valueOf(entry.getKey()), entry.getValue());
                            }
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.error("解析连线样式失败", e);
                }
            }

            if (dto.getColor() != null) style.put("stroke", dto.getColor());
            if (dto.getWeight() != null) style.put("strokeWidth", dto.getWeight());

            try {
                dto.setEdgeStyle(objectMapper.writeValueAsString(style));
            } catch (JsonProcessingException e) {
                log.error("序列化连线样式失败", e);
            }
        }

        // 3. 处理连线属性 -> JSON
        if (dto.getPoints() != null) {
            Map<String, Object> props = new LinkedHashMap<>();
            try {
                // 如果是有效的 JSON 数组，解析后存入，避免双重序列化
                Object pointsObj = objectMapper.readValue(dto.getPoints(), Object.class);
                props.put("points", pointsObj);
            } catch (JsonProcessingException e) {
                // 如果解析失败，原样存入字符串
                props.put("points", dto.getPoints());
            }
            try {
                dto.setEdgeProperties(objectMapper.writeValueAsString(props));
            } catch (JsonProcessingException e) {
                log.error("序列化连线属性失败", e);
            }
        }
    }
}
