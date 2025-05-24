package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DeliveryHtmlReporter {
    private static final String MD_PATH = "src/main/resources/job_details/job_details.md";
    private static final String HTML_PATH = "src/main/resources/delivery_report.html";

    public static class DeliveryRecord {
        public String dateTime;
        public String companyName;
        public String jobName;
        public String jobDutyRaw; // 原始职责文本
        public List<String> jobDutyList = new ArrayList<>(); // 分条职责
        public String salary;
        public String location;
        public String recruiter;
        public String aiGreeting;
    }

    public static void generateHtmlFromMd() {
        List<DeliveryRecord> records = parseMdFile();
        generateHtml(records);
    }

    private static List<DeliveryRecord> parseMdFile() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(MD_PATH)), StandardCharsets.UTF_8);
            return parseMarkdownContent(content);
        } catch (IOException e) {
            System.err.println("读取MD文件失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void generateHtml(List<DeliveryRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang='zh-CN'>\n<head>\n<meta charset='UTF-8'>\n<title>投递记录报告</title>\n");
        sb.append("<style>\n");
        sb.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("body { font-family: 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }\n");
        sb.append(".container { max-width: 1200px; margin: 0 auto; }\n");
        sb.append(".header { text-align: center; margin-bottom: 40px; }\n");
        sb.append(".header h1 { color: white; font-size: 2.5em; margin-bottom: 10px; text-shadow: 0 2px 4px rgba(0,0,0,0.3); }\n");
        sb.append(".header .subtitle { color: rgba(255,255,255,0.9); font-size: 1.1em; }\n");
        sb.append(".stats { display: flex; justify-content: center; gap: 30px; margin-bottom: 40px; }\n");
        sb.append(".stat-card { background: rgba(255,255,255,0.15); backdrop-filter: blur(10px); border-radius: 15px; padding: 20px; text-align: center; min-width: 120px; }\n");
        sb.append(".stat-number { font-size: 2em; font-weight: bold; color: white; }\n");
        sb.append(".stat-label { color: rgba(255,255,255,0.8); margin-top: 5px; }\n");
        sb.append(".timeline { position: relative; }\n");
        sb.append(".timeline::before { content: ''; position: absolute; left: 30px; top: 0; bottom: 0; width: 2px; background: rgba(255,255,255,0.3); }\n");
        sb.append(".delivery-card { position: relative; margin-bottom: 30px; margin-left: 70px; background: white; border-radius: 15px; padding: 25px; box-shadow: 0 8px 25px rgba(0,0,0,0.1); transition: transform 0.3s ease, box-shadow 0.3s ease; }\n");
        sb.append(".delivery-card:hover { transform: translateY(-5px); box-shadow: 0 15px 35px rgba(0,0,0,0.15); }\n");
        sb.append(".time-dot { position: absolute; left: -55px; top: 25px; width: 20px; height: 20px; background: #4CAF50; border-radius: 50%; border: 4px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.2); }\n");
        sb.append(".time-label { position: absolute; left: -140px; top: 20px; background: rgba(255,255,255,0.9); padding: 5px 10px; border-radius: 8px; font-size: 0.85em; color: #666; white-space: nowrap; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n");
        sb.append(".card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }\n");
        sb.append(".job-info h3 { color: #2c3e50; font-size: 1.4em; margin-bottom: 5px; }\n");
        sb.append(".company-name { color: #3498db; font-size: 1.1em; font-weight: 500; }\n");
        sb.append(".salary-badge { background: linear-gradient(45deg, #ff6b6b, #ee5a24); color: white; padding: 8px 15px; border-radius: 20px; font-weight: bold; font-size: 0.9em; }\n");
        sb.append(".card-content { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }\n");
        sb.append(".info-item { display: flex; align-items: center; padding: 10px; background: #f8f9fa; border-radius: 8px; }\n");
        sb.append(".info-icon { width: 20px; height: 20px; margin-right: 10px; }\n");
        sb.append(".info-label { font-weight: 500; color: #666; margin-right: 8px; }\n");
        sb.append(".info-value { color: #2c3e50; }\n");
        sb.append("@media (max-width: 768px) {\n");
        sb.append("  .container { padding: 10px; }\n");
        sb.append("  .delivery-card { margin-left: 50px; padding: 20px; }\n");
        sb.append("  .card-content { grid-template-columns: 1fr; gap: 10px; }\n");
        sb.append("  .time-label { display: none; }\n");
        sb.append("  .stats { flex-direction: column; align-items: center; gap: 15px; }\n");
        sb.append("}\n");
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div class='container'>\n");
        sb.append("<div class='header'>\n");
        sb.append("<h1>📋 投递记录报告</h1>\n");
        sb.append("<div class='subtitle'>Boss直聘自动投递记录 · 数据统计与分析</div>\n");
        sb.append("</div>\n");
        sb.append("<div class='stats'>\n");
        sb.append("<div class='stat-card'>\n");
        sb.append("<div class='stat-number'>").append(records.size()).append("</div>\n");
        sb.append("<div class='stat-label'>总投递数</div>\n");
        sb.append("</div>\n");
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        long todayCount = records.stream().filter(r -> r.dateTime != null && r.dateTime.startsWith(today)).count();
        sb.append("<div class='stat-card'>\n");
        sb.append("<div class='stat-number'>").append(todayCount).append("</div>\n");
        sb.append("<div class='stat-label'>今日投递</div>\n");
        sb.append("</div>\n");
        long companyCount = records.stream().map(r -> r.companyName).filter(Objects::nonNull).distinct().count();
        sb.append("<div class='stat-card'>\n");
        sb.append("<div class='stat-number'>").append(companyCount).append("</div>\n");
        sb.append("<div class='stat-label'>投递公司</div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("<div class='timeline'>\n");
        ListIterator<DeliveryRecord> it = records.listIterator(records.size());
        while (it.hasPrevious()) {
            DeliveryRecord r = it.previous();
            sb.append("<div class='delivery-card'>\n");
            sb.append("<div class='time-dot'></div>\n");
            sb.append("<div class='time-label'>").append(r.dateTime == null ? "未知时间" : r.dateTime).append("</div>\n");
            sb.append("<div class='card-header'>\n");
            sb.append("<div class='job-info'>\n");
            sb.append("<h3>").append(r.jobName == null ? "未知岗位" : r.jobName).append("</h3>\n");
            sb.append("<div class='company-name'>🏢 ").append(r.companyName == null ? "未知公司" : r.companyName).append("</div>\n");
            sb.append("</div>\n");
            sb.append("<div class='salary-badge'>💰 ").append(r.salary == null || r.salary.isEmpty() ? "面议" : r.salary).append("</div>\n");
            sb.append("</div>\n");
            sb.append("<div class='card-content'>\n");
            sb.append("<div class='info-item'>\n");
            sb.append("<span class='info-icon'>📍</span>\n");
            sb.append("<span class='info-label'>工作地点:</span>\n");
            sb.append("<span class='info-value'>").append(r.location == null || r.location.isEmpty() ? "未知" : r.location).append("</span>\n");
            sb.append("</div>\n");
            sb.append("<div class='info-item'>\n");
            sb.append("<span class='info-icon'>👤</span>\n");
            sb.append("<span class='info-label'>招聘者:</span>\n");
            sb.append("<span class='info-value'>").append(r.recruiter == null || r.recruiter.isEmpty() ? "未知" : r.recruiter).append("</span>\n");
            sb.append("</div>\n");
            sb.append("</div>\n");
            sb.append("</div>\n");
        }
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("</body>\n</html>");
        try (FileWriter fw = new FileWriter(HTML_PATH)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            System.err.println("生成投递HTML报告失败: " + e.getMessage());
        }
    }

    // 添加main方法，支持命令行直接生成HTML
    public static void main(String[] args) {
        generateHtmlFromMd();
        System.out.println("投递记录HTML报告已生成: " + HTML_PATH);
    }

    /**
     * 解析markdown内容为投递记录列表
     */
    private static List<DeliveryRecord> parseMarkdownContent(String content) {
        List<DeliveryRecord> records = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return records;
        }

        // 按"**岗位名称：**"分割记录
        String[] sections = content.split("(?=\\*\\*岗位名称：\\*\\*)");
        
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty() || !section.startsWith("**岗位名称：**")) {
                continue;
            }
            
            DeliveryRecord record = new DeliveryRecord();
            String[] lines = section.split("\\n");
            
            boolean inJobDuty = false;
            StringBuilder dutyBuilder = new StringBuilder();
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("**岗位名称：**")) {
                    record.jobName = line.replace("**岗位名称：**", "").trim();
                } else if (line.startsWith("**公司名称：**")) {
                    record.companyName = line.replace("**公司名称：**", "").trim();
                } else if (line.startsWith("**工作地点：**")) {
                    record.location = line.replace("**工作地点：**", "").trim();
                } else if (line.startsWith("**薪资：**")) {
                    record.salary = line.replace("**薪资：**", "").trim();
                } else if (line.startsWith("**招聘者：**")) {
                    record.recruiter = line.replace("**招聘者：**", "").trim();
                } else if (line.startsWith("**抓取时间：**")) {
                    record.dateTime = line.replace("**抓取时间：**", "").trim();
                    inJobDuty = false; // 结束职责描述
                } else if (line.startsWith("**AI打招呼语：**")) {
                    record.aiGreeting = line.replace("**AI打招呼语：**", "").trim();
                    inJobDuty = false; // 结束职责描述
                } else if (line.startsWith("**职位描述/职责/要求：**")) {
                    inJobDuty = true;
                    dutyBuilder.setLength(0); // 清空之前的内容
                } else if (inJobDuty && !line.isEmpty()) {
                    // 收集职责描述内容
                    if (dutyBuilder.length() > 0) {
                        dutyBuilder.append("\\n");
                    }
                    dutyBuilder.append(line);
                }
            }
            
            // 处理职责描述
            record.jobDutyRaw = dutyBuilder.toString();
            parseJobDuty(record);
            
            // 确保必要字段不为空
            if (record.jobName != null && !record.jobName.isEmpty() && 
                record.companyName != null && !record.companyName.isEmpty()) {
                records.add(record);
            }
        }
        
        return records;
    }

    /**
     * 解析职责描述，将原始文本转换为有序列表
     */
    private static void parseJobDuty(DeliveryRecord record) {
        if (record.jobDutyRaw == null || record.jobDutyRaw.trim().isEmpty()) {
            return;
        }
        
        String[] lines = record.jobDutyRaw.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.matches("\\d+、.*")) {
                // 去掉序号，只保留内容
                String content = line.replaceFirst("\\d+、", "").trim();
                if (!content.isEmpty()) {
                    record.jobDutyList.add(content);
                }
            }
        }
    }
} 