package utils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.text.SimpleDateFormat;

public class MdToJobDeliveryReport {
    public static void main(String[] args) throws Exception {
        String mdPath = "src/main/resources/job_details/job_details.md";
        String content = new String(Files.readAllBytes(Paths.get(mdPath)));
        List<JobDeliveryReporter.DeliveryRecord> records = new ArrayList<>();
        String[] sections = content.split("### ");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            JobDeliveryReporter.DeliveryRecord record = new JobDeliveryReporter.DeliveryRecord();
            String[] lines = section.split("\n");
            for (String line : lines) {
                if (line.startsWith("公司：")) record.setCompany(line.replace("公司：", "").trim());
                else if (line.startsWith("岗位：")) record.setPosition(line.replace("岗位：", "").trim());
                else if (line.startsWith("薪资：")) record.setSalary(line.replace("薪资：", "").trim());
                else if (line.startsWith("工作地点：")) record.setLocation(line.replace("工作地点：", "").trim());
                else if (line.startsWith("招聘者：")) record.setAiGreeting(line.replace("招聘者：", "").trim());
                else if (line.startsWith("AI打招呼语：")) record.setAiGreeting(line.replace("AI打招呼语：", "").trim());
                else if (line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) record.setDate(line.trim());
                else if (line.startsWith("### ")) record.setDate(line.replace("### ", "").trim());
            }
            record.setStatus("success");
            record.setFailReason(null);
            try {
                if (record.getDate() != null) {
                    record.setTimestamp(sdf.parse(record.getDate()).getTime());
                }
            } catch (Exception ignore) {}
            records.add(record);
        }
        // 生成新版HTML和JSON
        JobDeliveryReporter.generateHtmlReport(records);
        System.out.println("新版HTML报告已生成并覆盖 delivery_report.html！");
    }
}
