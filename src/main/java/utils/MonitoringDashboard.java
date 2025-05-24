package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时监控仪表板
 * 提供性能监控、异常检测、告警功能
 * 
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class MonitoringDashboard {
    
    private static final Logger log = LoggerFactory.getLogger(MonitoringDashboard.class);
    
    // 监控指标收集器
    private static final Map<String, AtomicLong> METRICS = new ConcurrentHashMap<>();
    private static final Map<String, List<Long>> TIME_SERIES = new ConcurrentHashMap<>();
    private static final Map<String, Double> THRESHOLDS = new ConcurrentHashMap<>();
    
    // 定时任务
    private static ScheduledExecutorService scheduler;
    private static boolean isMonitoring = false;
    
    // 告警配置
    private static final long ALERT_COOLDOWN = 5 * 60 * 1000; // 5分钟冷却期
    private static final Map<String, Long> LAST_ALERT_TIME = new ConcurrentHashMap<>();
    
    /**
     * 启动监控
     */
    public static void startMonitoring() {
        if (isMonitoring) {
            return;
        }
        
        isMonitoring = true;
        scheduler = Executors.newScheduledThreadPool(3);
        
        // 初始化阈值
        initThresholds();
        
        // 启动各种监控任务
        scheduler.scheduleAtFixedRate(MonitoringDashboard::collectSystemMetrics, 0, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(MonitoringDashboard::checkAlerts, 0, 60, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(MonitoringDashboard::generateReport, 0, 5, TimeUnit.MINUTES);
        
        log.info("监控仪表板已启动");
    }
    
    /**
     * 停止监控
     */
    public static void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        isMonitoring = false;
        log.info("监控仪表板已停止");
    }
    
    /**
     * 初始化告警阈值
     */
    private static void initThresholds() {
        THRESHOLDS.put("memory.usage.percentage", 85.0);  // 内存使用率85%
        THRESHOLDS.put("response.time.avg", 5000.0);      // 平均响应时间5秒
        THRESHOLDS.put("error.rate", 0.1);                // 错误率10%
        THRESHOLDS.put("thread.count", 100.0);            // 线程数100
        THRESHOLDS.put("delivery.success.rate", 0.7);     // 投递成功率70%
    }
    
    /**
     * 记录指标
     */
    public static void recordMetric(String metricName, long value) {
        METRICS.compute(metricName, (k, v) -> v == null ? new AtomicLong(value) : new AtomicLong(v.get() + value));
        
        // 记录时间序列数据
        TIME_SERIES.computeIfAbsent(metricName, k -> new ArrayList<>()).add(value);
        
        // 限制时间序列数据大小
        List<Long> series = TIME_SERIES.get(metricName);
        if (series.size() > 1000) {
            series.remove(0);
        }
    }
    
    /**
     * 设置指标值
     */
    public static void setMetric(String metricName, long value) {
        METRICS.put(metricName, new AtomicLong(value));
        TIME_SERIES.computeIfAbsent(metricName, k -> new ArrayList<>()).add(value);
    }
    
    /**
     * 增加计数器
     */
    public static void incrementCounter(String counterName) {
        METRICS.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * 收集系统指标
     */
    private static void collectSystemMetrics() {
        try {
            // 内存使用情况
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            double memoryUsagePercentage = (double) usedMemory / maxMemory * 100;
            
            setMetric("memory.used", usedMemory);
            setMetric("memory.max", maxMemory);
            setMetric("memory.usage.percentage", (long) memoryUsagePercentage);
            
            // 线程信息
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            setMetric("thread.count", threadBean.getThreadCount());
            setMetric("thread.daemon.count", threadBean.getDaemonThreadCount());
            
            // GC信息
            ManagementFactory.getGarbageCollectorMXBeans().forEach(gcBean -> {
                setMetric("gc." + gcBean.getName().replace(" ", "_") + ".count", gcBean.getCollectionCount());
                setMetric("gc." + gcBean.getName().replace(" ", "_") + ".time", gcBean.getCollectionTime());
            });
            
            // 记录内存使用情况
            log.debug("系统指标收集完成 - 内存使用率: {}%", (long) memoryUsagePercentage);
            
        } catch (Exception e) {
            log.error("收集系统指标失败", e);
        }
    }
    
    /**
     * 检查告警
     */
    private static void checkAlerts() {
        THRESHOLDS.forEach((metricName, threshold) -> {
            AtomicLong currentValue = METRICS.get(metricName);
            if (currentValue != null) {
                double value = currentValue.get();
                boolean shouldAlert = false;
                String alertMessage = "";
                
                switch (metricName) {
                    case "memory.usage.percentage":
                        if (value > threshold) {
                            shouldAlert = true;
                            alertMessage = String.format("内存使用率过高: %.1f%% (阈值: %.1f%%)", value, threshold);
                        }
                        break;
                    case "response.time.avg":
                        if (value > threshold) {
                            shouldAlert = true;
                            alertMessage = String.format("平均响应时间过长: %.0fms (阈值: %.0fms)", value, threshold);
                        }
                        break;
                    case "error.rate":
                        if (value > threshold) {
                            shouldAlert = true;
                            alertMessage = String.format("错误率过高: %.2f%% (阈值: %.2f%%)", value * 100, threshold * 100);
                        }
                        break;
                    case "thread.count":
                        if (value > threshold) {
                            shouldAlert = true;
                            alertMessage = String.format("线程数过多: %.0f (阈值: %.0f)", value, threshold);
                        }
                        break;
                    case "delivery.success.rate":
                        if (value < threshold) {
                            shouldAlert = true;
                            alertMessage = String.format("投递成功率过低: %.2f%% (阈值: %.2f%%)", value * 100, threshold * 100);
                        }
                        break;
                }
                
                if (shouldAlert && shouldSendAlert(metricName)) {
                    sendAlert(metricName, alertMessage);
                }
            }
        });
    }
    
    /**
     * 检查是否应该发送告警（考虑冷却期）
     */
    private static boolean shouldSendAlert(String metricName) {
        long now = System.currentTimeMillis();
        Long lastAlertTime = LAST_ALERT_TIME.get(metricName);
        if (lastAlertTime == null || now - lastAlertTime > ALERT_COOLDOWN) {
            LAST_ALERT_TIME.put(metricName, now);
            return true;
        }
        return false;
    }
    
    /**
     * 发送告警
     */
    private static void sendAlert(String metricName, String message) {
        log.warn("🚨 ALERT: {}", message);
        
        // 记录告警日志
        log.warn("系统告警 - 指标: {}, 消息: {}", metricName, message);
        
        // 发送通知（如果配置了Bot）
        try {
            Bot.sendBarkAlert(metricName, message);
        } catch (Exception e) {
            log.error("发送告警通知失败", e);
        }
    }
    
    /**
     * 生成监控报告
     */
    private static void generateReport() {
        try {
            StringBuilder report = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            report.append("=== 监控报告 ===\n");
            report.append("时间: ").append(sdf.format(new Date())).append("\n\n");
            
            // 系统指标
            report.append("系统指标:\n");
            AtomicLong memoryUsage = METRICS.get("memory.usage.percentage");
            if (memoryUsage != null) {
                report.append(String.format("  内存使用率: %d%%\n", memoryUsage.get()));
            }
            
            AtomicLong threadCount = METRICS.get("thread.count");
            if (threadCount != null) {
                report.append(String.format("  线程数量: %d\n", threadCount.get()));
            }
            
            // 业务指标
            report.append("\n业务指标:\n");
            long totalJobs = getMetric("total_jobs");
            long deliveredJobs = getMetric("delivered_jobs");
            long filteredJobs = getMetric("filtered_jobs");
            
            report.append(String.format("  总岗位数: %d\n", totalJobs));
            report.append(String.format("  投递成功: %d\n", deliveredJobs));
            report.append(String.format("  过滤数量: %d\n", filteredJobs));
            
            if (totalJobs > 0) {
                double successRate = (double) deliveredJobs / totalJobs;
                report.append(String.format("  成功率: %.2f%%\n", successRate * 100));
            }
            
            // 平台分布
            report.append("\n平台分布:\n");
            Arrays.asList("BOSS", "LAGOU", "LIEPIN", "ZHILIAN", "JOB51").forEach(platform -> {
                long count = getMetric("platform_" + platform.toLowerCase());
                if (count > 0) {
                    report.append(String.format("  %s: %d\n", platform, count));
                }
            });
            
            log.info("\n{}", report.toString());
            
            // 保存到文件
            saveReportToFile(report.toString());
            
        } catch (Exception e) {
            log.error("生成监控报告失败", e);
        }
    }
    
    /**
     * 保存报告到文件
     */
    private static void saveReportToFile(String report) {
        try {
            String fileName = String.format("./target/logs/monitoring-report-%s.txt", 
                new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date()));
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(report);
            }
        } catch (IOException e) {
            log.error("保存监控报告失败", e);
        }
    }
    
    /**
     * 获取指标值
     */
    public static long getMetric(String metricName) {
        AtomicLong metric = METRICS.get(metricName);
        return metric != null ? metric.get() : 0;
    }
    
    /**
     * 获取所有指标
     */
    public static Map<String, Long> getAllMetrics() {
        Map<String, Long> result = new HashMap<>();
        METRICS.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    /**
     * 获取时间序列数据
     */
    public static List<Long> getTimeSeries(String metricName) {
        return TIME_SERIES.getOrDefault(metricName, new ArrayList<>());
    }
    
    /**
     * 计算平均值
     */
    public static double calculateAverage(String metricName) {
        List<Long> series = getTimeSeries(metricName);
        if (series.isEmpty()) {
            return 0.0;
        }
        return series.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    /**
     * 检测异常值
     */
    public static boolean detectAnomaly(String metricName, long newValue) {
        List<Long> series = getTimeSeries(metricName);
        if (series.size() < 10) {
            return false; // 数据不足，无法检测异常
        }
        
        // 计算平均值和标准差
        double mean = series.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = series.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        // 使用3σ规则检测异常
        double threshold = 3 * stdDev;
        boolean isAnomaly = Math.abs(newValue - mean) > threshold;
        
        if (isAnomaly) {
            log.warn("指标异常检测 - 指标: {}, 异常值: {}, 平均值: {:.2f}, 标准差: {:.2f}", 
                metricName, newValue, mean, stdDev);
        }
        
        return isAnomaly;
    }
    
    /**
     * 设置阈值
     */
    public static void setThreshold(String metricName, double threshold) {
        THRESHOLDS.put(metricName, threshold);
    }
    
    /**
     * 获取健康状态
     */
    public static Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        // 整体状态
        boolean isHealthy = true;
        List<String> issues = new ArrayList<>();
        
        // 检查内存使用率
        long memoryUsage = getMetric("memory.usage.percentage");
        if (memoryUsage > 90) {
            isHealthy = false;
            issues.add("内存使用率过高: " + memoryUsage + "%");
        }
        
        // 检查线程数
        long threadCount = getMetric("thread.count");
        if (threadCount > 200) {
            isHealthy = false;
            issues.add("线程数过多: " + threadCount);
        }
        
        health.put("status", isHealthy ? "HEALTHY" : "UNHEALTHY");
        health.put("issues", issues);
        health.put("timestamp", System.currentTimeMillis());
        health.put("uptime", System.currentTimeMillis() - getMetric("start_time"));
        
        return health;
    }
    
    /**
     * 手动查看监控状态（命令行工具）
     */
    public static void main(String[] args) {
        System.out.println("=== 监控仪表板状态查看工具 ===");
        
        if (args.length > 0 && "start".equals(args[0])) {
            startMonitoring();
            System.out.println("监控已启动，按回车键查看报告...");
            try {
                System.in.read();
                generateReport();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stopMonitoring();
            }
        } else {
            // 显示当前指标
            System.out.println("\n当前系统指标:");
            getAllMetrics().forEach((k, v) -> System.out.printf("  %s: %d\n", k, v));
            
            System.out.println("\n当前业务计数器:");
            getAllMetrics().forEach((k, v) -> {
                if (k.startsWith("total_") || k.startsWith("delivered_") || k.startsWith("platform_")) {
                    System.out.printf("  %s: %d\n", k, v);
                }
            });
            
            System.out.println("\n使用方法:");
            System.out.println("  java utils.MonitoringDashboard start  # 启动监控并生成报告");
            System.out.println("  java utils.MonitoringDashboard        # 查看当前状态");
        }
    }
} 