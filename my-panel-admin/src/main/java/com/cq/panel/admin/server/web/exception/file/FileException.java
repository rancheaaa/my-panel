package com.cq.panel.admin.server.web.exception.file;

import com.cq.panel.admin.server.web.exception.base.BaseException;

/**
 * 文件信息异常类
 * 
 * @author cq
 */
public class FileException extends BaseException
{
    private static final long serialVersionUID = 1L;

    public FileException(String code, Object[] args)
    {
        super("file", code, args, null);
    }

}
