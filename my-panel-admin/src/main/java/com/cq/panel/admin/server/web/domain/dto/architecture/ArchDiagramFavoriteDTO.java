package com.cq.panel.admin.server.web.domain.dto.architecture;

import lombok.Data;
import java.io.Serializable;

/**
 * 架构图收藏 DTO
 * 
 * @author cq
 */
@Data
public class ArchDiagramFavoriteDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long diagramId;
    private Long userId;
    private String folderName;
}