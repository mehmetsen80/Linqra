package org.lite.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "server")
public class GatewayProperties {
    /**
     * The host the gateway is running on (e.g., localhost or api-gateway-service).
     */
    private String host = "localhost";

    /**
     * The port the gateway is listening on.
     */
    private int port = 7777;

    /**
     * SSL configuration.
     */
    private Ssl ssl = new Ssl();

    @Data
    public static class Ssl {
        private boolean enabled = false;
    }

    /**
     * Returns the internal base URL for the gateway to call itself.
     * 
     * @return Internal base URL (e.g., https://localhost:7777)
     */
    public String getInternalBaseUrl() {
        String protocol = ssl.isEnabled() ? "https" : "http";
        return protocol + "://" + host + ":" + port;
    }
}
