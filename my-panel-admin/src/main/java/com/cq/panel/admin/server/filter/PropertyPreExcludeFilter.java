package com.cq.panel.admin.server.filter;

import lombok.Getter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 排除JSON敏感属性（Jackson版本）
 * 
 * @author cq
 */
@Getter
public class PropertyPreExcludeFilter
{
    private final Set<String> excludes = new HashSet<>();

    public PropertyPreExcludeFilter()
    {
    }

    public PropertyPreExcludeFilter addExcludes(String... filters)
    {
        Collections.addAll(this.excludes, filters);
        return this;
    }

    public boolean shouldExclude(String property)
    {
        return excludes.contains(property);
    }
}