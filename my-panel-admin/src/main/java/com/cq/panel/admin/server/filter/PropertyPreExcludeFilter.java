package com.cq.panel.admin.server.filter;

import java.util.HashSet;
import java.util.Set;

/**
 * 排除JSON敏感属性（Jackson版本）
 * 
 * @author cq
 */
public class PropertyPreExcludeFilter
{
    private final Set<String> excludes = new HashSet<>();

    public PropertyPreExcludeFilter()
    {
    }

    public PropertyPreExcludeFilter addExcludes(String... filters)
    {
        for (String filter : filters)
        {
            this.excludes.add(filter);
        }
        return this;
    }

    public Set<String> getExcludes()
    {
        return excludes;
    }

    public boolean shouldExclude(String property)
    {
        return excludes.contains(property);
    }
}