package com.cq.panel.admin.server.common.utils.ip;

import com.cq.panel.admin.server.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.config.AppConfig;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 获取地址类
 * 
 * @author cq
 */
public class AddressUtils
{
    private static final Logger log = LoggerFactory.getLogger(AddressUtils.class);

    // IP地址查询
    public static final String IP_URL = "http://whois.pconline.com.cn/ipJson.jsp";

    // 未知地址
    public static final String UNKNOWN = "XX XX";

    public static String getRealAddressByIP(String ip)
    {
        // 内网不查询
        if (MyIpUtils.internalIp(ip))
        {
            return "内网IP";
        }
        if (SpringUtils.getBean(AppConfig.class).isAddressEnabled())
        {
            try
            {
                String rspStr = HttpUtils.sendGet(IP_URL, "ip=" + ip + "&json=true", Constants.GBK);
                if (StringUtils.isEmpty(rspStr))
                {
                    log.error("获取地理位置异常 {}", ip);
                    return UNKNOWN;
                }
                JsonNode obj = JsonUtils.getObjectMapper().readValue(rspStr, JsonNode.class);
                String region = obj.get("pro").asText();
                String city = obj.get("city").asText();
                return String.format("%s %s", region, city);
            }
            catch (Exception e)
            {
                log.error("获取地理位置异常 {}", ip);
            }
        }
        return UNKNOWN;
    }
}
