package com.cq.agent.batch.scanner;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GlobMatcher
{
    private final List<PathMatcher> includeMatchers;
    private final List<PathMatcher> excludeMatchers;

    public GlobMatcher(String baseDir, List<String> includePatterns, List<String> excludePatterns)
    {
        this.includeMatchers = compilePatterns(includePatterns);
        this.excludeMatchers = compilePatterns(excludePatterns);
    }

    public boolean matches(String relativePath)
    {
        String normalizedPath = normalizePath(relativePath);
        return shouldInclude(normalizedPath) && !shouldExclude(normalizedPath);
    }

    private List<PathMatcher> compilePatterns(List<String> patterns)
    {
        List<PathMatcher> matchers = new ArrayList<>();
        if (patterns == null || patterns.isEmpty())
        {
            return matchers;
        }
        for (String pattern : patterns)
        {
            if (pattern == null || pattern.isBlank())
            {
                continue;
            }
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + normalizePath(pattern.trim())));
        }
        return matchers;
    }

    private boolean shouldInclude(String relativePath)
    {
        if (includeMatchers.isEmpty())
        {
            return true;
        }
        Path path = Paths.get(relativePath);
        for (PathMatcher matcher : includeMatchers)
        {
            if (matcher.matches(path))
            {
                return true;
            }
        }
        return false;
    }

    private boolean shouldExclude(String relativePath)
    {
        Path path = Paths.get(relativePath);
        for (PathMatcher matcher : excludeMatchers)
        {
            if (matcher.matches(path))
            {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path)
    {
        return path.replace('\\', '/');
    }
}
