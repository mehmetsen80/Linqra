package org.lite.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

@Configuration
@EnableScheduling
@Slf4j
public class ResourceMonitoringConfig {

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    /**
     * Monitor memory usage every 15 seconds and log warnings if memory usage is high
     */
    @Scheduled(fixedRate = 15000) // Every 15 seconds for EC2 monitoring
    public void monitorMemoryUsage() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            
            long heapUsed = heapUsage.getUsed();
            long heapMax = heapUsage.getMax();
            long nonHeapUsed = nonHeapUsage.getUsed();
            
            double heapUsagePercent = (double) heapUsed / heapMax * 100;
            
            if (heapUsagePercent > 70) {  // Lowered threshold for EC2
                log.warn("⚠️ HIGH MEMORY USAGE: Heap usage is {}% ({}MB / {}MB). Non-heap: {}MB", 
                    String.format("%.1f", heapUsagePercent),
                    heapUsed / 1024 / 1024,
                    heapMax / 1024 / 1024,
                    nonHeapUsed / 1024 / 1024);
                
                // Force garbage collection more aggressively on EC2
                if (heapUsagePercent > 80) {  // Lowered critical threshold
                    log.warn("🚨 CRITICAL MEMORY USAGE: Forcing garbage collection...");
                    System.gc();
                }
            } else if (heapUsagePercent > 50) {  // Lowered warning threshold
                log.info("📊 Memory usage: {}% ({}MB / {}MB)", 
                    String.format("%.1f", heapUsagePercent),
                    heapUsed / 1024 / 1024,
                    heapMax / 1024 / 1024);
            }
        } catch (Exception e) {
            log.error("Error monitoring memory usage: {}", e.getMessage());
        }
    }

    /**
     * Log system resource information on application startup (runs once only)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logSystemInfo() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            Runtime runtime = Runtime.getRuntime();
            
            log.info("🚀 System Resources:");
            log.info("   - Available Processors: {}", runtime.availableProcessors());
            log.info("   - Max Memory: {}MB", heapUsage.getMax() / 1024 / 1024);
            log.info("   - Initial Memory: {}MB", heapUsage.getInit() / 1024 / 1024);
            log.info("   - JVM Version: {}", System.getProperty("java.version"));
            log.info("   - OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        } catch (Exception e) {
            log.error("Error logging system info: {}", e.getMessage());
        }
    }
}
