package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class JobDetailsMdWriter {
    private static final String MD_PATH = "src/main/resources/job_details/job_details.md";

    /**
     * 追加一条投递记录到md文件
     */
    public static void appendRecord(String company, String job, String salary, String location, String recruiter, String aiGreeting, List<String> jobDutyList, String status, String failReason) {
        try (FileWriter fw = new FileWriter(MD_PATH, true)) {
            StringBuilder sb = new StringBuilder();
            sb.append("### ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
            sb.append("公司：").append(company == null ? "" : company).append("\n");
            sb.append("岗位：").append(job == null ? "" : job).append("\n");
            sb.append("薪资：").append(salary == null ? "" : salary).append("\n");
            sb.append("工作地点：").append(location == null ? "" : location).append("\n");
            sb.append("招聘者：").append(recruiter == null ? "" : recruiter).append("\n");
            sb.append("AI打招呼语：").append(aiGreeting == null ? "" : aiGreeting).append("\n");
            sb.append("投递状态：").append(status == null ? "" : status).append("\n");
            if (failReason != null && !failReason.isEmpty()) {
                sb.append("失败原因：").append(failReason).append("\n");
            }
            sb.append("**职位描述/职责/要求：**\n");
            if (jobDutyList != null && !jobDutyList.isEmpty()) {
                int idx = 1;
                for (String duty : jobDutyList) {
                    sb.append(idx++).append("、").append(duty).append("\n");
                }
            }
            sb.append("\n");
            fw.write(sb.toString());
        } catch (IOException e) {
            System.err.println("写入job_details.md失败: " + e.getMessage());
        }
    }

    // 兼容老接口
    public static void appendRecord(String company, String job, String salary, String location, String recruiter, String aiGreeting, List<String> jobDutyList) {
        appendRecord(company, job, salary, location, recruiter, aiGreeting, jobDutyList, "success", null);
    }
} 