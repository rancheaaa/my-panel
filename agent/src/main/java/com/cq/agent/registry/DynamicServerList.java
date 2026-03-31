package com.cq.agent.registry;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.dto.ProxyApiResponse;
import com.cq.agent.dto.ServiceInstance;
import com.cq.panel.common.loadbalancer.HttpResponse;
import com.cq.panel.common.loadbalancer.LoadBalancerAlgorithm;
import com.cq.panel.common.loadbalancer.LoadBalancerClient;
import com.cq.panel.common.loadbalancer.LoadBalancerManager;
import com.cq.panel.common.loadbalancer.Server;
import com.cq.panel.common.loadbalancer.ServerList;
import com.cq.panel.common.loadbalancer.StaticServerList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态服务器列表
 * 从proxy服务中获取proxy-service的服务实例
 * 
 * @author cq 2026/3/31 22:51
 */
public class DynamicServerList implements ServerList {

    private static final Logger logger = LoggerFactory.getLogger(DynamicServerList.class);
    private static final String PROXY_SERVICE_NAME = "proxy-service";
    private static final String DEFAULT_ENVIRONMENT = "default";
    private static final String DISCOVER_ENDPOINT = "/api/v1/registry/discover";
    private static final String REGISTRY_SERVICE_NAME = "registry-service";

    private final AgentConfig config;
    private final LoadBalancerClient loadBalancerClient;
    private final Map<String, List<Server>> serviceServersCache;
    private volatile List<Server> proxyServers;

    public DynamicServerList(AgentConfig config) {
        this.config = config;
        this.loadBalancerClient = createLoadBalancerClient();
        this.serviceServersCache = new ConcurrentHashMap<>();
        this.proxyServers = new ArrayList<>();
        refresh();
    }

    /**
     * 创建用于调用proxy服务的LoadBalancerClient
     * 
     * @return LoadBalancerClient实例
     */
    private LoadBalancerClient createLoadBalancerClient() {
        List<String> registryUrls = config.getRegistryServerUrls();
        if (registryUrls == null || registryUrls.isEmpty()) {
            logger.warn("No registry server URLs configured, LoadBalancerClient will be null");
            return null;
        }

        LoadBalancerManager loadBalancerManager = LoadBalancerManager.createDefault();
        List<Server> servers = convertUrlsToServers(registryUrls);
        ServerList serverList = createStaticServerList(servers);

        LoadBalancerClient client = loadBalancerManager.getClient(
            REGISTRY_SERVICE_NAME, 
            serverList, 
            LoadBalancerAlgorithm.ROUND_ROBIN
        );

        logger.info("Created LoadBalancerClient for proxy service discovery with {} server(s)", servers.size());
        return client;
    }

