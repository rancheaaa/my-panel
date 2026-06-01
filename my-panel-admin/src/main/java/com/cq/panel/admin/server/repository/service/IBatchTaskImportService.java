package com.cq.panel.admin.server.repository.service;

import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportBatchVO;
import com.cq.panel.admin.server.web.domain.vo.batch.TaskImportPreviewVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IBatchTaskImportService {

    TaskImportPreviewVO uploadAndValidate(MultipartFile file, String userId);

    TaskImportPreviewVO preview(String batchNo);

    int commitImport(String batchNo, String mode);

    int rollbackImport(String batchNo);

    int batchStart(String batchNo);

    int batchPause(String batchNo);

    List<TaskImportBatchVO> listBatches(String searchKeyword);

    void deleteBatch(String batchNo);
}
