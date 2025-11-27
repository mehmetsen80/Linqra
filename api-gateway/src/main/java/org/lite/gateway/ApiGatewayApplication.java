package org.lite.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

@SpringBootApplication
@EnableAsync
public class ApiGatewayApplication {
    public static void main(String[] args){
        // Load VAULT_MASTER_KEY from .env file BEFORE Spring Boot starts
        // This ensures Spring Boot can resolve ${VAULT_MASTER_KEY} in YAML files
        loadVaultMasterKeyFromEnv();
        
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
    
    /**
     * Load VAULT_MASTER_KEY from .env file and set it as a system property
     * so Spring Boot can resolve ${VAULT_MASTER_KEY} placeholders
     */
    private static void loadVaultMasterKeyFromEnv() {
        // Only load if not already set
        if (System.getProperty("VAULT_MASTER_KEY") != null || System.getenv("VAULT_MASTER_KEY") != null) {
            return;
        }
        
        try {
            String projectRoot = System.getProperty("user.dir");
            File envFile = new File(projectRoot, ".env");
            
            if (!envFile.exists()) {
                return;
            }
            
            try (Stream<String> lines = Files.lines(Paths.get(envFile.getAbsolutePath()))) {
                String vaultMasterKey = lines
                    .filter(line -> line.startsWith("VAULT_MASTER_KEY="))
                    .map(line -> {
                        String value = line.substring("VAULT_MASTER_KEY=".length());
                        // Remove quotes if present
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value.trim();
                    })
                    .findFirst()
                    .orElse(null);
                
                if (vaultMasterKey != null && !vaultMasterKey.isEmpty()) {
                    System.setProperty("VAULT_MASTER_KEY", vaultMasterKey);
                    System.out.println("Loaded VAULT_MASTER_KEY from .env file");
                }
            }
        } catch (IOException e) {
            // Ignore errors - let EnvironmentPostProcessor handle it
        }
    }
}