    /**
     * 将URL列表转换为Server列表
     * 
     * @param urls URL列表
     * @return Server列表
     */
    private List<Server> convertUrlsToServers(List<String> urls) {
        List<Server> servers = new ArrayList<>();
        int index = 0;
        for (String url : urls) {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : (url.startsWith("https") ? 443 : 80);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String serverId = "registry-server-" + index;
                
                Server server = new Server(serverId, host, port, scheme, null);
                servers.add(server);
                index++;
            } catch (Exception e) {
                logger.error("Failed to parse registry server URL: {}, error: {}", url, e.getMessage());
            }
        }
        return servers;
    }

    /**
     * 创建静态服务器列表
     * 
     * @param servers Server列表
     * @return StaticServerList实例
     */
    private ServerList createStaticServerList(List<Server> servers) {
        StaticServerList serverList = new StaticServerList();
        serverList.addServers(REGISTRY_SERVICE_NAME, servers);
        return serverList;
    }

    @Override
    public List<Server> getServers(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            return List.of();
        }

        if (PROXY_SERVICE_NAME.equals(serviceName)) {
            return new ArrayList<>(proxyServers);
        }

        return serviceServersCache.getOrDefault(serviceName, List.of());
    }

    @Override
    public List<Server> getAllServers() {
        return fetchProxyServers();
    }

    @Override
    public List<Server> getUpServers(String serviceName) {
        List<Server> servers = getAllServers();
        List<Server> upServers = new ArrayList<>();
        for (Server server : servers) {
            if (server.isAlive()) {
                upServers.add(server);
            }
        }
        return upServers;
    }

    @Override
    public List<Server> getDownServers(String serviceName) {
        List<Server> servers = getAllServers();
        List<Server> downServers = new ArrayList<>();
        for (Server server : servers) {
            if (!server.isAlive()) {
                downServers.add(server);
            }
        }
        return downServers;
    }

    @Override
    public void refresh() {
        logger.info("Refreshing dynamic server list from proxy service");
        try {
            List<Server> newProxyServers = fetchProxyServers();
            if (!newProxyServers.isEmpty()) {
                this.proxyServers = newProxyServers;
                logger.info("Successfully refreshed proxy servers: {}", proxyServers);
            } else {
                logger.warn("No proxy servers found from registry");
            }
        } catch (Exception e) {
            logger.error("Failed to refresh proxy server list", e);
        }
    }

    @Override
    public List<String> getAllServiceNames() {
        List<String> serviceNames = new ArrayList<>();
        if (!proxyServers.isEmpty()) {
            serviceNames.add(PROXY_SERVICE_NAME);
        }
        serviceNames.addAll(serviceServersCache.keySet());
        return serviceNames;
    }

    /**
     * 从proxy服务中获取proxy-service的服务实例
     * 
     * @return Server列表
     */
    private List<Server> fetchProxyServers() {
        if (loadBalancerClient == null) {
            logger.warn("LoadBalancerClient is not available");
            return List.of();
        }

        try {
            String discoverPath = buildDiscoverPath();
            logger.debug("Fetching proxy servers using LoadBalancerClient");

            HttpResponse<String> response = loadBalancerClient.get(REGISTRY_SERVICE_NAME, discoverPath, String.class);
            
            if (response.getStatusCode() == 200 && response.getBody() != null) {
                List<ServiceInstance> instances = parseDiscoverResponse(response.getBody());
                if (instances != null && !instances.isEmpty()) {
                    List<Server> servers = new ArrayList<>();
                    for (ServiceInstance instance : instances) {
                        Server server = convertToServer(instance);
                        servers.add(server);
                    }
                    logger.debug("Found {} available proxy servers", instances.size());
                    return servers;
                }
            } else {
                logger.warn("Failed to fetch proxy servers, status code: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Failed to fetch proxy servers using LoadBalancerClient", e);
        }

        return List.of();
    }

    /**
     * 构建服务发现路径
     * 
     * @return 完整的路径
     */
    private String buildDiscoverPath() {
        return DISCOVER_ENDPOINT + "?serviceName=" + PROXY_SERVICE_NAME + "&environment=" + DEFAULT_ENVIRONMENT;
    }

    /**
     * 解析服务发现响应
     * 
     * @param responseBody 响应体
     * @return ServiceInstance列表
     */
    private List<ServiceInstance> parseDiscoverResponse(String responseBody) {
        try {
            TypeToken<ProxyApiResponse<List<ServiceInstance>>> typeToken = 
                new TypeToken<ProxyApiResponse<List<ServiceInstance>>>() {};
            
            Gson gsonWithAdapter = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new ServiceInstance.InstantTypeAdapter())
                .registerTypeAdapter(typeToken.getType(), 
                    new ProxyApiResponse.ProxyApiResponseDeserializer<List<ServiceInstance>>(typeToken.getType()))
                .create();
            
            ProxyApiResponse<List<ServiceInstance>> apiResponse = gsonWithAdapter.fromJson(responseBody, typeToken.getType());

            if (apiResponse != null && apiResponse.isSuccess() && apiResponse.getData() != null) {
                return apiResponse.getData();
            }
        } catch (Exception e) {
            logger.error("Failed to parse discover response: {}", responseBody, e);
        }
        return null;
    }

    /**
     * 将ServiceInstance转换为Server
     * 
     * @param instance 服务实例
     * @return Server对象
     */
    private Server convertToServer(ServiceInstance instance) {
        String serverId = "proxy-" + instance.host() + ":" + instance.port();
        return new Server(serverId, instance.host(), instance.port(), "http", null);
    }

    /**
     * 关闭资源
     */
    public void close() {
        if (loadBalancerClient != null) {
            loadBalancerClient.close();
            logger.info("DynamicServerList closed");
        }
    }

}