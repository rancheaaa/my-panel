package com.cq.panel.admin.server.web.converter.architecture;

import com.cq.panel.admin.server.repository.domain.ArchDiagram;
import com.cq.panel.admin.server.repository.domain.ArchNode;
import com.cq.panel.admin.server.repository.domain.ArchEdge;
import com.cq.panel.admin.server.repository.domain.ArchNodeType;
import com.cq.panel.admin.server.repository.domain.ArchDiagramHistory;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTemplate;
import com.cq.panel.admin.server.repository.domain.ArchNodeGroup;
import com.cq.panel.admin.server.repository.domain.ArchDiagramShare;
import com.cq.panel.admin.server.repository.domain.ArchDiagramComment;
import com.cq.panel.admin.server.repository.domain.ArchDiagramFavorite;
import com.cq.panel.admin.server.repository.domain.ArchDiagramLog;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTag;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchNodeDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchEdgeDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchNodeTypeDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramHistoryDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramTemplateDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchNodeGroupDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramShareDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramCommentDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramFavoriteDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramLogDTO;
import com.cq.panel.admin.server.web.domain.dto.architecture.ArchDiagramTagDTO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchNodeVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchEdgeVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchNodeTypeVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramHistoryVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramTemplateVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchNodeGroupVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramShareVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramCommentVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramFavoriteVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramLogVO;
import com.cq.panel.admin.server.web.domain.vo.architecture.ArchDiagramTagVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ArchConverter {
    ArchDiagram toEntity(ArchDiagramDTO dto);
    ArchDiagramVO toVO(ArchDiagram entity);
    List<ArchDiagram> toDiagramEntityList(List<ArchDiagramDTO> dtoList);
    List<ArchDiagramVO> toDiagramVOList(List<ArchDiagram> entityList);

    ArchNode toEntity(ArchNodeDTO dto);
    ArchNodeVO toVO(ArchNode entity);
    List<ArchNode> toNodeEntityList(List<ArchNodeDTO> dtoList);
    List<ArchNodeVO> toNodeVOList(List<ArchNode> entityList);

    ArchEdge toEntity(ArchEdgeDTO dto);
    ArchEdgeVO toVO(ArchEdge entity);
    List<ArchEdge> toEdgeEntityList(List<ArchEdgeDTO> dtoList);
    List<ArchEdgeVO> toEdgeVOList(List<ArchEdge> entityList);

    ArchNodeType toEntity(ArchNodeTypeDTO dto);
    ArchNodeTypeVO toNodeTypeVO(ArchNodeType entity);
    List<ArchNodeType> toNodeTypeEntityList(List<ArchNodeTypeDTO> dtoList);
    List<ArchNodeTypeVO> toNodeTypeVOList(List<ArchNodeType> entityList);

    ArchDiagramHistory toEntity(ArchDiagramHistoryDTO dto);
    ArchDiagramHistoryVO toHistoryVO(ArchDiagramHistory entity);
    List<ArchDiagramHistory> toHistoryEntityList(List<ArchDiagramHistoryDTO> dtoList);
    List<ArchDiagramHistoryVO> toHistoryVOList(List<ArchDiagramHistory> entityList);

    ArchDiagramTemplate toEntity(ArchDiagramTemplateDTO dto);
    ArchDiagramTemplateVO toTemplateVO(ArchDiagramTemplate entity);
    List<ArchDiagramTemplate> toTemplateEntityList(List<ArchDiagramTemplateDTO> dtoList);
    List<ArchDiagramTemplateVO> toTemplateVOList(List<ArchDiagramTemplate> entityList);

    ArchNodeGroup toEntity(ArchNodeGroupDTO dto);
    ArchNodeGroupVO toNodeGroupVO(ArchNodeGroup entity);
    List<ArchNodeGroup> toNodeGroupEntityList(List<ArchNodeGroupDTO> dtoList);
    List<ArchNodeGroupVO> toNodeGroupVOList(List<ArchNodeGroup> entityList);

    ArchDiagramShare toEntity(ArchDiagramShareDTO dto);
    ArchDiagramShareVO toShareVO(ArchDiagramShare entity);
    List<ArchDiagramShare> toShareEntityList(List<ArchDiagramShareDTO> dtoList);
    List<ArchDiagramShareVO> toShareVOList(List<ArchDiagramShare> entityList);

    ArchDiagramComment toEntity(ArchDiagramCommentDTO dto);
    ArchDiagramCommentVO toCommentVO(ArchDiagramComment entity);
    List<ArchDiagramComment> toCommentEntityList(List<ArchDiagramCommentDTO> dtoList);
    List<ArchDiagramCommentVO> toCommentVOList(List<ArchDiagramComment> entityList);

    ArchDiagramFavorite toEntity(ArchDiagramFavoriteDTO dto);
    ArchDiagramFavoriteVO toFavoriteVO(ArchDiagramFavorite entity);
    List<ArchDiagramFavorite> toFavoriteEntityList(List<ArchDiagramFavoriteDTO> dtoList);
    List<ArchDiagramFavoriteVO> toFavoriteVOList(List<ArchDiagramFavorite> entityList);

    ArchDiagramLog toEntity(ArchDiagramLogDTO dto);
    ArchDiagramLogVO toLogVO(ArchDiagramLog entity);
    List<ArchDiagramLog> toLogEntityList(List<ArchDiagramLogDTO> dtoList);
    List<ArchDiagramLogVO> toLogVOList(List<ArchDiagramLog> entityList);

    ArchDiagramTag toEntity(ArchDiagramTagDTO dto);
    ArchDiagramTagVO toTagVO(ArchDiagramTag entity);
    List<ArchDiagramTag> toTagEntityList(List<ArchDiagramTagDTO> dtoList);
    List<ArchDiagramTagVO> toTagVOList(List<ArchDiagramTag> entityList);
}