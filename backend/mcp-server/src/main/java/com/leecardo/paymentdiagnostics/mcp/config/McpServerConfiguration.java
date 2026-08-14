package com.leecardo.paymentdiagnostics.mcp.config;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class McpServerConfiguration {

    @Bean
    HttpServletStreamableServerTransportProvider streamableHttpTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, "/mcp");
        registration.setName("mcp-streamable-http");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer paymentDiagnosticsMcpServer(HttpServletStreamableServerTransportProvider transportProvider) {
        return McpServer.sync(transportProvider)
                .serverInfo("payment-diagnostics-mcp-server", "0.1.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();
    }
}
