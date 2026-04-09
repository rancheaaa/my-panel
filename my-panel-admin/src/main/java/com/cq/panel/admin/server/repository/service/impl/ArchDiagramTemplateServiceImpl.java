package com.cq.panel.admin.server.repository.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cq.panel.admin.server.repository.mapper.ArchDiagramTemplateMapper;
import com.cq.panel.admin.server.repository.domain.ArchDiagramTemplate;
import com.cq.panel.admin.server.repository.service.IArchDiagramTemplateService;

/**
 * 架构图模板 服务实现
 * 
 * @author cq
 */
@Service
public class ArchDiagramTemplateServiceImpl implements IArchDiagramTemplateService
{
    @Autowired
    private ArchDiagramTemplateMapper archDiagramTemplateMapper;

    @Override
    public List<ArchDiagramTemplate> selectArchDiagramTemplateList(ArchDiagramTemplate archDiagramTemplate)
    {
        return archDiagramTemplateMapper.selectArchDiagramTemplateList(archDiagramTemplate);
    }

    @Override
    public List<ArchDiagramTemplate> selectArchDiagramTemplateAll()
    {
        return archDiagramTemplateMapper.selectArchDiagramTemplateAll();
    }

    @Override
    public ArchDiagramTemplate selectArchDiagramTemplateById(Long id)
    {
        return archDiagramTemplateMapper.selectArchDiagramTemplateById(id);
    }

    @Override
    public int insertArchDiagramTemplate(ArchDiagramTemplate archDiagramTemplate)
    {
        return archDiagramTemplateMapper.insertArchDiagramTemplate(archDiagramTemplate);
    }

    @Override
    public int updateArchDiagramTemplate(ArchDiagramTemplate archDiagramTemplate)
    {
        return archDiagramTemplateMapper.updateArchDiagramTemplate(archDiagramTemplate);
    }

    @Override
    public int deleteArchDiagramTemplateById(Long id)
    {
        return archDiagramTemplateMapper.deleteArchDiagramTemplateById(id);
    }

    @Override
    public int deleteArchDiagramTemplateByIds(Long[] ids)
    {
        return archDiagramTemplateMapper.deleteArchDiagramTemplateByIds(ids);
    }

    @Override
    public int useTemplate(Long templateId)
    {
        return archDiagramTemplateMapper.incrementUseCount(templateId);
    }

    @Override
    public int rateTemplate(Long templateId, Double rating)
    {
        ArchDiagramTemplate template = archDiagramTemplateMapper.selectArchDiagramTemplateById(templateId);
        if (template == null)
        {
            return 0;
        }
        int newUseCount = template.getUseCount() + 1;
        BigDecimal currentTotal = template.getRating().multiply(BigDecimal.valueOf(template.getUseCount()));
        BigDecimal newRating = currentTotal.add(BigDecimal.valueOf(rating)).divide(BigDecimal.valueOf(newUseCount), 2, RoundingMode.HALF_UP);
        template.setUseCount(newUseCount);
        template.setRating(newRating);
        return archDiagramTemplateMapper.updateArchDiagramTemplate(template);
    }
}