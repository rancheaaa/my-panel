package com.cq.panel.admin.server.web.domain.vo.system;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 路由配置信息
 * 
 * @author cq
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "路由配置信息")
public class RouterVo
{
    /**
     * 路由名字
     */
    @Schema(description = "路由名字", example = "User")
    private String name;

    /**
     * 路由地址
     */
    @Schema(description = "路由地址", example = "user")
    private String path;

    /**
     * 是否隐藏路由，当设置 true 的时候该路由不会再侧边栏出现
     */
    @Schema(description = "是否隐藏路由", example = "false")
    private boolean hidden;

    /**
     * 重定向地址，当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
     */
    @Schema(description = "重定向地址", example = "noRedirect")
    private String redirect;

    /**
     * 组件地址
     */
    @Schema(description = "组件地址", example = "system/user/index")
    private String component;

    /**
     * 路由参数：如 {"id": 1, "name": "ry"}
     */
    @Schema(description = "路由参数", example = "{\"id\": 1, \"name\": \"ry\"}")
    private String query;

    /**
     * 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
     */
    @Schema(description = "是否总是显示", example = "true")
    private Boolean alwaysShow;

    /**
     * 其他元素
     */
    @Schema(description = "其他元素")
    private MetaVo meta;

    /**
     * 子路由
     */
    @Schema(description = "子路由")
    private List<RouterVo> children;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public boolean getHidden()
    {
        return hidden;
    }

    public void setHidden(boolean hidden)
    {
        this.hidden = hidden;
    }

    public String getRedirect()
    {
        return redirect;
    }

    public void setRedirect(String redirect)
    {
        this.redirect = redirect;
    }

    public String getComponent()
    {
        return component;
    }

    public void setComponent(String component)
    {
        this.component = component;
    }

    public String getQuery()
    {
        return query;
    }

    public void setQuery(String query)
    {
        this.query = query;
    }

    public Boolean getAlwaysShow()
    {
        return alwaysShow;
    }

    public void setAlwaysShow(Boolean alwaysShow)
    {
        this.alwaysShow = alwaysShow;
    }

    public MetaVo getMeta()
    {
        return meta;
    }

    public void setMeta(MetaVo meta)
    {
        this.meta = meta;
    }

    public List<RouterVo> getChildren()
    {
        return children;
    }

    public void setChildren(List<RouterVo> children)
    {
        this.children = children;
    }
}

