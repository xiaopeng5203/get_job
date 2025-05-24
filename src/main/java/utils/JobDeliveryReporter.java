package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位投递记录报告生成器
 * 用于生成可视化的HTML投递报告
 */
public class JobDeliveryReporter {
    
    private static final String RECORDS_FILE = "src/main/resources/delivery_records.json";
    private static final String HTML_REPORT_FILE = "src/main/resources/delivery_report.html";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    @Data
    public static class DeliveryRecord {
        private String date;
        private String company;
        private String position;
        private String salary;
        private String location;
        private String jobDescription;
        private String aiGreeting;
        private String platform;
        private String jobUrl;
        private long timestamp;
        private String status;
        private String failReason;
        
        public DeliveryRecord() {
            this.timestamp = System.currentTimeMillis();
            this.date = dateFormat.format(new Date(this.timestamp));
        }
    }
    
    /**
     * 记录投递信息
     */
    public static void recordDelivery(Job job, String aiGreeting, String platform, String status, String failReason) {
        try {
            DeliveryRecord record = new DeliveryRecord();
            record.setCompany(job.getCompanyName());
            record.setPosition(job.getJobName());
            record.setSalary(job.getSalary());
            record.setLocation(job.getJobArea());
            record.setJobDescription(job.getJobInfo());
            record.setAiGreeting(aiGreeting);
            record.setPlatform(platform);
            record.setJobUrl(job.getHref());
            record.setStatus(status);
            record.setFailReason(failReason);
            
            // 读取现有记录
            List<DeliveryRecord> records = loadRecords();
            records.add(record);
            
            // 保存记录
            saveRecords(records);
            
            // 生成HTML报告
            generateHtmlReport(records);
            
        } catch (Exception e) {
            System.err.println("记录投递信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 兼容老接口
     */
    public static void recordDelivery(Job job, String aiGreeting, String platform) {
        recordDelivery(job, aiGreeting, platform, "success", null);
    }
    
    /**
     * 加载现有记录
     */
    private static List<DeliveryRecord> loadRecords() {
        try {
            File file = new File(RECORDS_FILE);
            if (!file.exists()) {
                return new ArrayList<>();
            }
            
            String content = new String(Files.readAllBytes(Paths.get(RECORDS_FILE)));
            if (content.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            return objectMapper.readValue(content, new TypeReference<List<DeliveryRecord>>() {});
        } catch (Exception e) {
            System.err.println("加载投递记录失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 保存记录到JSON文件
     */
    private static void saveRecords(List<DeliveryRecord> records) throws IOException {
        // 确保目录存在
        File file = new File(RECORDS_FILE);
        file.getParentFile().mkdirs();
        
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(records);
        Files.write(Paths.get(RECORDS_FILE), json.getBytes());
    }
    
    /**
     * 生成HTML报告
     */
    public static void generateHtmlReport(List<DeliveryRecord> records) throws IOException {
        // 按日期分组
        Map<String, List<DeliveryRecord>> recordsByDay = records.stream()
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp())) // 按时间倒序
            .collect(Collectors.groupingBy(
                record -> dayFormat.format(new Date(record.getTimestamp())),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        
        StringBuilder html = new StringBuilder();
        html.append(getHtmlHeader());
        html.append(getStatisticsSection(records));
        
        // 按天展示记录
        for (Map.Entry<String, List<DeliveryRecord>> entry : recordsByDay.entrySet()) {
            String day = entry.getKey();
            List<DeliveryRecord> dayRecords = entry.getValue();
            
            html.append(getDaySection(day, dayRecords));
        }
        
        html.append(getHtmlFooter());
        
        // 保存HTML文件
        File htmlFile = new File(HTML_REPORT_FILE);
        htmlFile.getParentFile().mkdirs();
        
        try (FileWriter writer = new FileWriter(htmlFile)) {
            writer.write(html.toString());
        }
        
        System.out.println("📄 投递报告已生成: " + new File(HTML_REPORT_FILE).getAbsolutePath());
    }
    
    /**
     * HTML头部
     */
    private static String getHtmlHeader() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Boss直聘投递记录报告</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;
                        line-height: 1.6;
                        color: #333;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        padding: 20px;
                    }
                    
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                        background: white;
                        border-radius: 15px;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }
                    
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 30px;
                        text-align: center;
                    }
                    
                    .header h1 {
                        font-size: 2.5em;
                        margin-bottom: 10px;
                        text-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }
                    
                    .header p {
                        opacity: 0.9;
                        font-size: 1.1em;
                    }
                    
                    .stats {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        padding: 30px;
                        background: #f8f9fa;
                    }
                    
                    .stat-card {
                        background: white;
                        padding: 25px;
                        border-radius: 12px;
                        text-align: center;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.1);
                        transition: transform 0.3s ease;
                    }
                    
                    .stat-card:hover {
                        transform: translateY(-5px);
                    }
                    
                    .stat-number {
                        font-size: 2em;
                        font-weight: bold;
                        color: #667eea;
                        display: block;
                    }
                    
                    .stat-label {
                        color: #666;
                        margin-top: 5px;
                    }
                    
                    .content {
                        padding: 0 30px 30px;
                    }
                    
                    .day-section {
                        margin-bottom: 40px;
                    }
                    
                    .day-header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 15px 25px;
                        border-radius: 10px;
                        margin-bottom: 20px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    
                    .day-title {
                        font-size: 1.3em;
                        font-weight: bold;
                    }
                    
                    .day-count {
                        background: rgba(255,255,255,0.2);
                        padding: 5px 15px;
                        border-radius: 20px;
                        font-size: 0.9em;
                    }
                    
                    .job-card {
                        background: white;
                        border: 1px solid #e9ecef;
                        border-radius: 12px;
                        margin-bottom: 20px;
                        overflow: hidden;
                        transition: all 0.3s ease;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.05);
                    }
                    
                    .job-card:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 5px 20px rgba(0,0,0,0.1);
                    }
                    
                    .job-header {
                        background: #f8f9fa;
                        padding: 20px;
                        border-bottom: 1px solid #e9ecef;
                        display: grid;
                        grid-template-columns: 1fr auto;
                        gap: 20px;
                        align-items: start;
                    }
                    
                    .job-title {
                        font-size: 1.4em;
                        font-weight: bold;
                        color: #2c3e50;
                        margin-bottom: 5px;
                    }
                    
                    .job-company {
                        color: #667eea;
                        font-size: 1.1em;
                        margin-bottom: 10px;
                    }
                    
                    .job-meta {
                        display: flex;
                        gap: 15px;
                        flex-wrap: wrap;
                    }
                    
                    .meta-item {
                        display: flex;
                        align-items: center;
                        gap: 5px;
                        color: #666;
                        font-size: 0.9em;
                    }
                    
                    .job-time {
                        color: #95a5a6;
                        font-size: 0.9em;
                    }
                    
                    .job-body {
                        padding: 20px;
                    }
                    
                    .job-description {
                        background: #f8f9fa;
                        padding: 15px;
                        border-radius: 8px;
                        margin-bottom: 15px;
                        border-left: 4px solid #667eea;
                    }
                    
                    .job-description h4 {
                        color: #2c3e50;
                        margin-bottom: 10px;
                    }
                    
                    .ai-greeting {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 15px;
                        border-radius: 8px;
                        margin-top: 15px;
                    }
                    
                    .ai-greeting h4 {
                        margin-bottom: 10px;
                        display: flex;
                        align-items: center;
                        gap: 5px;
                    }
                    
                    .platform-badge {
                        background: #667eea;
                        color: white;
                        padding: 5px 12px;
                        border-radius: 20px;
                        font-size: 0.8em;
                        text-transform: uppercase;
                        font-weight: bold;
                    }
                    
                    .salary-highlight {
                        color: #e74c3c;
                        font-weight: bold;
                        font-size: 1.1em;
                    }
                    
                    .location-badge {
                        background: #27ae60;
                        color: white;
                        padding: 3px 8px;
                        border-radius: 12px;
                        font-size: 0.8em;
                    }
                    
                    .footer {
                        text-align: center;
                        padding: 30px;
                        background: #f8f9fa;
                        color: #666;
                        border-top: 1px solid #e9ecef;
                    }
                    
                    @media (max-width: 768px) {
                        .job-header {
                            grid-template-columns: 1fr;
                        }
                        
                        .job-meta {
                            justify-content: space-between;
                        }
                        
                        .stats {
                            grid-template-columns: repeat(2, 1fr);
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🚀 Boss直聘投递记录</h1>
                        <p>智能求职自动化平台投递报告</p>
                    </div>
            """;
    }
    
    /**
     * 统计信息部分
     */
    private static String getStatisticsSection(List<DeliveryRecord> records) {
        long totalJobs = records.size();
        long successJobs = records.stream().filter(r -> "success".equals(r.getStatus())).count();
        long failJobs = records.stream().filter(r -> "fail".equals(r.getStatus())).count();
        long todayJobs = records.stream().filter(record -> dayFormat.format(new Date(record.getTimestamp())).equals(dayFormat.format(new Date()))).count();
        long uniqueCompanies = records.stream().map(DeliveryRecord::getCompany).distinct().count();
        long last7Days = records.stream().filter(record -> (System.currentTimeMillis() - record.getTimestamp()) / (1000 * 60 * 60 * 24) <= 7).count();
        // 失败原因分布
        Map<String, Long> failReasonMap = records.stream().filter(r -> "fail".equals(r.getStatus())).collect(Collectors.groupingBy(r -> r.getFailReason() == null ? "其它" : r.getFailReason(), Collectors.counting()));
        StringBuilder stats = new StringBuilder();
        stats.append("""
            <div class=\"stats\">
                <div class=\"stat-card\">
                    <span class=\"stat-number\">%d</span>
                    <div class=\"stat-label\">总投递数</div>
                </div>
                <div class=\"stat-card\">
                    <span class=\"stat-number\">%d</span>
                    <div class=\"stat-label\">成功</div>
                </div>
                <div class=\"stat-card\">
                    <span class=\"stat-number\">%d</span>
                    <div class=\"stat-label\">失败</div>
                </div>
                <div class=\"stat-card\">
                    <span class=\"stat-number\">%d</span>
                    <div class=\"stat-label\">今日投递</div>
                </div>
                <div class=\"stat-card\">
                    <span class=\"stat-number\">%d</span>
                    <div class=\"stat-label\">投递公司</div>
                </div>
                <div class=\"stat-card\">
                    <span class=\"stat-number\">%d</span>
                    <div class=\"stat-label\">近7天投递</div>
                </div>
            </div>
            <div id=\"fail-reason-chart\" style=\"width:100%;height:300px;margin:30px 0;\"></div>
            <div id=\"trend-chart\" style=\"width:100%;height:300px;margin:30px 0;\"></div>
        """.formatted(totalJobs, successJobs, failJobs, todayJobs, uniqueCompanies, last7Days));
        // 失败原因分布表
        if (!failReasonMap.isEmpty()) {
            stats.append("<div style='margin:20px 0;'><b>失败原因分布：</b><ul>");
            for (Map.Entry<String, Long> entry : failReasonMap.entrySet()) {
                stats.append("<li>" + entry.getKey() + ": " + entry.getValue() + "</li>");
            }
            stats.append("</ul></div>");
        }
        // 预留趋势图和饼图容器（可用ECharts/Chart.js后续补充）
        stats.append("<script src='https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js'></script>");
        stats.append("<script>/* 这里可后续补充JS动态渲染趋势图和饼图 */</script>");
        return stats.toString();
    }
    
    /**
     * 单日记录部分
     */
    private static String getDaySection(String day, List<DeliveryRecord> dayRecords) {
        StringBuilder section = new StringBuilder();
        section.append("""
            <div class="content">
                <div class="day-section">
                    <div class="day-header">
                        <div class="day-title">📅 %s</div>
                        <div class="day-count">%d 个岗位</div>
                    </div>
            """.formatted(day, dayRecords.size()));
        
        for (DeliveryRecord record : dayRecords) {
            section.append(getJobCard(record));
        }
        
        section.append("""
                </div>
            </div>
            """);
        
        return section.toString();
    }
    
    /**
     * 单个岗位卡片
     */
    private static String getJobCard(DeliveryRecord record) {
        String description = record.getJobDescription();
        if (description != null && description.length() > 300) {
            description = description.substring(0, 300) + "...";
        }
        
        String aiGreeting = record.getAiGreeting();
        if (aiGreeting != null && aiGreeting.length() > 200) {
            aiGreeting = aiGreeting.substring(0, 200) + "...";
        }
        
        // 新增：状态和失败原因
        String statusHtml = "success".equals(record.getStatus()) ?
            "<span style='color:#27ae60;font-weight:bold;'>✔️ 成功</span>" :
            ("<span style='color:#e74c3c;font-weight:bold;'>❌ 失败</span>" + (record.getFailReason() != null ? " - " + record.getFailReason() : ""));
        
        return """
            <div class="job-card">
                <div class="job-header">
                    <div>
                        <div class="job-title">%s</div>
                        <div class="job-company">🏢 %s</div>
                        <div class="job-meta">
                            <div class="meta-item">
                                <span>💰</span>
                                <span class="salary-highlight">%s</span>
                            </div>
                            <div class="meta-item">
                                <span>📍</span>
                                <span class="location-badge">%s</span>
                            </div>
                            <div class="meta-item">
                                <span class="platform-badge">%s</span>
                            </div>
                            <div class="meta-item">%s</div>
                        </div>
                    </div>
                    <div class="job-time">%s</div>
                </div>
                <div class="job-body">
                    %s
                    %s
                </div>
            </div>
            """.formatted(
                record.getPosition() != null ? record.getPosition() : "未知岗位",
                record.getCompany() != null ? record.getCompany() : "未知公司", 
                record.getSalary() != null ? record.getSalary() : "薪资面议",
                record.getLocation() != null ? record.getLocation() : "未知地点",
                record.getPlatform() != null ? record.getPlatform() : "BOSS",
                statusHtml,
                record.getDate() != null ? record.getDate() : "",
                description != null ? 
                    "<div class=\"job-description\"><h4>💼 岗位描述</h4><p>" + description + "</p></div>" : "",
                aiGreeting != null ?
                    "<div class=\"ai-greeting\"><h4>🤖 AI招呼内容</h4><p>" + aiGreeting + "</p></div>" : ""
            );
    }
    
    /**
     * HTML尾部
     */
    private static String getHtmlFooter() {
        return """
                    <div class="footer">
                        <p>📊 报告生成时间: %s</p>
                        <p>🤖 由Boss直聘自动投递系统生成</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(dateFormat.format(new Date()));
    }
    
    /**
     * 手动生成报告（用于重新生成历史记录的报告）
     */
    public static void generateReport() {
        try {
            List<DeliveryRecord> records = loadRecords();
            generateHtmlReport(records);
        } catch (Exception e) {
            System.err.println("生成报告失败: " + e.getMessage());
        }
    }
} 