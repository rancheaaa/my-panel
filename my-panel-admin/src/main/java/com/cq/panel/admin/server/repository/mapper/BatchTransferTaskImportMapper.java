package com.cq.panel.admin.server.repository.mapper;

import com.cq.panel.admin.server.repository.domain.BatchTransferTaskImport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BatchTransferTaskImportMapper {

    void batchInsert(@Param("list") List<BatchTransferTaskImport> list);

    List<BatchTransferTaskImport> selectByBatchNo(@Param("batchNo") String batchNo);

    List<BatchTransferTaskImport> selectByBatchNoAndImportStatus(@Param("batchNo") String batchNo,
                                                                  @Param("importStatus") String importStatus);

    void updateImportStatus(@Param("id") Long id,
                            @Param("importStatus") String importStatus,
                            @Param("importedTaskId") Long importedTaskId);

    void updateValidateStatus(@Param("id") Long id,
                              @Param("validateStatus") String validateStatus,
                              @Param("validateMessage") String validateMessage);

    List<Map<String, Object>> selectBatchList(@Param("searchKeyword") String searchKeyword);

    void deleteByBatchNo(@Param("batchNo") String batchNo);
}
