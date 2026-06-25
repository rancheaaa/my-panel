package com.cq.panel.admin.server.common.utils.ip;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * IP地理位置查询响应
 * 对应 whois.pconline.com.cn 接口返回的JSON格式：
 * {"pro":"广东","city":"深圳","ip":"1.2.3.4",...}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpLocationResult {

    /** 省份 */
    private String pro;

    /** 城市 */
    private String city;

    /** IP地址 */
    private String ip;
}
