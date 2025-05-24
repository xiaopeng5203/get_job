package boss;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import utils.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static boss.BossElementLocators.*;
import static utils.Bot.sendMessageByTime;
import static utils.Constant.CHROME_DRIVER;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href=
 * "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * Boss直聘自动投递
 */
public class Boss {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?";
    static Set<String> blackCompanies;
    static Set<String> blackRecruiters;
    static Set<String> blackJobs;
    static List<Job> resultList = new ArrayList<>();
    static String dataPath = ProjectRootResolver.rootPath + "/src/main/java/boss/data.json";
    static String cookiePath = ProjectRootResolver.rootPath + "/src/main/java/boss/cookie.json";
    static Date startDate;
    public static BossConfig config = BossConfig.init();
    static H5BossConfig h5Config = H5BossConfig.init();
    // 默认推荐岗位集合
    static List<Job> recommendJobs = new ArrayList<>();

    static String blacklistTimePath = ProjectRootResolver.rootPath + "/src/main/resources/blacklist_time.json";
    static Map<String, Map<String, Object>> blacklistTimeData = new HashMap<>();

    // 1. 新增全局变量
    static Set<String> appliedJobs = new HashSet<>();
    static boolean allowRepeatApply = false;

    // 1. 新增 data.json 路径常量
    static String appliedJobsKey = "appliedJobs";

    static void loadBlacklistTime() {
        File file = new File(blacklistTimePath);
        if (!file.exists()) {
            blacklistTimeData.put("companies", new HashMap<>());
            blacklistTimeData.put("jobs", new HashMap<>());
            blacklistTimeData.put("recruiters", new HashMap<>());
            saveBlacklistTime();
            return;
        }
        try {
            String json = new String(Files.readAllBytes(Paths.get(blacklistTimePath)), StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            for (String type : Arrays.asList("companies", "jobs", "recruiters")) {
                Map<String, Object> map = new HashMap<>();
                if (obj.has(type)) {
                    JSONObject sub = obj.getJSONObject(type);
                    for (String key : sub.keySet()) {
                        map.put(key, sub.getJSONObject(key).toMap());
                    }
                }
                blacklistTimeData.put(type, map);
            }
        } catch (Exception e) {
            log.warn("加载blacklist_time.json失败: {}", e.getMessage());
        }
    }

    static void saveBlacklistTime() {
        try {
            JSONObject obj = new JSONObject();
            for (String type : Arrays.asList("companies", "jobs", "recruiters")) {
                Map<String, Object> map = blacklistTimeData.getOrDefault(type, new HashMap<>());
                JSONObject sub = new JSONObject();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    sub.put(entry.getKey(), entry.getValue());
                }
                obj.put(type, sub);
            }
            Files.write(Paths.get(blacklistTimePath), obj.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("保存blacklist_time.json失败: {}", e.getMessage());
        }
    }

    // === 新增：黑名单项结构 ===
    public static class BlackItem {
        public String name;
        public Integer days; // 有效天数，null为永久
        public Long addTime; // 加入时间戳，null为永久
        public BlackItem(String name, Integer days, Long addTime) {
            this.name = name;
            this.days = days;
            this.addTime = addTime;
        }
        public BlackItem(String name) {
            this(name, null, null);
        }
        public boolean isExpired() {
            if (days == null || addTime == null) return false;
            long now = System.currentTimeMillis();
            return (now - addTime) > days * 24 * 60 * 60 * 1000L;
        }
        public long remainDays() {
            if (days == null || addTime == null) return Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            long remain = days * 24 * 60 * 60 * 1000L - (now - addTime);
            return remain > 0 ? remain / (24 * 60 * 60 * 1000L) : 0;
        }
    }
    // === 新增：黑名单加载与持久化 ===
    static List<BlackItem> blackCompanyItems = new ArrayList<>();
    static List<BlackItem> blackJobItems = new ArrayList<>();
    static List<BlackItem> blackRecruiterItems = new ArrayList<>();

    // === 新增：公司+招聘者黑名单项结构 ===
    public static class BlackCompanyRecruiterItem {
        public String company;
        public String recruiter;
        public Integer days; // 有效天数，null为永久
        public Long addTime; // 加入时间戳，null为永久
        public BlackCompanyRecruiterItem(String company, String recruiter, Integer days, Long addTime) {
            this.company = company;
            this.recruiter = recruiter;
            this.days = days;
            this.addTime = addTime;
        }
        public BlackCompanyRecruiterItem(String company, String recruiter) {
            this(company, recruiter, null, null);
        }
        public boolean isExpired() {
            if (days == null || addTime == null) return false;
            long now = System.currentTimeMillis();
            return (now - addTime) > days * 24 * 60 * 60 * 1000L;
        }
        public long remainDays() {
            if (days == null || addTime == null) return Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            long remain = days * 24 * 60 * 60 * 1000L - (now - addTime);
            return remain > 0 ? remain / (24 * 60 * 60 * 1000L) : 0;
        }
    }
    static List<BlackCompanyRecruiterItem> blackCompanyRecruiterItems = new ArrayList<>();

    static void loadBlackItems() {
        loadBlacklistTime();
        List<?> companies = config.getManualBlackCompanies();
        List<?> jobs = config.getManualBlackJobs();
        List<?> recruiters = config.getManualBlackRecruiters();
        List<?> companyRecruiters = config.getManualBlackCompanyRecruiters();
        blackCompanyItems = parseBlackListWithTime(companies, "companies");
        blackJobItems = parseBlackListWithTime(jobs, "jobs");
        blackRecruiterItems = parseBlackListWithTime(recruiters, "recruiters");
        blackCompanyRecruiterItems = parseBlackCompanyRecruiterListWithTime(companyRecruiters, "companyRecruiters");
    }

    static List<BlackItem> parseBlackListWithTime(List<?> list, String type) {
        List<BlackItem> result = new ArrayList<>();
        if (list == null) return result;
        Map<String, Object> timeMap = blacklistTimeData.getOrDefault(type, new HashMap<>());
        for (Object o : list) {
            if (o instanceof String) {
                String name = (String) o;
                Long addTime = null;
                Integer days = null;
                if (timeMap.containsKey(name)) {
                    Map t = (Map) timeMap.get(name);
                    addTime = t.get("addTime") == null ? null : Long.parseLong(t.get("addTime").toString());
                    days = t.get("days") == null ? null : Integer.parseInt(t.get("days").toString());
                }
                result.add(new BlackItem(name, days, addTime));
            } else if (o instanceof Map) {
                Map map = (Map) o;
                String name = (String) map.get("name");
                Integer days = map.get("days") == null ? null : Integer.parseInt(map.get("days").toString());
                Long addTime = null;
                if (timeMap.containsKey(name)) {
                    Map t = (Map) timeMap.get(name);
                    addTime = t.get("addTime") == null ? null : Long.parseLong(t.get("addTime").toString());
                }
                result.add(new BlackItem(name, days, addTime));
            }
        }
        return result;
    }

    static List<BlackCompanyRecruiterItem> parseBlackCompanyRecruiterListWithTime(List<?> list, String type) {
        List<BlackCompanyRecruiterItem> result = new ArrayList<>();
        if (list == null) return result;
        Map<String, Object> timeMap = blacklistTimeData.getOrDefault(type, new HashMap<>());
        for (Object o : list) {
            if (o instanceof String) {
                String s = (String) o;
                String[] arr = s.split("\\|");
                if (arr.length == 2) {
                    String company = arr[0];
                    String recruiter = arr[1];
                    Long addTime = null;
                    Integer days = null;
                    String key = company + "|" + recruiter;
                    if (timeMap.containsKey(key)) {
                        Map t = (Map) timeMap.get(key);
                        addTime = t.get("addTime") == null ? null : Long.parseLong(t.get("addTime").toString());
                        days = t.get("days") == null ? null : Integer.parseInt(t.get("days").toString());
                    }
                    result.add(new BlackCompanyRecruiterItem(company, recruiter, days, addTime));
                }
            } else if (o instanceof Map) {
                Map map = (Map) o;
                String company = (String) map.get("company");
                String recruiter = (String) map.get("recruiter");
                Integer days = map.get("days") == null ? null : Integer.parseInt(map.get("days").toString());
                Long addTime = null;
                String key = company + "|" + recruiter;
                if (timeMap.containsKey(key)) {
                    Map t = (Map) timeMap.get(key);
                    addTime = t.get("addTime") == null ? null : Long.parseLong(t.get("addTime").toString());
                }
                result.add(new BlackCompanyRecruiterItem(company, recruiter, days, addTime));
            }
        }
        return result;
    }

    static {
        try {
            // 检查dataPath文件是否存在，不存在则创建
            File dataFile = new File(dataPath);
            if (!dataFile.exists()) {
                // 确保父目录存在
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                // 创建文件并写入初始JSON结构
                Map<String, Set<String>> initialData = new HashMap<>();
                initialData.put("blackCompanies", new HashSet<>());
                initialData.put("blackRecruiters", new HashSet<>());
                initialData.put("blackJobs", new HashSet<>());
                String initialJson = customJsonFormat(initialData);
                Files.write(Paths.get(dataPath), initialJson.getBytes());
                log.info("创建数据文件: {}", dataPath);
            }

            // 检查cookiePath文件是否存在，不存在则创建
            File cookieFile = new File(cookiePath);
            if (!cookieFile.exists()) {
                // 确保父目录存在
                if (!cookieFile.getParentFile().exists()) {
                    cookieFile.getParentFile().mkdirs();
                }
                // 创建空的cookie文件
                Files.write(Paths.get(cookiePath), "[]".getBytes());
                log.info("创建cookie文件: {}", cookiePath);
            }
        } catch (IOException e) {
            log.error("创建文件时发生异常: {}", e.getMessage());
        }
    }

    // 添加缺失的方法
    private static void loadRepeatConfig() {
        try {
            allowRepeatApply = config.getAllowRepeatApply() != null ? config.getAllowRepeatApply() : false;
            log.info("重复投递配置: {}", allowRepeatApply ? "允许" : "不允许");
        } catch (Exception e) {
            log.warn("加载重复投递配置失败: {}", e.getMessage());
            allowRepeatApply = false;
        }
    }

    private static void login() {
        Page page = PlaywrightUtil.getPageObject();
        PlaywrightUtil.loadCookies(cookiePath);
        page.navigate(homeUrl);
        page.waitForLoadState();
        PlaywrightUtil.sleep(2);
        log.info("已访问Boss直聘首页并加载cookies");
    }

    private static void postH5JobByCityByPlaywright(String cityCode) {
        log.info("开始处理H5移动端岗位投递 - 城市代码: {}", cityCode);
        // H5投递逻辑的简化实现
        log.info("H5投递功能暂未完全实现");
    }

    private static void processRecommendJobs() {
        log.info("开始处理推荐岗位列表，共 {} 个岗位", recommendJobs.size());
        int processedCount = 0;
        int filteredCount = 0;
        int deliveredCount = 0;
        int filteredByJobBlacklist = 0;
        int filteredByKeyword = 0;
        int filteredByApplied = 0;
        int filteredByCompanyBlacklist = 0;
        int filteredBySalary = 0;
        int filteredByExperience = 0;
        List<String> filteredDetail = new ArrayList<>();
        
        for (Job job : recommendJobs) {
            try {
                processedCount++;
                log.info("正在处理第 {}/{} 个岗位: {} @ {}", processedCount, recommendJobs.size(), job.getJobName(), job.getCompanyName());
                
                // 岗位黑名单过滤
                if (isInBlackList(blackJobItems, job.getJobName(), "岗位", job.getJobName())) {
                    filteredCount++;
                    filteredByJobBlacklist++;
                    // isInBlackList方法已经输出了详细的过滤原因，这里不需要重复输出
                    utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "岗位黑名单");
                    java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                    if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                        for (String duty : job.getJobInfo().split("[\n;；]")) {
                            String d = duty.trim();
                            if (!d.isEmpty()) jobDutyList.add(d);
                        }
                    }
                    utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "岗位黑名单");
                    continue;
                }
                
                // 关键词过滤
                if (!isTargetJob(config.getKeywords().get(0), job.getJobName())) {
                    filteredCount++;
                    filteredByKeyword++;
                    String msg = String.format("未投递原因：岗位【%s】不包含任何配置关键词%s", job.getJobName(), config.getKeywords());
                    log.info(msg);
                    filteredDetail.add(msg);
                    utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "关键词不符");
                    java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                    if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                        for (String duty : job.getJobInfo().split("[\n;；]")) {
                            String d = duty.trim();
                            if (!d.isEmpty()) jobDutyList.add(d);
                        }
                    }
                    utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "关键词不符");
                    continue;
                }
                
                // 已投递过滤
                String uniqueKey = getUniqueKey(job);
                if (!allowRepeatApply && isAppliedJobValid(uniqueKey)) {
                    filteredCount++;
                    filteredByApplied++;
                    String msg = String.format("未投递原因：岗位【%s @ %s】已投递过（唯一标识：%s）", job.getJobName(), job.getCompanyName(), uniqueKey);
                    log.info(msg);
                    filteredDetail.add(msg);
                    utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "已投递过");
                    java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                    if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                        for (String duty : job.getJobInfo().split("[\n;；]")) {
                            String d = duty.trim();
                            if (!d.isEmpty()) jobDutyList.add(d);
                        }
                    }
                    utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "已投递过");
                    continue;
                }
                
                // 公司黑名单过滤
                if (isInBlackList(blackCompanyItems, job.getCompanyName(), "公司", job.getJobName())) {
                    filteredCount++;
                    filteredByCompanyBlacklist++;
                    // isInBlackList方法已经输出了详细的过滤原因，这里不需要重复输出
                    utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "公司黑名单");
                    java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                    if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                        for (String duty : job.getJobInfo().split("[\n;；]")) {
                            String d = duty.trim();
                            if (!d.isEmpty()) jobDutyList.add(d);
                        }
                    }
                    utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "公司黑名单");
                    continue;
                }
                
                // 薪资过滤
                if (isSalaryNotExpected(job.getSalary())) {
                    filteredCount++;
                    filteredBySalary++;
                    String msg = String.format("未投递原因：岗位【%s】薪资【%s】与期望薪资%s均无重叠", job.getJobName(), job.getSalary(), getSalaryList());
                    log.info(msg);
                    filteredDetail.add(msg);
                    utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "薪资不符");
                    java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                    if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                        for (String duty : job.getJobInfo().split("[\n;；]")) {
                            String d = duty.trim();
                            if (!d.isEmpty()) jobDutyList.add(d);
                        }
                    }
                    utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "薪资不符");
                    continue;
                }
                
                // 经验过滤（如有经验字段）
                try {
                    java.lang.reflect.Method getExp = job.getClass().getMethod("getExperience");
                    Object jobExp = getExp.invoke(job);
                    if (jobExp != null && config.getExperience() != null && !config.getExperience().isEmpty()) {
                        // 只要有一个经验要求匹配即可
                        boolean match = false;
                        for (String exp : config.getExperience()) {
                            if (jobExp.toString().contains(exp)) {
                                match = true;
                                break;
                            }
                        }
                        if (!match) {
                            filteredCount++;
                            filteredByExperience++;
                            String msg = String.format("未投递原因：岗位【%s】工作经验【%s】不匹配期望【%s}", job.getJobName(), jobExp, config.getExperience());
                            log.info(msg);
                            filteredDetail.add(msg);
                            utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "经验不符");
                            java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                            if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                                for (String duty : job.getJobInfo().split("[\n;；]")) {
                                    String d = duty.trim();
                                    if (!d.isEmpty()) jobDutyList.add(d);
                                }
                            }
                            utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "经验不符");
                            continue;
                        }
                    }
                } catch (Exception ignore) {}
                
                log.info("岗位【{} @ {}】通过所有过滤条件，开始投递", job.getJobName(), job.getCompanyName());
                deliveredCount++;
                // 处理推荐岗位投递
                Page jobPage = PlaywrightUtil.getPageObject().context().newPage();
                try {
                    jobPage.navigate(homeUrl + job.getHref());
                    jobPage.waitForLoadState();
                    PlaywrightUtil.sleep(2);
                    log.info("开始投递推荐岗位: {} @ {}", job.getJobName(), job.getCompanyName());
                    boolean success = attemptJobDelivery(job, jobPage);
                    java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                    if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                        for (String duty : job.getJobInfo().split("[\n;；]")) {
                            String d = duty.trim();
                            if (!d.isEmpty()) jobDutyList.add(d);
                        }
                    }
                    if (success) {
                        resultList.add(job);
                        log.info("✅ 推荐岗位投递成功: {} @ {}", job.getJobName(), job.getCompanyName());
                        utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "success", null);
                        utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "success", null);
                    } else {
                        log.info("❌ 推荐岗位投递失败: {} @ {}", job.getJobName(), job.getCompanyName());
                        utils.JobDeliveryReporter.recordDelivery(job, "自动投递", "BOSS", "fail", "沟通按钮缺失/页面异常");
                        utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "fail", "沟通按钮缺失/页面异常");
                    }
                } finally {
                    jobPage.close();
                }
                Thread.sleep(3000 + (int)(Math.random() * 2000));
            } catch (Exception e) {
                log.error("处理推荐岗位失败: {} @ {} - {}", job.getJobName(), job.getCompanyName(), e.getMessage());
            }
        }
        log.info("推荐岗位处理完成 - 总计：{}，过滤：{}，投递：{}", processedCount, filteredCount, deliveredCount);
        log.info("过滤明细：岗位黑名单:{}，关键词:{}，已投递:{}，公司黑名单:{}，薪资:{}，经验:{}", filteredByJobBlacklist, filteredByKeyword, filteredByApplied, filteredByCompanyBlacklist, filteredBySalary, filteredByExperience);
        if (!filteredDetail.isEmpty()) {
            log.info("详细过滤原因如下：\n{}", String.join("\n", filteredDetail));
        }
    }

    /**
     * 处理岗位详情（合并AI打招呼语、图片简历、兜底选择器等全部逻辑）
     * 兼容新旧功能，供attemptJobDelivery调用
     */
    private static boolean attemptJobDelivery(Job job, Page page) {
        try {
            page.waitForLoadState();
            if (isLimit()) {
                log.info("检测到投递上限，停止投递");
                return false;
            }
            // ===== 详情页字段多选择器兜底抓取（融合bak版全部细节+SEO兜底） =====
            // 1. 薪资
            String salary = "";
            String[] salarySelectors = {
                ".job-primary .salary", ".job-salary", ".salary", ".job-limit .red",
                ".job-primary .red", ".salary-text", ".job-item-salary", ".price"
            };
            for (String selector : salarySelectors) {
                try {
                    Locator loc = page.locator(selector);
                    if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        salary = loc.first().textContent().trim();
                        if (!salary.isEmpty()) {
                            break;
                        }
                    }
                } catch (Exception ex) {}
            }
            // 2. 职位描述
            String jobDesc = "";
            String[] descSelectors = {
                ".job-detail-section", ".job-sec-text", ".job-sec-content", ".job-detail",
                ".detail-content", ".job-description", ".job-content", ".text-desc",
                ".job-detail .text", ".job-detail-text", ".job-detail-content"
            };
            for (String selector : descSelectors) {
                try {
                    Locator loc = page.locator(selector);
                    if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        jobDesc = loc.first().textContent().trim();
                        if (!jobDesc.isEmpty()) {
                            break;
                        }
                    }
                } catch (Exception ex) {}
            }
            // 3. 招聘者
            String recruiter = "";
            String hrActiveStatus = "";
            String[] recruiterSelectors = {
                ".job-boss-info h2.name", ".job-author .name", ".boss-info .name",
                ".recruiter-name", ".boss-name", ".hr-name", ".contact-name"
            };
            for (String selector : recruiterSelectors) {
                try {
                    Locator loc = page.locator(selector);
                    if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        recruiter = loc.first().textContent().trim();
                        if (!recruiter.isEmpty()) {
                            // 尝试获取招聘者活跃状态（通常在同一区域）
                            try {
                                Locator activeElem = page.locator(".job-boss-info .active-time, .boss-info .active-time, .active-status");
                                if (activeElem.isVisible()) {
                                    hrActiveStatus = activeElem.textContent().trim();
                                }
                            } catch (Exception ignore) {}
                            break;
                        }
                    }
                } catch (Exception ex) {}
            }
            // 4. 岗位名称
            String jobName = "";
            String[] jobNameSelectors = { ".job-name", ".job-title", "h1", "span[class*='job-name']" };
            for (String selector : jobNameSelectors) {
                try {
                    Locator loc = page.locator(selector);
                    if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        jobName = loc.first().textContent().trim();
                        if (!jobName.isEmpty()) break;
                    }
                } catch (Exception ex) {}
            }
            // 5. 公司名称
            String companyName = "";
            String[] companySelectors = {
                ".company-name", ".company", "span[class*='company']", "a.company-link", "div.company-info__name", "div.info-company .name", "div.company-title", "div.company-content .name", "div.company-content .company-name"
            };
            for (String selector : companySelectors) {
                try {
                    Locator loc = page.locator(selector);
                    if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        companyName = loc.first().textContent().trim();
                        if (!companyName.isEmpty()) break;
                    }
                } catch (Exception ex) {}
            }
            
            // ===== SEO兜底：<title>/<meta>正则提取 =====
            if ((jobName == null || jobName.isEmpty()) || (companyName == null || companyName.isEmpty())) {
                String html = page.content();
                // 1. <title>兜底
                java.util.regex.Pattern titlePattern = java.util.regex.Pattern.compile("<title>「(.+?)招聘」_(.+?)招聘-BOSS直聘</title>");
                java.util.regex.Matcher m = titlePattern.matcher(html);
                if (m.find()) {
                    if (jobName == null || jobName.isEmpty()) jobName = m.group(1);
                    if (companyName == null || companyName.isEmpty()) companyName = m.group(2);
                    log.info("通过<title>兜底抓取公司/岗位：{}-{}", companyName, jobName);
                } else {
                    // 2. <meta name="description">兜底
                    java.util.regex.Pattern metaPattern = java.util.regex.Pattern.compile("<meta name=\"description\" content=\"(.+?)招聘，薪资：(.+?)，地点：(.+?)，要求：(.+?)，学历：(.+?)，福利：(.+?)，(.+?)刚刚在线");
                    java.util.regex.Matcher metaM = metaPattern.matcher(html);
                    if (metaM.find()) {
                        if (jobName == null || jobName.isEmpty()) jobName = metaM.group(1);
                        // 公司名再兜底
                        java.util.regex.Pattern companyPattern = java.util.regex.Pattern.compile("_(.+?)" + jobName);
                        java.util.regex.Matcher companyM = companyPattern.matcher(metaM.group(1));
                        if ((companyName == null || companyName.isEmpty()) && companyM.find()) {
                            companyName = companyM.group(1);
                        }
                        log.info("通过<meta>兜底抓取公司/岗位：{}-{}", companyName, jobName);
                    }
                }
            }
            // ===== 兜底失败处理 =====
            if (jobName == null || jobName.isEmpty()) {
                log.error("详情页抓取岗位名称失败，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                try {
                    String screenshotPath = "/tmp/boss_jobname_error_" + System.currentTimeMillis() + ".png";
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                    log.info("已保存页面截图到: {}", screenshotPath);
                } catch (Exception ssEx) { log.error("保存截图失败: {}", ssEx.getMessage()); }
                return false;
            }
            if (companyName == null || companyName.isEmpty()) {
                log.error("详情页抓取公司名称失败，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                try {
                    String screenshotPath = "/tmp/boss_company_error_" + System.currentTimeMillis() + ".png";
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                    log.info("已保存页面截图到: {}", screenshotPath);
                } catch (Exception ssEx) { log.error("保存截图失败: {}", ssEx.getMessage()); }
                return false;
            }
            if (salary == null || salary.isEmpty()) {
                log.error("详情页抓取薪资失败，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                try {
                    String screenshotPath = "/tmp/boss_salary_error_" + System.currentTimeMillis() + ".png";
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                    log.info("已保存页面截图到: {}", screenshotPath);
                } catch (Exception ssEx) { log.error("保存截图失败: {}", ssEx.getMessage()); }
                return false;
            }
            if (jobDesc == null || jobDesc.isEmpty()) {
                log.error("详情页抓取职位描述失败，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                try {
                    String screenshotPath = "/tmp/boss_desc_error_" + System.currentTimeMillis() + ".png";
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                    log.info("已保存页面截图到: {}", screenshotPath);
                } catch (Exception ssEx) { log.error("保存截图失败: {}", ssEx.getMessage()); }
                return false;
            }
            if (recruiter == null || recruiter.isEmpty()) {
                log.error("详情页抓取招聘者失败，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                try {
                    String screenshotPath = "/tmp/boss_recruiter_error_" + System.currentTimeMillis() + ".png";
                    page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                    log.info("已保存页面截图到: {}", screenshotPath);
                } catch (Exception ssEx) { log.error("保存截图失败: {}", ssEx.getMessage()); }
                return false;
            }
            // 强制写回job对象
            job.setJobName(jobName);
            job.setCompanyName(companyName);
            job.setSalary(salary);
            job.setRecruiter(recruiter.replaceAll("\\s+", ""));
            job.setJobArea(jobDesc);
            job.setJobKeywordTag(jobDesc);

            // 汇总主要信息到一行日志显示
            log.info("岗位详情汇总： 公司[{}] 岗位[{}] 薪资[{}] 招聘者[{}] HR活跃状态[{}]", 
                companyName, jobName, salary, recruiter.replaceAll("\\s+", ""), hrActiveStatus);

            // 输出开始投递岗位的日志
            log.info("开始投递岗位: {}-{}", companyName, jobName);

            // ===== 统一过滤逻辑（通过后才投递） =====
            // 关键词过滤（任意关键词通过）
            boolean matchAnyKeyword = false;
            for (String k : config.getKeywords()) {
                if (jobName != null && jobName.toLowerCase().contains(k.toLowerCase())) {
                    matchAnyKeyword = true;
                    break;
                }
            }
            if (!matchAnyKeyword) {
                log.info("❌ 岗位【{} @ {}】被过滤，原因：岗位名称不包含任何配置关键词{}", jobName, companyName, config.getKeywords());
                return false;
            }
            // 岗位黑名单
            if (isInBlackList(blackJobItems, jobName, "岗位", jobName)) {
                log.info("❌ 岗位【{} @ {}】被过滤，原因：岗位黑名单", jobName, companyName);
                return false;
            }
            // 已投递过滤
            if (!allowRepeatApply && isAppliedJobValid(getUniqueKey(job))) {
                String uniqueKey = getUniqueKey(job);
                log.info("❌ 岗位【{} @ {}】被过滤，原因：已投递过（唯一标识：{}）", jobName, companyName, uniqueKey);
                return false;
            }
            // 公司黑名单
            if (isInBlackList(blackCompanyItems, companyName, "公司", jobName)) {
                log.info("❌ 岗位【{} @ {}】被过滤，原因：公司黑名单", jobName, companyName);
                return false;
            }
            // 薪资过滤
            if (isSalaryNotExpected(salary)) {
                log.info("❌ 岗位【{} @ {}】被过滤，原因：薪资不符", jobName, companyName);
                return false;
            }
            // 经验过滤（如有）
            try {
                java.lang.reflect.Method getExp = job.getClass().getMethod("getExperience");
                Object jobExp = getExp.invoke(job);
                if (jobExp != null && config.getExperience() != null && !config.getExperience().isEmpty()) {
                    boolean match = false;
                    for (String exp : config.getExperience()) {
                        if (jobExp.toString().contains(exp)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        log.info("❌ 岗位【{} @ {}】被过滤，原因：工作经验要求【{}】不匹配期望经验{}", jobName, companyName, jobExp, config.getExperience());
                        return false;
                    }
                }
            } catch (Exception ignore) {}

            // HR活跃状态过滤
            if (config.getDeadStatus() != null && !config.getDeadStatus().isEmpty() && !hrActiveStatus.isEmpty()) {
                for (String dead : config.getDeadStatus()) {
                    if (hrActiveStatus.contains(dead)) {
                        log.info("❌ 岗位【{} @ {}】被过滤，原因：HR活跃状态为[{}]", jobName, companyName, hrActiveStatus);
                        return false;
                    }
                }
            }

            // ===== AI打招呼语生成 =====
            if (config.getEnableAI() && ai.AiConfig.init() != null) {
                ai.AiFilter filter = checkJob(job.getJobName(), job.getJobName(), jobDesc);
                if (!filter.isMatch()) {
                    log.info("❌ 岗位【{} @ {}】被过滤，原因：AI判断不匹配", jobName, companyName);
                    return false;
                }
                job.setJobInfo(filter.getResult());
                log.info("AI打招呼语已生成");
            }

            // ===== 聊天与图片简历上传（多重兼容） =====
            Locator chatButton = page.locator(BossElementLocators.CHAT_BUTTON).first();
            if (!chatButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                log.info("无法找到沟通按钮，公司[{}] 岗位[{}]", job.getCompanyName(), job.getJobName());
                return false;
            }
            chatButton.click();
            boolean chatInputFound = false;
            Locator chatInput = null;
            try {
                page.waitForSelector(BossElementLocators.CHAT_INPUT, new Page.WaitForSelectorOptions().setTimeout(5000));
                chatInput = page.locator(BossElementLocators.CHAT_INPUT);
                if (chatInput.isVisible()) {
                    log.info("检测到旧版聊天输入框");
                    chatInputFound = true;
                }
            } catch (Exception e) {
                log.info("未找到旧版聊天输入框，尝试其他选择器...");
            }
            if (!chatInputFound) {
                for (String selector : BossElementLocators.CHAT_INPUT_ALT.split(", ")) {
                try {
                        Locator altInput = page.locator(selector);
                        if (altInput.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                            chatInput = altInput;
                            chatInputFound = true;
                            log.info("检测到替代聊天输入框: {}", selector);
                            break;
                        }
                    } catch (Exception ex) {}
                }
            }
            if (!chatInputFound) {
                log.info("未找到任何聊天输入框，检查是否有弹窗...");
                try {
                    Locator dialog = page.locator(BossElementLocators.POPUP_CONTAINER);
                    if (dialog.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                        String dialogText = dialog.textContent();
                        log.info("检测到弹窗内容: {}", dialogText);
                        Locator questionDialog = page.locator(BossElementLocators.POPUP_QUESTION_DIALOG);
                        if (questionDialog.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                            log.info("检测到问题对话框，尝试填写并提交...");
                            Locator questionInput = page.locator(BossElementLocators.POPUP_QUESTION_INPUT);
                            if (questionInput.isVisible()) {
                                questionInput.fill("您好，我对贵公司的岗位非常感兴趣，希望能有机会与您进一步交流。");
                                PlaywrightUtil.sleep(1);
                                Locator confirmBtn = page.locator(BossElementLocators.POPUP_CONFIRM_BUTTON);
                                if (confirmBtn.isVisible()) {
                                    confirmBtn.click();
                    PlaywrightUtil.sleep(2);
                                }
                            }
                        } else {
                            Locator confirmBtn = page.locator(BossElementLocators.POPUP_CONFIRM_BUTTON);
                            if (confirmBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                log.info("点击弹窗确认按钮");
                                confirmBtn.click();
                                PlaywrightUtil.sleep(2);
                            } else {
                                Locator closeBtn = page.locator(BossElementLocators.POPUP_CLOSE_BUTTON);
                                if (closeBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                    log.info("点击弹窗关闭按钮");
                                    closeBtn.click();
                                    PlaywrightUtil.sleep(2);
                                }
                            }
                        }
                        for (String selector : (BossElementLocators.CHAT_INPUT + ", " + BossElementLocators.CHAT_INPUT_ALT).split(", ")) {
                            try {
                                Locator input = page.locator(selector);
                                if (input.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                    chatInput = input;
                                    chatInputFound = true;
                                    log.info("处理弹窗后找到聊天输入框: {}", selector);
                                    break;
                                }
                            } catch (Exception inputEx) {}
                        }
                    }
                } catch (Exception dialogEx) {
                    log.error("处理弹窗失败: {}", dialogEx.getMessage());
                }
                if (!chatInputFound) {
                    log.error("尝试所有方法后仍未找到聊天输入框，放弃当前岗位。页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                    try {
                        String screenshotPath = "/tmp/boss_chatinput_error_" + System.currentTimeMillis() + ".png";
                        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                        log.info("已保存页面截图到: {}", screenshotPath);
                    } catch (Exception ssEx) { log.error("保存截图失败: {}", ssEx.getMessage()); }
                    return false;
                }
            }
            // 填写打招呼语
            String sayHi = config.getSayHi();
            if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                sayHi = job.getJobInfo();
            }
            try {
                chatInput.fill(sayHi);
                log.info("已发送打招呼语");
        } catch (Exception e) {
                log.error("填写打招呼语失败，尝试其他方式: {}", e.getMessage());
                try {
                    page.evaluate("el => el.textContent = '" + sayHi.replace("'", "\\'") + "'", chatInput);
                    log.info("已发送打招呼语");
                } catch (Exception jsEx) {
                    log.error("JavaScript填写失败: {}", jsEx.getMessage());
                    return false;
                }
            }
            // 点击发送按钮
            boolean sendSuccess = false;
            try {
                Locator sendBtn = page.locator(BossElementLocators.SEND_BUTTON);
                if (sendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    sendBtn.click();
                    sendSuccess = true;
                    log.info("点击旧版发送按钮");
                }
            } catch (Exception e) {
                log.info("未找到旧版发送按钮，尝试其他选择器...");
            }
            if (!sendSuccess) {
                for (String selector : BossElementLocators.SEND_BUTTON_ALT.split(", ")) {
                    try {
                        Locator altSendBtn = page.locator(selector);
                        if (altSendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                            altSendBtn.click();
                            sendSuccess = true;
                            log.info("点击替代发送按钮: {}", selector);
                            break;
                        }
                    } catch (Exception ex) {}
                }
            }
            if (!sendSuccess) {
                try {
                    chatInput.press("Enter");
                    sendSuccess = true;
                    log.info("使用Enter键发送消息");
                } catch (Exception e) {
                    log.error("使用Enter键发送失败: {}", e.getMessage());
                }
            }
            PlaywrightUtil.sleep(1);
            log.info("已向公司[{}] 岗位[{}] 发送消息: {}", job.getCompanyName(), job.getJobName(), sayHi);
            // ===== 图片简历上传 =====
            if (config.getSendImgResume() != null && config.getSendImgResume()) {
                boolean resumeSent = false;
                boolean needSendBtn = false;
                String resumePath = "/Users/gupeng/get_jobs-feature-job-boss-mobile/src/main/resources/resume.jpg";
                String[] alternativeSelectors = {
                    "input[type='file']",
                    ".upload-btn input[type='file']",
                    ".image-upload input[type='file']",
                    ".file-upload input[type='file']",
                    "div[aria-label='发送图片'] input",
                    "div[title='发送图片'] input",
                    ".chat-tools .image input",
                    ".tool-item input[type='file']"
                };
                try {
                    Locator imageUploadInput = page.locator(BossElementLocators.IMAGE_UPLOAD);
                    if (imageUploadInput.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        File imageFile = new File(resumePath);
                        if (imageFile.exists()) {
                            imageUploadInput.setInputFiles(Paths.get(imageFile.getAbsolutePath()));
                            resumeSent = true;
                            log.info("已通过标准选择器发送图片简历");
                        }
                    }
                    if (!resumeSent) {
                        for (String selector : alternativeSelectors) {
                            try {
                                Locator altUploadInput = page.locator(selector);
                                if (altUploadInput.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                    File imageFile = new File(resumePath);
                                    if (imageFile.exists()) {
                                        altUploadInput.setInputFiles(Paths.get(imageFile.getAbsolutePath()));
                                        resumeSent = true;
                                        log.info("已通过替代选择器[{}]发送图片简历", selector);
                                        break;
                                    }
                                }
                            } catch (Exception ex) {}
                        }
                    }
                    if (!resumeSent) {
                        String[] imageButtonSelectors = {
                            "button.btn-image",
                            ".image-btn",
                            ".icon-image",
                            "div[aria-label='发送图片']",
                            "div[title='发送图片']",
                            ".tool-item:has(.icon-image)",
                            ".chat-tools .image"
                        };
                        for (String btnSelector : imageButtonSelectors) {
                            try {
                                Locator imageBtn = page.locator(btnSelector);
                                if (imageBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                    imageBtn.click();
                                    PlaywrightUtil.sleep(1);
                                    for (String inputSelector : alternativeSelectors) {
                                        try {
                                            Locator fileInput = page.locator(inputSelector);
                                            if (fileInput.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                                File imageFile = new File(resumePath);
                                                if (imageFile.exists()) {
                                                    fileInput.setInputFiles(Paths.get(imageFile.getAbsolutePath()));
                                                    resumeSent = true;
                                                    needSendBtn = true;
                                                    log.info("已通过按钮点击后的选择器[{}]发送图片简历", inputSelector);
                                                    break;
                                                }
                                            }
                                        } catch (Exception ex) {}
                                    }
                                    if (resumeSent) break;
                                }
                            } catch (Exception ex) {}
                        }
                    }
                    if (resumeSent && needSendBtn) {
                        PlaywrightUtil.sleep(2); // 上传后等待2秒
                        try {
                            Locator sendBtn = page.locator(BossElementLocators.SEND_BUTTON);
                            if (sendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                sendBtn.click();
                                log.info("图片上传后自动点击发送按钮");
                            } else {
                                for (String selector : BossElementLocators.SEND_BUTTON_ALT.split(", ")) {
                                    try {
                                        Locator altSendBtn = page.locator(selector);
                                        if (altSendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                            altSendBtn.click();
                                            log.info("图片上传后自动点击替代发送按钮: {}", selector);
                                            break;
                                        }
                                    } catch (Exception ex) {}
                                }
                            }
                        } catch (Exception e) {
                            log.warn("图片上传后自动点击发送按钮失败: {}", e.getMessage());
                        }
                    } else if (!resumeSent) {
                        log.warn("未能找到任何可用的图片上传元素，跳过发送图片简历");
                    }
                    if (resumeSent) {
                        try {
                            page.waitForSelector(".chat-img, .image-thumb, .img-preview, .image-preview, .uploaded-image", new Page.WaitForSelectorOptions().setTimeout(8000));
                            log.info("图片缩略图已出现，图片上传完成");
                        } catch (Exception e) {
                            log.info("未找到图片缩略图，强制等待2秒确保图片上传完成");
                            PlaywrightUtil.sleep(2); // 由5秒改为2秒
                        }
                    }
                } catch (Exception e) {
                    log.error("发送图片简历时出错: {}", e.getMessage());
                }
            }
            return true;
        } catch (Exception e) {
            log.error("处理岗位详情异常: {}", e.getMessage());
            return false;
        }
    }

    // 添加缺失的isLimit方法
    private static boolean isLimit() {
        // 简单实现，可以根据需要扩展
        return false;
    }

    private static Integer[] parseSalaryRange(String salaryText) {
        if (salaryText == null || salaryText.trim().isEmpty()) {
            return new Integer[0];
        }
        
        try {
            // 处理"50K以上"或"50k+"格式
            if (salaryText.contains("以上") || salaryText.contains("+")) {
                String pattern = "(\\d+)[Kk]";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(salaryText);
                
                if (m.find()) {
                    int min = Integer.parseInt(m.group(1));
                    // 用一个足够大的值表示"以上"
                    int max = 9999;
                    log.info("解析'以上'格式薪资 [{}] 为范围: [{}-{}]", salaryText, min, max);
                    return new Integer[]{min, max};
                }
            }
            
            // 匹配类似 "20-30K·13薪" 或 "15K-25K" 的格式，忽略后面的额外信息
            String pattern = "(\\d+)[-~](\\d+)[Kk]";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(salaryText);
            
            if (m.find()) {
                int min = Integer.parseInt(m.group(1));
                int max = Integer.parseInt(m.group(2));
                log.info("解析区间格式薪资 [{}] 为范围: [{}-{}]", salaryText, min, max);
                return new Integer[]{min, max};
            }
            
            // 匹配单个数字如 "25K·13薪"，忽略后面的额外信息
            pattern = "(\\d+)[Kk]";
            p = java.util.regex.Pattern.compile(pattern);
            m = p.matcher(salaryText);
            
            if (m.find()) {
                int salary = Integer.parseInt(m.group(1));
                log.info("解析单值格式薪资 [{}] 为: [{}]", salaryText, salary);
                return new Integer[]{salary};
            }
            
        } catch (Exception e) {
            log.info("解析薪资范围失败: {}", salaryText);
        }
        
        return new Integer[0];
    }

    private static boolean isJobInCity(Job job, String cityName) {
        if (job.getJobArea() == null || cityName == null) {
            return true; // 无法判断时默认通过
        }
        
        String jobArea = job.getJobArea().toLowerCase();
        String targetCity = cityName.toLowerCase();
        
        // 检查是否包含目标城市名称
        return jobArea.contains(targetCity) || targetCity.contains(jobArea.split("-")[0]);
    }

    private static void addToBlackCompanyRecruiterBlacklist(String companyName, String recruiterName, int days) {
        try {
            BlackCompanyRecruiterItem item = new BlackCompanyRecruiterItem(companyName, recruiterName, days, System.currentTimeMillis());
            blackCompanyRecruiterItems.add(item);
            
            // 同时保存到时间数据中
            Map<String, Object> companyRecruiterMap = blacklistTimeData.getOrDefault("companyRecruiters", new HashMap<>());
            String key = companyName + "|" + recruiterName;
            Map<String, Object> timeInfo = new HashMap<>();
            timeInfo.put("addTime", System.currentTimeMillis());
            timeInfo.put("days", days);
            companyRecruiterMap.put(key, timeInfo);
            blacklistTimeData.put("companyRecruiters", companyRecruiterMap);
            
            saveBlacklistTime();
            log.info("已将公司+招聘者组合加入黑名单: {} | {} ({}天)", companyName, recruiterName, days);
        } catch (Exception e) {
            log.error("添加公司+招聘者黑名单失败: {}", e.getMessage());
        }
    }

    private static boolean isInBlackList(List<BlackItem> blackItems, String name, String type, String contextInfo) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        for (BlackItem item : blackItems) {
            if (item.isExpired()) {
                continue; // 跳过已过期的项目
            }
            
            if (name.contains(item.name) || item.name.contains(name)) {
                long remainDays = item.remainDays();
                if (remainDays == Long.MAX_VALUE) {
                    log.info("❌ {}【{}】被过滤，原因：匹配黑名单项【{}】（永久黑名单）", type, name, item.name);
                } else {
                    log.info("❌ {}【{}】被过滤，原因：匹配黑名单项【{}】（剩余{}天）", type, name, item.name, remainDays);
                }
                return true;
            }
        }
        
        return false;
    }

    private static boolean isTargetJob(String keyword, String jobName) {
        if (keyword == null || jobName == null) {
            return false;
        }
        
        return jobName.toLowerCase().contains(keyword.toLowerCase());
    }

    private static boolean isSalaryNotExpected(String salary) {
        if (salary == null || salary.trim().isEmpty()) {
            return false; // 无法判断时不过滤
        }
        
        try {
            Integer[] range = parseSalaryRange(salary);
            if (range.length == 0) {
                log.info("薪资[{}]无法解析，默认不过滤", salary);
                return false;
            }
            
            // 获取配置的薪资期望
            List<String> expectedSalaries = getSalaryList();
            if (expectedSalaries.isEmpty()) {
                log.info("未配置期望薪资，默认不过滤");
                return false;
            }
            
            // 检查是否在期望薪资范围内（只要与任一配置薪资区间有重叠即通过）
                    int jobMin = range[0];
                    int jobMax = range.length > 1 ? range[1] : range[0];
            
            log.info("岗位薪资[{}]解析为区间[{}-{}]", salary, jobMin, jobMax);
            
            for (String expectedSalary : expectedSalaries) {
                Integer[] expectedRange = parseSalaryRange(expectedSalary);
                if (expectedRange.length == 0) {
                    log.info("期望薪资[{}]无法解析，跳过", expectedSalary);
                    continue;
                }
                
                    int expectedMin = expectedRange[0];
                int expectedMax = expectedRange.length > 1 ? expectedRange[1] : expectedRange[0];
                    
                log.info("期望薪资[{}]解析为区间[{}-{}]", expectedSalary, expectedMin, expectedMax);
                
                // 只要有重叠，就认为符合期望（返回false）
                    if (jobMax >= expectedMin && jobMin <= expectedMax) {
                    log.info("薪资[{}]与期望[{}]有重叠，通过验证", salary, expectedSalary);
                    return false;
                }
            }
            
            log.info("❌ 薪资[{}]（区间[{}-{}]）与所有期望薪资{}均无重叠", salary, jobMin, jobMax, expectedSalaries);
            return true; // 不符合任何期望区间
        } catch (Exception e) {
            log.info("薪资检查异常: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isAppliedJobValid(String uniqueKey) {
        return appliedJobs.contains(uniqueKey);
    }

    public static void main(String[] args) {
        // 设置基本的日志上下文
        LogUtils.Context.setPlatform("BOSS");
        startDate = new Date();
        log.info("程序启动，开始执行Boss直聘自动投递");
        
        try {
            // 首次启动时执行数据迁移（已移除，无需任何操作）
            // 直接进入主流程
            loadBlackItems();
            loadRepeatConfig();
            log.info("Boss直聘自动投递程序启动 - 版本: 企业级AI智能求职自动化平台");
            if (PlaywrightUtil.getPageObject() == null) {
                PlaywrightUtil.init();
            }
            login();
            if (config.getH5Jobs()) {
                h5Config.getCityCode().forEach(Boss::postH5JobByCityByPlaywright);
            }
            if (recommendJobs.isEmpty() && config.getRecommendJobs()) {
                getRecommendJobs();
                processRecommendJobs();
            }
            config.getCityCode().forEach(Boss::postJobByCityByPlaywright);
            log.info(resultList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}",
                    resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
            if (!config.getDebugger()) {
                printResult();
            }
        } catch (Exception e) {
            log.error("程序启动时发生异常: {}", e.getMessage());
        }
        
        // 初始化输出文件
        printResult();
        log.info("程序执行完成");
    }

    private static void printResult() {
        StringBuilder sb = new StringBuilder();
        sb.append("【Boss直聘】今日投递汇总\n");
        sb.append(String.format("共发起 %d 个聊天，用时 %s\n", resultList.size(), formatDuration(startDate, new Date())));
        sb.append("-------------------------\n");
        int idx = 1;
        for (Job job : resultList) {
            sb.append(String.format("%d. %s @ %s | %s | %s\n", idx++, job.getJobName(), job.getCompanyName(), job.getSalary() == null ? "" : job.getSalary(), job.getJobArea() == null ? "" : job.getJobArea()));
        }
        sb.append("-------------------------\n");
        sb.append("祝你早日找到心仪的工作！");
        String message = sb.toString();
        log.info(message);
        utils.Bot.sendBark(message); // 统一Bark推送格式
        saveData(dataPath);
        resultList.clear();
        resultList.clear();
        
        // 生成可视化投递报告
        try {
            utils.JobDeliveryReporter.generateReport();
            log.info("可视化投递报告已生成：src/main/resources/delivery_report.html");
        } catch (Exception e) {
            log.error("生成可视化投递报告失败: {}", e.getMessage());
        }         if (!config.getDebugger()) {
            PlaywrightUtil.close();
        }
    }

    /**
     * 推荐岗位处理方法
     * 处理逻辑：
     * 1. 首先访问推荐岗位页面，根据配置的recommendDefaultCity设置城市
     * 2. 优先处理页面最前面的推荐Tab，不管配置文件中的优先级是什么
     * 3. 遍历获取所有岗位，然后根据配置文件中的关键词等过滤规则进行过滤
     * 4. 投递符合条件的岗位
     * 5. 然后按照配置文件中recommendTabPriority指定的Tab优先级顺序，处理后续Tab
     * 6. 最后处理剩余未配置优先级的Tab
     * 
     * 注意：推荐岗位的城市配置(recommendDefaultCity)与普通岗位的城市配置(cityCode)是完全独立的
     */
    private static void getRecommendJobs() {
        Page page = PlaywrightUtil.getPageObject();
        PlaywrightUtil.loadCookies(cookiePath);
        
        // 获取配置中的推荐城市
        String cityCode = null;
        
        // 如果配置了推荐城市，则查找对应的城市编码
        if (config.getRecommendDefaultCity() != null && !config.getRecommendDefaultCity().trim().isEmpty()) {
            // 1. 先从枚举中查找
            for (BossEnum.CityCode cc : BossEnum.CityCode.values()) {
                if (cc.getName().equals(config.getRecommendDefaultCity())) {
                    cityCode = cc.getCode();
                    log.info("从枚举中找到城市[{}]的编码: {}", config.getRecommendDefaultCity(), cityCode);
                    break;
                }
            }
            // 2. 如果枚举中没有，再尝试从自定义城市编码中查找
            if (cityCode == null && config.getCustomCityCode() != null && config.getCustomCityCode().containsKey(config.getRecommendDefaultCity())) {
                cityCode = config.getCustomCityCode().get(config.getRecommendDefaultCity());
                log.info("从自定义城市编码中找到城市[{}]的编码: {}", config.getRecommendDefaultCity(), cityCode);
            }
            
            if (cityCode == null) {
                log.warn("未找到配置城市[{}]的编码，将使用默认URL", config.getRecommendDefaultCity());
            }
        }
        
        // 先访问基础推荐页面，不带城市参数
        String baseUrl = "https://www.zhipin.com/web/geek/jobs";
        log.info("访问推荐岗位基础页面: {}", baseUrl);
        page.navigate(baseUrl);
        page.waitForLoadState();
        PlaywrightUtil.sleep(2); // 等待页面元素渲染
        
        // 获取配置的目标城市
        String configCity = config.getRecommendDefaultCity();

        // 等待页面加载
        page.waitForLoadState();

        try {
            // 等待Tab元素出现，最多等待10秒
            // 修改：同时等待a.synthesis和a.expect-item，兼容"推荐"Tab
            page.waitForSelector("a.synthesis, a.expect-item", new Page.WaitForSelectorOptions().setTimeout(10000));

            // 获取所有推荐岗位Tab，包括"推荐"Tab
            List<ElementHandle> tabs = page.querySelectorAll("a.synthesis, a.expect-item");
            if (tabs == null || tabs.isEmpty()) {
                log.error("未找到class为'synthesis'或'expect-item'的a标签元素");
                return;
            }

            // 读取配置文件中的Tab优先级
            List<String> tabPriority = config.getRecommendTabPriority();
            // 构建Tab文本到ElementHandle的映射
            Map<String, ElementHandle> tabMap = new java.util.LinkedHashMap<>();
            for (ElementHandle tab : tabs) {
                String tabText = tab.textContent().trim();
                tabMap.put(tabText, tab);
                log.info("找到推荐Tab: {}", tabText);
            }
            
            // 记录已处理的Tab
            Set<String> processed = new java.util.HashSet<>();
            
            // 步骤1: 首先处理页面第一个Tab（最前面的"推荐"）- 无论配置文件中如何设置
            if (!tabs.isEmpty()) {
                ElementHandle firstTab = tabs.get(0);
                String firstTabText = firstTab.textContent().trim();
                log.info("首先处理页面最前面的Tab: {}", firstTabText);
                firstTab.click();
                page.waitForLoadState();
                page.waitForTimeout(1000);
                // 循环重试城市切换，直到成功
                int retry = 0;
                int maxRetry = 10;
                boolean citySwitched = false;
                while (retry < maxRetry) {
                    citySwitched = switchCityInRecommendPage(page, configCity);
                    if (citySwitched) break;
                    retry++;
                    log.warn("第一个Tab[{}]城市切换失败，正在第{}次重试...", firstTabText, retry);
                    PlaywrightUtil.sleep(2);
                }
                if (!citySwitched) {
                    log.error("第一个Tab[{}]城市切换连续{}次失败，放弃本次投递", firstTabText, maxRetry);
                    processed.add(firstTabText);
                } else {
                    log.info("✅ 第一个Tab[{}]城市切换成功，等待岗位数据加载...", firstTabText);
                    // 切换城市后额外等待，确保岗位数据加载
                    page.waitForLoadState();
                    PlaywrightUtil.sleep(2);
                    if (isJobsPresent()) {
                        try {
                            // 边加载边投递推荐岗位
                            collectRecommendJobs(page, firstTabText);
                        } catch (Exception e) {
                            log.error("滚动加载数据异常: {}", e.getMessage());
                        }
                    }
                }
                processed.add(firstTabText);
            }
            
            // 步骤2: 然后按照配置文件中指定的Tab优先级处理（模糊匹配）
            if (tabPriority != null && !tabPriority.isEmpty()) {
                for (String tabName : tabPriority) {
                    ElementHandle tab = null;
                    tabs = page.querySelectorAll("a.synthesis, a.expect-item");
                    for (ElementHandle t : tabs) {
                        String tabText = t.textContent().trim();
                        if (tabText.contains(tabName) && !processed.contains(tabText)) {
                            tab = t;
                            tabName = tabText;
                            break;
                        }
                    }
                    if (tab == null) continue;
                    log.info("按配置优先投递Tab(模糊匹配): {}", tabName);
                    tab.click();
                    page.waitForLoadState();
                    page.waitForTimeout(1000);
                    // 循环重试城市切换，直到成功
                    int retry = 0;
                    int maxRetry = 10;
                    boolean citySwitched = false;
                    while (retry < maxRetry) {
                        citySwitched = switchCityInRecommendPage(page, configCity);
                        if (citySwitched) break;
                        retry++;
                        log.warn("Tab[{}]城市切换失败，正在第{}次重试...", tabName, retry);
                        PlaywrightUtil.sleep(2);
                    }
                    if (!citySwitched) {
                        log.error("Tab[{}]城市切换连续{}次失败，放弃本次投递", tabName, maxRetry);
                        processed.add(tabName);
                        continue;
                    }
                    log.info("✅ Tab[{}]城市切换成功，等待岗位数据加载...", tabName);
                    page.waitForLoadState();
                    PlaywrightUtil.sleep(2);
                    if (isJobsPresent()) {
                        try {
                            // 边加载边投递推荐岗位
                            collectRecommendJobs(page, tabName);
                        } catch (Exception e) {
                            log.error("滚动加载数据异常: {}", e.getMessage());
                        }
                    }
                    processed.add(tabName);
                }
            }
            
            // 步骤3: 处理未在配置中的Tab，按页面顺序（跳过已处理的）
            tabs = page.querySelectorAll("a.synthesis, a.expect-item");
            for (ElementHandle tab : tabs) {
                String tabText = tab.textContent().trim();
                if (processed.contains(tabText)) continue;
                log.info("投递剩余Tab: {}", tabText);
                tab.click();
                page.waitForLoadState();
                page.waitForTimeout(1000);
                // 循环重试城市切换，直到成功
                int retry = 0;
                int maxRetry = 10;
                boolean citySwitched = false;
                while (retry < maxRetry) {
                    citySwitched = switchCityInRecommendPage(page, configCity);
                    if (citySwitched) break;
                    retry++;
                    log.warn("Tab[{}]城市切换失败，正在第{}次重试...", tabText, retry);
                    PlaywrightUtil.sleep(2);
                }
                if (!citySwitched) {
                    log.error("Tab[{}]城市切换连续{}次失败，放弃本次投递", tabText, maxRetry);
                    processed.add(tabText);
                    continue;
                }
                log.info("✅ Tab[{}]城市切换成功，等待岗位数据加载...", tabText);
                page.waitForLoadState();
                PlaywrightUtil.sleep(2);
                if (isJobsPresent()) {
                    try {
                        // 边加载边投递推荐岗位
                        collectRecommendJobs(page, tabText);
                    } catch (Exception e) {
                        log.error("滚动加载数据异常: {}", e.getMessage());
                    }
                }
                processed.add(tabText);
            }
        } catch (Exception e) {
            log.error("寻找或点击'expect-item'元素时出错: {}", e.getMessage());
        }
    }

    // 工具方法：兼容单字符串和数组，支持多薪资区间
    // config.yaml 示例：salary: ["10-20K", "20-50K"] 或 salary: "20-50K"
    private static List<String> getSalaryList() {
        try {
            // 直接从配置文件读取原始薪资配置，而不是使用转换后的编码
            BossConfig rawConfig = utils.JobUtils.getConfig(BossConfig.class);
            Object salaryObj = rawConfig.getSalary();
        if (salaryObj instanceof List) {
            return (List<String>) salaryObj;
        } else if (salaryObj instanceof String) {
            return java.util.Collections.singletonList((String) salaryObj);
        } else {
                return java.util.Collections.singletonList("");
            }
        } catch (Exception e) {
            log.error("读取原始薪资配置失败: {}", e.getMessage());
            return java.util.Collections.singletonList("");
        }
    }

    // 新增：岗位核心名称和薪资区间提取
    private static String getCoreJobName(String jobName) {
        if (jobName == null) return "";
        // 去除括号及括号内内容、去除多余后缀
        return jobName.replaceAll("[（(][^）)]*[）)]", "").replaceAll("[\s·]+", "").replaceAll("[0-9A-Za-z]+$", "").trim();
    }
    private static String getCoreSalary(String salaryText) {
        Integer[] range = parseSalaryRange(salaryText == null ? "" : salaryText);
        if (range == null || range.length == 0) return "";
        if (range.length == 2) return range[0] + "-" + range[1] + "K";
        return range[0] + "K";
    }
    // 修改唯一标识为 公司名称|岗位核心名称|岗位核心薪资区间
    private static String getUniqueKey(Job job) {
        String company = job.getCompanyName() == null ? "" : job.getCompanyName();
        String jobCore = getCoreJobName(job.getJobName());
        String salaryCore = getCoreSalary(job.getSalary());
        return company + "|" + jobCore + "|" + salaryCore;
    }

    // 新增：获取城市拼音首字母的方法
    private static String getCityFirstLetter(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return null;
        }
        
        // 常见城市拼音首字母映射
        Map<String, String> cityLetterMap = new HashMap<>();
        
        // A
        cityLetterMap.put("安庆", "A");
        cityLetterMap.put("安阳", "A");
        cityLetterMap.put("鞍山", "A");
        cityLetterMap.put("安康", "A");
        cityLetterMap.put("阿克苏", "A");
        cityLetterMap.put("安顺", "A");
        
        // B
        cityLetterMap.put("北京", "B");
        cityLetterMap.put("保定", "B");
        cityLetterMap.put("包头", "B");
        cityLetterMap.put("蚌埠", "B");
        cityLetterMap.put("本溪", "B");
        cityLetterMap.put("宝鸡", "B");
        cityLetterMap.put("北海", "B");
        cityLetterMap.put("滨州", "B");
        cityLetterMap.put("亳州", "B");
        
        // C
        cityLetterMap.put("重庆", "C");
        cityLetterMap.put("成都", "C");
        cityLetterMap.put("长沙", "C");
        cityLetterMap.put("长春", "C");
        cityLetterMap.put("常州", "C");
        cityLetterMap.put("沧州", "C");
        cityLetterMap.put("承德", "C");
        cityLetterMap.put("长治", "C");
        cityLetterMap.put("常德", "C");
        cityLetterMap.put("郴州", "C");
        cityLetterMap.put("滁州", "C");
        cityLetterMap.put("巢湖", "C");
        cityLetterMap.put("潮州", "C");
        cityLetterMap.put("昌吉", "C");
        cityLetterMap.put("朝阳", "C");
        cityLetterMap.put("赤峰", "C");
        
        // D
        cityLetterMap.put("大连", "D");
        cityLetterMap.put("东莞", "D");
        cityLetterMap.put("大庆", "D");
        cityLetterMap.put("丹东", "D");
        cityLetterMap.put("大同", "D");
        cityLetterMap.put("德州", "D");
        cityLetterMap.put("东营", "D");
        cityLetterMap.put("大理", "D");
        cityLetterMap.put("德阳", "D");
        cityLetterMap.put("达州", "D");
        cityLetterMap.put("德宏", "D");
        
        // E
        cityLetterMap.put("鄂尔多斯", "E");
        cityLetterMap.put("恩施", "E");
        
        // F
        cityLetterMap.put("福州", "F");
        cityLetterMap.put("佛山", "F");
        cityLetterMap.put("阜阳", "F");
        cityLetterMap.put("抚顺", "F");
        cityLetterMap.put("抚州", "F");
        cityLetterMap.put("阜新", "F");
        cityLetterMap.put("防城港", "F");
        
        // G
        cityLetterMap.put("广州", "G");
        cityLetterMap.put("贵阳", "G");
        cityLetterMap.put("桂林", "G");
        cityLetterMap.put("赣州", "G");
        cityLetterMap.put("广安", "G");
        cityLetterMap.put("广元", "G");
        cityLetterMap.put("贵港", "G");
        cityLetterMap.put("固原", "G");
        
        // H
        cityLetterMap.put("杭州", "H");
        cityLetterMap.put("合肥", "H");
        cityLetterMap.put("哈尔滨", "H");
        cityLetterMap.put("海口", "H");
        cityLetterMap.put("呼和浩特", "H");
        cityLetterMap.put("惠州", "H");
        cityLetterMap.put("邯郸", "H");
        cityLetterMap.put("衡水", "H");
        cityLetterMap.put("葫芦岛", "H");
        cityLetterMap.put("淮安", "H");
        cityLetterMap.put("湖州", "H");
        cityLetterMap.put("黄山", "H");
        cityLetterMap.put("淮南", "H");
        cityLetterMap.put("淮北", "H");
        cityLetterMap.put("黄石", "H");
        cityLetterMap.put("黄冈", "H");
        cityLetterMap.put("荆州", "H");
        cityLetterMap.put("衡阳", "H");
        cityLetterMap.put("怀化", "H");
        cityLetterMap.put("惠州", "H");
        cityLetterMap.put("河源", "H");
        cityLetterMap.put("贺州", "H");
        cityLetterMap.put("河池", "H");
        cityLetterMap.put("海南", "H");
        cityLetterMap.put("红河", "H");
        cityLetterMap.put("汉中", "H");
        cityLetterMap.put("海东", "H");
        cityLetterMap.put("海北", "H");
        cityLetterMap.put("黄南", "H");
        cityLetterMap.put("海西", "H");
        cityLetterMap.put("哈密", "H");
        cityLetterMap.put("和田", "H");
        
        // J
        cityLetterMap.put("济南", "J");
        cityLetterMap.put("金华", "J");
        cityLetterMap.put("嘉兴", "J");
        cityLetterMap.put("江门", "J");
        cityLetterMap.put("吉林", "J");
        cityLetterMap.put("锦州", "J");
        cityLetterMap.put("九江", "J");
        cityLetterMap.put("景德镇", "J");
        cityLetterMap.put("吉安", "J");
        cityLetterMap.put("荆门", "J");
        cityLetterMap.put("荆州", "J");
        cityLetterMap.put("济宁", "J");
        cityLetterMap.put("焦作", "J");
        cityLetterMap.put("济源", "J");
        cityLetterMap.put("揭阳", "J");
        cityLetterMap.put("嘉峪关", "J");
        cityLetterMap.put("金昌", "J");
        cityLetterMap.put("酒泉", "J");
        
        // K
        cityLetterMap.put("昆明", "K");
        cityLetterMap.put("开封", "K");
        cityLetterMap.put("喀什", "K");
        cityLetterMap.put("克拉玛依", "K");
        cityLetterMap.put("库尔勒", "K");
        
        // L
        cityLetterMap.put("兰州", "L");
        cityLetterMap.put("洛阳", "L");
        cityLetterMap.put("临沂", "L");
        cityLetterMap.put("连云港", "L");
        cityLetterMap.put("廊坊", "L");
        cityLetterMap.put("辽阳", "L");
        cityLetterMap.put("辽源", "L");
        cityLetterMap.put("六安", "L");
        cityLetterMap.put("龙岩", "L");
        cityLetterMap.put("莱芜", "L");
        cityLetterMap.put("聊城", "L");
        cityLetterMap.put("洛阳", "L");
        cityLetterMap.put("漯河", "L");
        cityLetterMap.put("娄底", "L");
        cityLetterMap.put("柳州", "L");
        cityLetterMap.put("来宾", "L");
        cityLetterMap.put("丽江", "L");
        cityLetterMap.put("临沧", "L");
        cityLetterMap.put("拉萨", "L");
        cityLetterMap.put("林芝", "L");
        cityLetterMap.put("陇南", "L");
        cityLetterMap.put("临夏", "L");
        
        // M
        cityLetterMap.put("马鞍山", "M");
        cityLetterMap.put("牡丹江", "M");
        cityLetterMap.put("茂名", "M");
        cityLetterMap.put("梅州", "M");
        cityLetterMap.put("绵阳", "M");
        cityLetterMap.put("眉山", "M");
        
        // N
        cityLetterMap.put("南京", "N");
        cityLetterMap.put("宁波", "N");
        cityLetterMap.put("南昌", "N");
        cityLetterMap.put("南宁", "N");
        cityLetterMap.put("南通", "N");
        cityLetterMap.put("南阳", "N");
        cityLetterMap.put("南充", "N");
        cityLetterMap.put("内江", "N");
        cityLetterMap.put("南平", "N");
        cityLetterMap.put("宁德", "N");
        cityLetterMap.put("怒江", "N");
        cityLetterMap.put("那曲", "N");
        
        // P
        cityLetterMap.put("平顶山", "P");
        cityLetterMap.put("濮阳", "P");
        cityLetterMap.put("莆田", "P");
        cityLetterMap.put("萍乡", "P");
        cityLetterMap.put("攀枝花", "P");
        cityLetterMap.put("盘锦", "P");
        cityLetterMap.put("普洱", "P");
        cityLetterMap.put("平凉", "P");
        
        // Q
        cityLetterMap.put("青岛", "Q");
        cityLetterMap.put("泉州", "Q");
        cityLetterMap.put("秦皇岛", "Q");
        cityLetterMap.put("齐齐哈尔", "Q");
        cityLetterMap.put("衢州", "Q");
        cityLetterMap.put("清远", "Q");
        cityLetterMap.put("钦州", "Q");
        cityLetterMap.put("曲靖", "Q");
        cityLetterMap.put("庆阳", "Q");
        
        // R
        cityLetterMap.put("日照", "R");
        cityLetterMap.put("日喀则", "R");
        
        // S
        cityLetterMap.put("上海", "S");
        cityLetterMap.put("深圳", "S");
        cityLetterMap.put("苏州", "S");
        cityLetterMap.put("沈阳", "S");
        cityLetterMap.put("石家庄", "S");
        cityLetterMap.put("绍兴", "S");
        cityLetterMap.put("汕头", "S");
        cityLetterMap.put("汕尾", "S");
        cityLetterMap.put("韶关", "S");
        cityLetterMap.put("四平", "S");
        cityLetterMap.put("松原", "S");
        cityLetterMap.put("双鸭山", "S");
        cityLetterMap.put("绥化", "S");
        cityLetterMap.put("宿迁", "S");
        cityLetterMap.put("宿州", "S");
        cityLetterMap.put("三明", "S");
        cityLetterMap.put("上饶", "S");
        cityLetterMap.put("十堰", "S");
        cityLetterMap.put("随州", "S");
        cityLetterMap.put("邵阳", "S");
        cityLetterMap.put("遂宁", "S");
        cityLetterMap.put("山南", "S");
        cityLetterMap.put("商洛", "S");
        cityLetterMap.put("石嘴山", "S");
        cityLetterMap.put("石河子", "S");
        
        // T
        cityLetterMap.put("天津", "T");
        cityLetterMap.put("太原", "T");
        cityLetterMap.put("台州", "T");
        cityLetterMap.put("唐山", "T");
        cityLetterMap.put("泰安", "T");
        cityLetterMap.put("泰州", "T");
        cityLetterMap.put("铁岭", "T");
        cityLetterMap.put("通辽", "T");
        cityLetterMap.put("通化", "T");
        cityLetterMap.put("铜陵", "T");
        cityLetterMap.put("铜仁", "T");
        cityLetterMap.put("铜川", "T");
        cityLetterMap.put("天水", "T");
        cityLetterMap.put("吐鲁番", "T");
        cityLetterMap.put("塔城", "T");
        
        // W
        cityLetterMap.put("武汉", "W");
        cityLetterMap.put("无锡", "W");
        cityLetterMap.put("温州", "W");
        cityLetterMap.put("潍坊", "W");
        cityLetterMap.put("威海", "W");
        cityLetterMap.put("芜湖", "W");
        cityLetterMap.put("梧州", "W");
        cityLetterMap.put("乌鲁木齐", "W");
        cityLetterMap.put("乌海", "W");
        cityLetterMap.put("乌兰察布", "W");
        cityLetterMap.put("文山", "W");
        cityLetterMap.put("渭南", "W");
        cityLetterMap.put("武威", "W");
        cityLetterMap.put("吴忠", "W");
        
        // X
        cityLetterMap.put("西安", "X");
        cityLetterMap.put("厦门", "X");
        cityLetterMap.put("徐州", "X");
        cityLetterMap.put("邢台", "X");
        cityLetterMap.put("新乡", "X");
        cityLetterMap.put("许昌", "X");
        cityLetterMap.put("信阳", "X");
        cityLetterMap.put("新余", "X");
        cityLetterMap.put("湘潭", "X");
        cityLetterMap.put("湘西", "X");
        cityLetterMap.put("西宁", "X");
        cityLetterMap.put("咸阳", "X");
        cityLetterMap.put("西双版纳", "X");
        cityLetterMap.put("新疆", "X");
        cityLetterMap.put("兴安盟", "X");
        cityLetterMap.put("锡林郭勒盟", "X");
        cityLetterMap.put("香港", "X");
        
        // Y
        cityLetterMap.put("扬州", "Y");
        cityLetterMap.put("盐城", "Y");
        cityLetterMap.put("烟台", "Y");
        cityLetterMap.put("宜昌", "Y");
        cityLetterMap.put("岳阳", "Y");
        cityLetterMap.put("益阳", "Y");
        cityLetterMap.put("永州", "Y");
        cityLetterMap.put("阳江", "Y");
        cityLetterMap.put("云浮", "Y");
        cityLetterMap.put("玉林", "Y");
        cityLetterMap.put("宜宾", "Y");
        cityLetterMap.put("雅安", "Y");
        cityLetterMap.put("宜春", "Y");
        cityLetterMap.put("鹰潭", "Y");
        cityLetterMap.put("营口", "Y");
        cityLetterMap.put("延边", "Y");
        cityLetterMap.put("伊春", "Y");
        cityLetterMap.put("玉溪", "Y");
        cityLetterMap.put("延安", "Y");
        cityLetterMap.put("榆林", "Y");
        cityLetterMap.put("银川", "Y");
        cityLetterMap.put("伊犁", "Y");
        
        // Z
        cityLetterMap.put("郑州", "Z");
        cityLetterMap.put("珠海", "Z");
        cityLetterMap.put("中山", "Z");
        cityLetterMap.put("湛江", "Z");
        cityLetterMap.put("肇庆", "Z");
        cityLetterMap.put("张家口", "Z");
        cityLetterMap.put("镇江", "Z");
        cityLetterMap.put("淄博", "Z");
        cityLetterMap.put("枣庄", "Z");
        cityLetterMap.put("驻马店", "Z");
        cityLetterMap.put("周口", "Z");
        cityLetterMap.put("漳州", "Z");
        cityLetterMap.put("株洲", "Z");
        cityLetterMap.put("张家界", "Z");
        cityLetterMap.put("自贡", "Z");
        cityLetterMap.put("资阳", "Z");
        cityLetterMap.put("遵义", "Z");
        cityLetterMap.put("昭通", "Z");
        cityLetterMap.put("张掖", "Z");
        cityLetterMap.put("中卫", "Z");
        
        // 去掉"市"、"区"、"县"等后缀再查找
        String cleanCityName = cityName.replaceAll("[市区县]", "").trim();
        
        // 首先尝试完全匹配
        String letter = cityLetterMap.get(cleanCityName);
        if (letter != null) {
            return letter;
        }
        
        // 如果完全匹配失败，尝试包含匹配
        for (Map.Entry<String, String> entry : cityLetterMap.entrySet()) {
            if (cleanCityName.contains(entry.getKey()) || entry.getKey().contains(cleanCityName)) {
                return entry.getValue();
            }
        }
        
        // 如果都没有找到，返回null
        log.warn("未找到城市[{}]的拼音首字母映射", cityName);
        return null;
    }

    /**
     * 在推荐页面中切换城市的通用方法
     * @param page 页面对象
     * @param targetCity 目标城市名称
     * @return 是否切换成功
     */
    private static boolean switchCityInRecommendPage(Page page, String targetCity) {
        if (targetCity == null || targetCity.trim().isEmpty()) {
            log.info("未配置目标城市，跳过城市切换");
            return true;
        }
        try {
            log.info("开始模拟真实用户操作切换城市到[{}]", targetCity);
            page.waitForLoadState();
            PlaywrightUtil.sleep(2);
            // 查找页面头部城市按钮
            String[] cityButtonSelectors = {
                ".city-label", ".city-picker", ".city-select", ".city-selector",
                ".city-menu", "span.city", "div.city", ".location-text",
                ".header-city", ".nav-city", "[data-city]", ".city-name",
                ".ka-select-city", ".geek-city-selector", ".location-selector",
                ".city-switch", ".city-btn", ".location-btn", ".area-selector",
                ".header-location", ".nav-location", ".city-dropdown"
            };
            Locator cityBtn = null;
            String currentCity = "";
            for (String selector : cityButtonSelectors) {
                try {
                    Locator btn = page.locator(selector);
                    if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        cityBtn = btn;
                        currentCity = btn.textContent().trim();
                        log.info("找到城市按钮[{}]，当前城市：{}", selector, currentCity);
                        break;
                    }
                } catch (Exception ex) {}
            }
            if (cityBtn == null) {
                log.error("未找到页面头部城市按钮");
                return false;
            } else if (currentCity.contains(targetCity) || targetCity.contains(currentCity) ||
                       (currentCity.length() <= 2 && targetCity.contains(currentCity))) {
                log.info("页面当前城市已是[{}]，无需切换", targetCity);
                return true;
            } else {
                log.info("当前城市[{}]与目标城市[{}]不符，开始切换", currentCity, targetCity);
                cityBtn.click();
                PlaywrightUtil.sleep(1);
                // 等待城市选择弹窗出现
                String[] dialogSelectors = {
                    ".dialog-container", ".city-dialog", ".city-popup", ".city-modal",
                    ".popup-container", ".modal-container", ".city-selector-dialog",
                    ".ka-select-city", ".city-select-dialog", ".location-dialog",
                    ".city-picker-dialog", ".geek-city-selector", ".city-list-container"
                };
                boolean dialogFound = false;
                for (String dialogSelector : dialogSelectors) {
                    try {
                        page.waitForSelector(dialogSelector, new Page.WaitForSelectorOptions().setTimeout(3000));
                        dialogFound = true;
                        log.info("城市选择弹窗已出现[{}]", dialogSelector);
                        break;
                    } catch (Exception e) {}
                }
                if (!dialogFound) {
                    log.error("城市选择弹窗未出现");
                    return false;
                }
                
                // 修改：首先尝试在热门城市列表中查找目标城市
                boolean citySelected = false;
                try {
                    // 尝试直接点击热门城市
                    String[] hotCitySelectors = {
                        "ul.city-list-hot li:text-is(\"" + targetCity + "\")",
                        ".city-list-hot li:text-is(\"" + targetCity + "\")",
                        "ul.hot-city li:text-is(\"" + targetCity + "\")",
                        ".hot-city li:text-is(\"" + targetCity + "\")",
                        ".hot-city-list li:text-is(\"" + targetCity + "\")"
                    };
                    
                    for (String selector : hotCitySelectors) {
                        try {
                            Locator hotCityOption = page.locator(selector);
                            if (hotCityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                log.info("在热门城市列表中找到城市[{}]", targetCity);
                                hotCityOption.click();
                                PlaywrightUtil.sleep(2);
                                citySelected = true;
                                break;
                            }
                        } catch (Exception ex) {}
                    }
                } catch (Exception e) {
                    log.info("在热门城市列表中查找城市[{}]失败: {}", targetCity, e.getMessage());
                }
                
                // 如果热门城市中没有找到，尝试按字母分组查找
                if (!citySelected) {
                    String firstLetter = getCityFirstLetter(targetCity);
                    if (firstLetter != null) {
                        log.info("城市[{}]的拼音首字母为：{}", targetCity, firstLetter);
                        
                        // 字母组选择器
                        String[][] letterGroups = {
                            {"A", "B", "C", "D", "E"}, 
                            {"F", "G", "H", "J"}, 
                            {"K", "L", "M", "N"}, 
                            {"P", "Q", "R", "S", "T"}, 
                            {"W", "X", "Y", "Z"}
                        };
                        
                        String targetGroup = null;
                        for (String[] group : letterGroups) {
                            for (String letter : group) {
                                if (letter.equals(firstLetter)) {
                                    // 找到字母所在组
                                    targetGroup = String.join("", group);
                                    break;
                                }
                            }
                            if (targetGroup != null) break;
                        }
                        
                        if (targetGroup != null) {
                            log.info("城市[{}]首字母[{}]属于字母组[{}]", targetCity, firstLetter, targetGroup);
                            
                            // 尝试点击对应的字母组Tab
                            String[] groupTabSelectors = {
                                "li:text-is(\"" + targetGroup + "\")", 
                                "li:has-text(\"" + targetGroup + "\")",
                                ".letter-tab:has-text(\"" + targetGroup + "\")",
                                ".city-letter:has-text(\"" + targetGroup + "\")"
                            };
                            
                            boolean groupTabClicked = false;
                            for (String selector : groupTabSelectors) {
                                try {
                                    Locator groupTab = page.locator(selector);
                                    if (groupTab.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                        groupTab.click();
                                        log.info("已点击字母组Tab[{}]", targetGroup);
                                        PlaywrightUtil.sleep(1);
                                        groupTabClicked = true;
                                        break;
                                    }
                                } catch (Exception ex) {
                                    log.debug("点击字母组Tab[{}]失败: {}", targetGroup, ex.getMessage());
                                }
                            }
                            
                            // 如果成功点击了字母组，查找城市
                            if (groupTabClicked) {
                                String[] citySelectors = {
                                    "li:text-is(\"" + targetCity + "\")",
                                    "span:text-is(\"" + targetCity + "\")",
                                    "div:text-is(\"" + targetCity + "\")",
                                    "a:text-is(\"" + targetCity + "\")",
                                    ".city-list li:text-is(\"" + targetCity + "\")",
                                    ".city-item:text-is(\"" + targetCity + "\")",
                                    "text=" + targetCity
                                };
                                
                                for (String selector : citySelectors) {
                                    try {
                                        Locator cityOption = page.locator(selector);
                                        if (cityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                                            cityOption.click();
                                            log.info("在字母组[{}]下找到并点击城市[{}]", targetGroup, targetCity);
                                            citySelected = true;
                                            break;
                                        }
                                    } catch (Exception ex) {
                                        log.debug("点击城市[{}]失败: {}", targetCity, ex.getMessage());
                                    }
                                }
                            }
                        } else {
                            log.warn("无法确定城市[{}]首字母[{}]所在的字母组", targetCity, firstLetter);
                        }
                    }
                }
                
                // 如果以上方法都失败，尝试直接搜索城市名称
                if (!citySelected) {
                    try {
                        log.info("尝试在所有城市列表中直接搜索[{}]", targetCity);
                        String[] allCitySelectors = {
                            "li:text-is(\"" + targetCity + "\")",
                            "span:text-is(\"" + targetCity + "\")",
                            "div.city-item:text-is(\"" + targetCity + "\")",
                            "div.city-list li:text-is(\"" + targetCity + "\")",
                            "text=" + targetCity
                        };
                        
                        for (String selector : allCitySelectors) {
                            try {
                                Locator cityOption = page.locator(selector);
                                if (cityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                                    cityOption.click();
                                    log.info("直接搜索找到并点击城市[{}]", targetCity);
                                    citySelected = true;
                                    break;
                                }
                            } catch (Exception ex) {}
                        }
                    } catch (Exception e) {
                        log.info("直接搜索城市[{}]失败: {}", targetCity, e.getMessage());
                    }
                }
                
                if (!citySelected) {
                    // 如果还是失败了，尝试在热门城市中选择一个接近的城市
                    log.info("所有方法查找城市[{}]均失败，尝试从热门城市中选择", targetCity);
                    try {
                        // 先确保热门城市Tab被选中
                        page.locator("li:text-is(\"热门城市\")").first().click();
                        PlaywrightUtil.sleep(1);
                        
                        // 获取所有热门城市
                        List<ElementHandle> hotCities = page.querySelectorAll("ul.city-list-hot li, .hot-city-list li, .hot-city li");
                        if (!hotCities.isEmpty()) {
                            // 尝试选择厦门或其他备选城市
                            String[] preferredCities = {"厦门", "福州", "泉州", "漳州", "广州", "深圳", "北京", "上海"};
                            for (String preferredCity : preferredCities) {
                                for (ElementHandle city : hotCities) {
                                    if (city.textContent().trim().equals(preferredCity)) {
                                        city.click();
                                        log.info("未找到目标城市[{}]，已选择备选热门城市[{}]", targetCity, preferredCity);
                                        citySelected = true;
                                        break;
                                    }
                                }
                                if (citySelected) break;
                            }
                            
                            // 如果没有找到备选城市，随机选择一个热门城市
                            if (!citySelected && !hotCities.isEmpty()) {
                                // 选择第二个热门城市（第一个通常是"全国"）
                                int index = hotCities.size() > 1 ? 1 : 0;
                                String fallbackCity = hotCities.get(index).textContent().trim();
                                hotCities.get(index).click();
                                log.info("未找到目标城市[{}]或备选城市，已选择热门城市[{}]", targetCity, fallbackCity);
                                citySelected = true;
                            }
                        }
                    } catch (Exception e) {
                        log.error("选择备选热门城市失败: {}", e.getMessage());
                    }
                }
                
                // 如果仍然无法选择城市，尝试按ESC关闭弹窗
                if (!citySelected) {
                    log.error("拼音首字母Tab查找城市[{}]失败，跳过此次城市切换", targetCity);
                    try { page.keyboard().press("Escape"); PlaywrightUtil.sleep(1); } catch (Exception e) {}
                    return false;
                }
                
                // 验证城市切换
                page.waitForLoadState();
                PlaywrightUtil.sleep(3);
                boolean cityVerified = false;
                int verifyAttempts = 0;
                int maxVerifyAttempts = 10;
                while (!cityVerified && verifyAttempts < maxVerifyAttempts) {
                    verifyAttempts++;
                    PlaywrightUtil.sleep(verifyAttempts <= 3 ? 2 : 3);
                    page.waitForLoadState();
                    String newCurrentCity = "";
                    boolean foundCityButton = false;
                    for (String selector : cityButtonSelectors) {
                        try {
                            Locator btn = page.locator(selector);
                            if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                newCurrentCity = btn.textContent().trim();
                                foundCityButton = true;
                                break;
                            }
                        } catch (Exception ex) {}
                    }
                    if (!foundCityButton) continue;
                    boolean isCityMatched = false;
                    if (newCurrentCity.equals(targetCity)) {
                        isCityMatched = true;
                    } else if (newCurrentCity.contains(targetCity) || targetCity.contains(newCurrentCity)) {
                        if (Math.abs(newCurrentCity.length() - targetCity.length()) <= 2) {
                            isCityMatched = true;
                        }
                    } else if (targetCity.length() >= 2 && newCurrentCity.length() >= 2) {
                        String targetPrefix = targetCity.substring(0, Math.min(2, targetCity.length()));
                        String currentPrefix = newCurrentCity.substring(0, Math.min(2, newCurrentCity.length()));
                        if (targetPrefix.equals(currentPrefix)) {
                            isCityMatched = true;
                        }
                    }
                    if (isCityMatched) {
                        cityVerified = true;
                        PlaywrightUtil.sleep(3);
                        break;
                    }
                }
                if (cityVerified) {
                    log.info("🎉 城市切换流程完成！已成功切换到目标城市[{}]，开始处理岗位投递", targetCity);
                    return true;
                } else {
                    log.error("❌ 城市切换验证失败！经过{}次验证尝试，仍未能确认城市切换到[{}]", maxVerifyAttempts, targetCity);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("模拟真实用户操作切换城市失败: {}，页面HTML片段：{}", e.getMessage(), page.content().substring(0, Math.min(1000, page.content().length())));
            return false;
        }
    }

    // 主流程：遍历每个薪资区间
    private static void postJobByCityByPlaywright(String cityCode) {
        String cityName = null;
        for (boss.BossEnum.CityCode cc : boss.BossEnum.CityCode.values()) {
            if (cc.getCode().equals(cityCode)) {
                cityName = cc.getName();
                break;
            }
        }
        if (cityName == null) cityName = cityCode;
        List<String> experienceList = config.getExperience();
        List<String> salaryList = getSalaryList();
        List<String> scaleList = config.getScale();
        for (String experience : experienceList) {
            for (String salary : salaryList) {
                for (String scale : scaleList) {
        for (String keyword : config.getKeywords()) {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
                        String url = getSearchUrl(cityCode, salary, experience, scale) + "&query=" + encodedKeyword;
                        log.info("查询岗位链接:{} (经验:{} 薪资:{} 规模:{})", url, experience, salary, scale);
            Page page = PlaywrightUtil.getPageObject().context().newPage();
            PlaywrightUtil.loadCookies(cookiePath);
            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                // 增加页面加载等待时间，避免风控
                page.waitForLoadState();
                PlaywrightUtil.sleep(2 + (int)(Math.random() * 3)); // 随机等待2-5秒
                            // 滚动加载所有岗位
                int previousJobCount = 0;
                int currentJobCount = 0;
                int unchangedCount = 0;
                if (isJobsPresent()) {
                    try {
                        log.info("开始获取岗位信息...");
                        while (unchangedCount < 2) {
                            List<ElementHandle> jobCards = page.querySelectorAll(JOB_LIST_SELECTOR);
                            currentJobCount = jobCards.size();
                            log.info("当前已加载岗位数量:{} ", currentJobCount);
                            if (currentJobCount > previousJobCount) {
                                previousJobCount = currentJobCount;
                                unchangedCount = 0;
                                PlaywrightUtil.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                                log.info("下拉页面加载更多...");
                                page.waitForTimeout(3000 + (int)(Math.random() * 2000)); // 随机等待3-5秒，避免风控
                            } else {
                                unchangedCount++;
                                if (unchangedCount < 2) {
                                    System.out.println("下拉后岗位数量未增加，再次尝试...");
                                    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                                    page.waitForTimeout(3000 + (int)(Math.random() * 2000)); // 随机等待3-5秒，避免风控
                                } else {
                                    break;
                                }
                            }
                        }
                        log.info("已获取所有可加载岗位，共计: " + currentJobCount + " 个");
                    } catch (Exception e) {
                        log.error("滚动加载数据异常: {}", e.getMessage());
                                }
                            }
                            // 滚动加载完成后，重新获取所有岗位卡片，全部收集后统一处理
                            List<Job> jobs = new ArrayList<>();
                            try {
                                Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, BossElementLocators.JOB_CARD_BOX);
                                int count = jobLocators.count();
                                for (int i = 0; i < count; i++) {
                                    try {
                                        Locator jobCard = jobLocators.nth(i);
                                        String jobName = jobCard.locator(BossElementLocators.JOB_NAME).textContent();
                                        String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                                        String jobArea = jobCard.locator(BossElementLocators.JOB_AREA).textContent();
                                        Job job = new Job();
                                        job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                                        job.setCompanyName(companyName);
                                        job.setJobName(jobName);
                                        job.setJobArea(jobArea);
                                        // 新增：岗位城市过滤（更鲁棒）
                                        if (!isJobInCity(job, cityName)) {
                                            log.info("已过滤：岗位【{}】不在当前城市【{}】", jobName, cityName);
                                            continue;
                                        }
                                        // 获取标签列表
                                        Locator tagElements = jobCard.locator(BossElementLocators.TAG_LIST);
                                        int tagCount = tagElements.count();
                                        StringBuilder tag = new StringBuilder();
                                        for (int j = 0; j < tagCount; j++) {
                                            tag.append(tagElements.nth(j).textContent()).append("·");
                                        }
                                        if (tag.length() > 0) {
                                            job.setCompanyTag(tag.substring(0, tag.length() - 1));
                                        } else {
                                            job.setCompanyTag("");
                                        }
                                        // 设置当前薪资区间
                                        job.setSalary(salary);
                                        jobs.add(job);
                                    } catch (Exception e) {
                                        log.debug("处理岗位卡片失败: {}", e.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                log.error("收集岗位卡片失败: {}", e.getMessage());
                            }
                            // 统一处理所有岗位
                            int result = processJobListDetails(jobs, keyword, page, cityName);
                            if (result == -1) {
                                log.info("检测到投递已达每日上限，准备关闭所有窗口并退出程序...");
                                // 构造推送内容
                                StringBuilder sb = new StringBuilder();
                                sb.append("【Boss直聘】今日投递汇总\n");
                                sb.append(String.format("共发起 %d 个聊天，用时 %s\n", resultList.size(), formatDuration(startDate, new Date())));
                                sb.append("-------------------------\n");
                                int idx = 1;
                                for (Job job : resultList) {
                                    sb.append(String.format("%d. %s @ %s | %s | %s\n", idx++, job.getJobName(), job.getCompanyName(), job.getSalary() == null ? "" : job.getSalary(), job.getJobArea() == null ? "" : job.getJobArea()));
                                }
                                sb.append("-------------------------\n");
                                sb.append("祝你早日找到心仪的工作！");
                                String barkMsg = sb.toString();
                                utils.Bot.sendBark(barkMsg);
                                log.info(barkMsg);
                                
                                // 关闭浏览器窗口的更可靠方法
                                try {
                                    if (page != null) {
                                        page.close();
                                        log.info("成功关闭当前页面");
                                    }
                                } catch (Exception e) {
                                    log.error("关闭当前页面失败: {}", e.getMessage());
                                }
                                
                                try {
                                    PlaywrightUtil.close();
                                    log.info("成功关闭所有Playwright窗口和会话");
                                } catch (Exception e) {
                                    log.error("关闭Playwright会话失败: {}", e.getMessage());
                                }
                                
                                System.exit(0);
                            }
                            

            } catch (Exception e) {
                log.error("页面跳转超时: {}，url: {}", e.getMessage(), url);
                continue;
            } finally {
                if (page != null) {
                    page.close();
                }
            }
            try {
                            // 增加随机间隔时间，避免风控
                            Thread.sleep(4000 + (int)(Math.random() * 3000)); // 随机等待4-7秒
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("线程休眠被中断: {}", ie.getMessage());
            }
        }
                }
            }
        }
    }

    // 新增多参数getSearchUrl
    private static String getSearchUrl(String cityCode, String salary, String experience, String scale) {
        return baseUrl + JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobType()) +
                JobUtils.appendParam("salary", salary) +
                JobUtils.appendListParam("experience", Collections.singletonList(experience)) +
                JobUtils.appendListParam("degree", config.getDegree()) +
                JobUtils.appendListParam("scale", Collections.singletonList(scale)) +
                JobUtils.appendListParam("industry", config.getIndustry()) +
                JobUtils.appendListParam("stage", config.getStage());
    }

    private static boolean isJobsPresent() {
        try {
            // 先尝试原有的选择器
            PlaywrightUtil.waitForElement(JOB_LIST_CONTAINER);
            return true;
        } catch (Exception e1) {
            try {
                // 尝试兼容的选择器
                PlaywrightUtil.waitForElement(JOB_LIST_CONTAINER_ALT);
                return true;
            } catch (Exception e2) {
                log.error("加载岗位区块失败，尝试了多个选择器都无效: {}", e1.getMessage());
                return false;
            }
        }
    }

    // 优化 saveData 方法，纵向排列所有数组
    private static void saveData(String path) {
        try {
            updateListData();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            data.put(appliedJobsKey, new java.util.ArrayList<>(appliedJobs));
            // 使用美化输出，纵向排列
            mapper.writerWithDefaultPrettyPrinter().writeValue(new java.io.File(path), data);
        } catch (IOException e) {
            log.error("保存【{}】数据失败！", path);
        }
    }

    private static void updateListData() {
        com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
        page.navigate("https://www.zhipin.com/web/geek/chat");
        PlaywrightUtil.sleep(3);

        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                Locator bottomElement = page.locator(FINISHED_TEXT);
                if (bottomElement.isVisible() && "没有更多了".equals(bottomElement.textContent())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }

            Locator items = page.locator(CHAT_LIST_ITEM);
            int itemCount = items.count();

            for (int i = 0; i < itemCount; i++) {
                try {
                    Locator companyElements = page.locator(COMPANY_NAME_IN_CHAT);
                    Locator messageElements = page.locator(LAST_MESSAGE);

                    String companyName = null;
                    String message = null;
                    int retryCount = 0;

                    while (retryCount < 2) {
                        try {
                            if (i < companyElements.count() && i < messageElements.count()) {
                                companyName = companyElements.nth(i).textContent();
                                message = messageElements.nth(i).textContent();
                                break; // 成功获取文本，跳出循环
                            } else {
                                log.info("元素索引超出范围");
                                break;
                            }
                        } catch (Exception e) {
                            retryCount++;
                            if (retryCount >= 2) {
                                log.info("尝试获取元素文本2次失败，放弃本次获取");
                                break;
                            }
                            log.info("页面元素已变更，正在重试第{}次获取元素文本...", retryCount);
                            // 等待短暂时间后重试
                            PlaywrightUtil.sleep(1);
                        }
                    }

                    // 只有在成功获取文本的情况下才继续处理
                    if (companyName != null && message != null) {
                        boolean match = message.contains("不") || message.contains("感谢") || message.contains("但")
                                || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                        boolean nomatch = message.contains("不是") || message.contains("不生");
                        if (match && !nomatch) {
                            log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                continue;
                            }
                            companyName = companyName.replaceAll("\\.{3}", "");
                            if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                                blackCompanies.add(companyName);
                            }
                            // 新增：自动将公司+招聘者组合加入黑名单
                            String recruiterName = null;
                            try {
                                recruiterName = messageElements.nth(i).textContent(); // 这里假设能获取到招聘者名
                            } catch (Exception e) {}
                            if (recruiterName != null && !recruiterName.isEmpty()) {
                                addToBlackCompanyRecruiterBlacklist(companyName, recruiterName, 7); // 默认7天，可根据配置调整
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("寻找黑名单公司异常...", e);
                }
            }

            try {
                // 尝试找到加载更多的元素
                Locator loadMoreElement = page.locator(SCROLL_LOAD_MORE);
                if (loadMoreElement.isVisible()) {
                    // 滚动到加载更多元素
                    loadMoreElement.scrollIntoViewIfNeeded();
                    PlaywrightUtil.sleep(1);
                } else {
                    // 如果找不到特定元素，尝试滚动到页面底部
                    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) {
                log.info("没找到滚动条...");
                break;
            }
        }
        log.info("黑名单公司数量：{}", blackCompanies.size());
    }

    private static String customJsonFormat(Map<String, Set<String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": [\n");
            sb.append(entry.getValue().stream().map(s -> "        \"" + s + "\"").collect(Collectors.joining(",\n")));

            sb.append("\n    ],\n");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("\n}");
        return sb.toString();
    }

    private static void loadData(String path) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            parseJson(json);
        } catch (IOException e) {
            log.error("读取【{}】数据失败！", path);
        }
    }

    private static void parseJson(String json) {
        JSONObject jsonObject = new JSONObject(json);
        blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        // 新增：appliedJobs
        if (jsonObject.has("appliedJobs")) {
            appliedJobs.clear();
            appliedJobs.addAll(jsonObject.getJSONArray("appliedJobs").toList().stream().map(Object::toString).collect(Collectors.toSet()));
        }
    }


    @SneakyThrows
    private static Integer h5ResumeSubmission(String keyword, Page page, String cityName) {
        // 查找所有job卡片元素
        // 获取元素总数
        List<ElementHandle> jobCards = page.querySelectorAll("ul li.item");
        List<Job> jobs = new ArrayList<>();
        for (ElementHandle jobCard : jobCards) {
            // 获取完整HTML
            String outerHtml = jobCard.evaluate("el => el.outerHTML").toString();
            // 获取招聘者信息
            ElementHandle recruiterElement = jobCard.querySelector("div.recruiter div.name");
            String recruiterText = recruiterElement.textContent();

            String salary = jobCard.querySelector("div.title span.salary").textContent();
            String jobHref = jobCard.querySelector("a").getAttribute("href");

            // === 新增：本地二次过滤开关 ===
            if (h5Config.getStrictLocalFilter() != null && h5Config.getStrictLocalFilter()) {
                if (isInBlackList(blackRecruiterItems, recruiterText, "招聘者", jobHref)) {
                    continue;
                }
                String jobName = jobCard.querySelector("div.title span.title-text").textContent();
                if (isInBlackList(blackJobItems, jobName, "岗位", jobName) || !isTargetJob(keyword, jobName)) {
                    continue;
                }
                String companyName = jobCard.querySelector("div.name span.company").textContent();
                if (isInBlackList(blackCompanyItems, companyName, "公司", jobName)) {
                    continue;
                }
                if (isSalaryNotExpected(salary)) {
                    // 过滤薪资
                    log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", companyName, jobName, salary);
                    continue;
                }
                if (config.getKeyFilter()) {
                    // 修改为：只要岗位名称包含任意一个关键词就通过
                    boolean matchAnyKeyword = false;
                    for (String k : config.getKeywords()) {
                        if (jobName.toLowerCase().contains(k.toLowerCase())) {
                            matchAnyKeyword = true;
                            break;
                        }
                    }
                    if (!matchAnyKeyword) {
                        log.info("已过滤：岗位【{}】名称不包含任意关键字{}", jobName, config.getKeywords());
                        continue;
                    }
                }
            }

            Job job = new Job();
            // 获取职位链接
            job.setHref(jobHref);
            // 获取职位名称
            job.setJobName(jobCard.querySelector("div.title span.title-text").textContent());
            // 获取工作地点
            job.setJobArea(jobCard.querySelector("div.name span.workplace").textContent());
            // 获取薪资
            job.setSalary(salary);
            // 获取标签
            List<ElementHandle> tagElements = jobCard.querySelectorAll("div.labels span");
            StringBuilder tag = new StringBuilder();
            for (ElementHandle tagElement : tagElements) {
                tag.append(tagElement.textContent()).append("·");
            }
            if (tag.length() > 0) {
                job.setCompanyTag(tag.substring(0, tag.length() - 1));
            } else {
                job.setCompanyTag("");
            }
            // 获取公司名称
            job.setCompanyName(jobCard.querySelector("div.name span.company").textContent());
            // 设置招聘者信息
            job.setRecruiter(recruiterText);
            // 新增：查找并点击H5"立即沟通"按钮
            ElementHandle chatBtn = jobCard.querySelector(".btn-chat");
            if (chatBtn != null && chatBtn.isVisible()) {
                chatBtn.click();
                PlaywrightUtil.sleep(2); // 等待页面响应
                // 可在此处补充自动填写打招呼语、发送等后续逻辑
            }
            jobs.add(job);
        }

        // 处理每个职位详情
        int result = processJobListDetails(jobs, keyword, page, cityName);
        if (result < 0) {
            if (result == -1) {
                log.info("在H5版本投递中检测到投递已达每日上限，准备关闭所有窗口并退出程序...");
                // 构造推送内容
                StringBuilder sb = new StringBuilder();
                sb.append("【Boss直聘移动版】今日投递汇总\n");
                sb.append(String.format("共发起 %d 个聊天，用时 %s\n", resultList.size(), formatDuration(startDate, new Date())));
                sb.append("-------------------------\n");
                int idx = 1;
                for (Job j : resultList) {
                    sb.append(String.format("%d. %s @ %s | %s | %s\n", idx++, j.getJobName(), j.getCompanyName(), j.getSalary() == null ? "" : j.getSalary(), j.getJobArea() == null ? "" : j.getJobArea()));
                }
                sb.append("-------------------------\n");
                sb.append("祝你早日找到心仪的工作！");
                String barkMsg = sb.toString();
                utils.Bot.sendBark(barkMsg);
                log.info(barkMsg);
                
                // 关闭浏览器窗口的更可靠方法
                try {
                    PlaywrightUtil.close();
                    log.info("成功关闭所有Playwright窗口和会话");
                } catch (Exception e) {
                    log.error("关闭Playwright会话失败: {}", e.getMessage());
                }
                
                System.exit(0);
            }
            return result;
        }

        return resultList.size();
    }


    @SneakyThrows
    private static Integer resumeSubmission(String keyword, Page page, String cityName) {
        // 查找所有job卡片元素
        // 使用page.locator方法获取所有匹配的元素
        Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, BossElementLocators.JOB_CARD_BOX);
        // 获取元素总数
        int count = jobLocators.count();

        List<Job> jobs = new ArrayList<>();
        // 遍历所有找到的job卡片
        for (int i = 0; i < count; i++) {
            try {
                Locator jobCard = jobLocators.nth(i);
                String jobName = jobCard.locator(BossElementLocators.JOB_NAME).textContent();
                String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                String jobArea = jobCard.locator(BossElementLocators.JOB_AREA).textContent();
                Job job = new Job();
                job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                job.setCompanyName(companyName);
                job.setJobName(jobName);
                job.setJobArea(jobArea);
                // 新增：岗位城市过滤（更鲁棒）
                if (!isJobInCity(job, cityName)) {
                    log.info("已过滤：岗位【{}】不在当前城市【{}】", jobName, cityName);
                    continue;
                }
                // 获取标签列表
                Locator tagElements = jobCard.locator(BossElementLocators.TAG_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                if (tag.length() > 0) {
                    job.setCompanyTag(tag.substring(0, tag.length() - 1));
                } else {
                    job.setCompanyTag("");
                }


                if (isInBlackList(blackJobItems, jobName, "岗位", jobName) || !isTargetJob(keyword, jobName)) {
                    continue;
                }


                if (isInBlackList(blackCompanyItems, companyName, "公司", jobName)) {
                    continue;
                }

                if (config.getKeyFilter()) {
                    // 修改为：只要岗位名称包含任意一个关键词就通过
                    boolean matchAnyKeyword = false;
                    for (String k : config.getKeywords()) {
                        if (jobName.toLowerCase().contains(k.toLowerCase())) {
                            matchAnyKeyword = true;
                            break;
                        }
                    }
                    if (!matchAnyKeyword) {
                        log.info("已过滤：岗位【{}】名称不包含任意关键字{}", jobName, config.getKeywords());
                        continue;
                    }
                }

                jobs.add(job);
            } catch (Exception e) {
                log.debug("处理岗位卡片失败: {}", e.getMessage());
            }
        }

        // 处理每个职位详情
        int result = processJobListDetails(jobs, keyword, page, cityName);
        if (result < 0) {
            if (result == -1) {
                log.info("在PC版本投递中检测到投递已达每日上限，准备关闭所有窗口并退出程序...");
                // 构造推送内容
                StringBuilder sb = new StringBuilder();
                sb.append("【Boss直聘PC版】今日投递汇总\n");
                sb.append(String.format("共发起 %d 个聊天，用时 %s\n", resultList.size(), formatDuration(startDate, new Date())));
                sb.append("-------------------------\n");
                int idx = 1;
                for (Job j : resultList) {
                    sb.append(String.format("%d. %s @ %s | %s | %s\n", idx++, j.getJobName(), j.getCompanyName(), j.getSalary() == null ? "" : j.getSalary(), j.getJobArea() == null ? "" : j.getJobArea()));
                }
                sb.append("-------------------------\n");
                sb.append("祝你早日找到心仪的工作！");
                String barkMsg = sb.toString();
                utils.Bot.sendBark(barkMsg);
                log.info(barkMsg);
                
                // 关闭浏览器窗口的更可靠方法
                try {
                    PlaywrightUtil.close();
                    log.info("成功关闭所有Playwright窗口和会话");
                } catch (Exception e) {
                    log.error("关闭Playwright会话失败: {}", e.getMessage());
                }
                
                System.exit(0);
            }
            return result;
        }

        return resultList.size();
    }

    /**
     * 处理职位列表
     *
     * @param jobs    职位列表
     * @param keyword 搜索关键词
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processJobListDetails(List<Job> jobs, String keyword, Page page, String cityName) {
        List<String> keywords = config.getKeywords();
        for (Job job : jobs) {
            Page jobPage = null;
            try {
                String uniqueKey = getUniqueKey(job);
                if (!allowRepeatApply && isAppliedJobValid(uniqueKey)) {
                    log.info("已投递过该岗位，跳过：{}", uniqueKey);
                    continue;
                }
                if (!isJobInCity(job, cityName)) {
                    log.info("已过滤：岗位【{}】不在当前城市【{}】", job.getJobName(), cityName);
                    continue;
                }
                jobPage = page.context().newPage();
                try {
                    jobPage.navigate(homeUrl + job.getHref());
                    // 等待页面加载
                    jobPage.waitForLoadState();
                    PlaywrightUtil.sleep(2); // 增加等待时间，避免风控
                    
                    // 简单模拟投递逻辑
                    boolean deliveryResult = attemptJobDelivery(job, jobPage);
                    if (deliveryResult) {
                        resultList.add(job);
                        log.info("投递成功: {} @ {}", job.getJobName(), job.getCompanyName());
                        // 追加写入md并生成HTML
                        java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                        if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                            // 简单按换行或分号分割
                            for (String duty : job.getJobInfo().split("[\n;；]") ) {
                                String d = duty.trim();
                                if (!d.isEmpty()) jobDutyList.add(d);
                            }
                        }
                        String recruiter = job.getRecruiter() == null ? "" : job.getRecruiter();
                        String aiGreeting = "自动投递"; // 如有AI生成可替换
                        utils.JobDetailsMdWriter.appendRecord(
                            job.getCompanyName(),
                            job.getJobName(),
                            job.getSalary(),
                            job.getJobArea(),
                            recruiter,
                            aiGreeting,
                            jobDutyList
                        );
                        utils.DeliveryHtmlReporter.generateHtmlFromMd();
                    } else {
                        log.info("投递失败或跳过: {} @ {}", job.getJobName(), job.getCompanyName());
                    }
                    
                } catch (Exception e) {
                    log.error("处理岗位详情时发生异常: {}", e.getMessage());
                } finally {
                    if (jobPage != null) {
                            jobPage.close();
                    }
                }
                
                // 随机间隔，避免风控
                Thread.sleep(3000 + (int)(Math.random() * 2000));
                
        } catch (Exception e) {
                log.error("处理岗位失败: {}", e.getMessage());
                if (jobPage != null) {
                    jobPage.close();
                }
            }
        }
        
        return resultList.size();
    }

    // 添加格式化时间方法
    private static String formatDuration(Date start, Date end) {
        if (start == null || end == null) {
            return "未知";
        }
        
        long duration = end.getTime() - start.getTime();
        long hours = duration / (1000 * 60 * 60);
        long minutes = (duration % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (duration % (1000 * 60)) / 1000;
        
        if (hours > 0) {
            return String.format("%d小时%d分钟%d秒", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds);
                        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 收集推荐岗位信息 - 优化版：边加载边投递
     * @param page 页面对象
     * @param tabName Tab名称
     */
    private static void collectRecommendJobs(Page page, String tabName) {
        log.info("开始收集Tab[{}]的推荐岗位（边加载边投递模式）...", tabName);
        recommendJobs.clear();
        int totalProcessed = 0, totalFiltered = 0, totalDelivered = 0;
        int filteredByJobBlacklist = 0, filteredByKeyword = 0, filteredByApplied = 0, filteredByCompanyBlacklist = 0, filteredBySalary = 0, filteredByExperience = 0;
        Set<String> processedJobHrefs = new HashSet<>();
        int previousJobCount = 0, currentJobCount = 0, unchangedCount = 0;
        try {
            log.info("开始滚动加载推荐岗位（边加载边投递）...");
            while (unchangedCount < 3) {
                List<ElementHandle> jobCards = page.querySelectorAll("ul.rec-job-list li.job-card-box, ul.job-list-box li.job-card-wrapper");
                currentJobCount = jobCards.size();
                log.info("当前已加载推荐岗位数量: {}，开始处理新增岗位...", currentJobCount);
                for (int i = previousJobCount; i < currentJobCount; i++) {
                    try {
                        ElementHandle jobCard = jobCards.get(i);
                        ElementHandle linkElement = jobCard.querySelector("a");
                        if (linkElement == null) continue;
                        String href = linkElement.getAttribute("href");
                        if (href == null || href.isEmpty() || processedJobHrefs.contains(href)) continue;
                        processedJobHrefs.add(href);
                        totalProcessed++;
                        // 只用href初始化Job
                        Job job = new Job();
                        job.setHref(href);
                        Page jobPage = page.context().newPage();
                        boolean success = false;
                        try {
                            jobPage.navigate(homeUrl + href);
                            jobPage.waitForLoadState();
                            PlaywrightUtil.sleep(2);
                            // 用attemptJobDelivery抓取真实信息（会set岗位名/薪资/公司等）
                            success = attemptJobDelivery(job, jobPage);
                        } catch (Exception e) {
                            log.error("推荐岗位详情页异常: {}", e.getMessage());
                        }
                        // 过滤逻辑全部用详情页抓取到的内容
                        String jobName = cleanString(job.getJobName());
                        String companyName = cleanString(job.getCompanyName());
                        String salary = cleanString(job.getSalary());
                        String recruiter = cleanString(job.getRecruiter());
                        String jobArea = cleanString(job.getJobArea());
                        if (jobName.isEmpty()) jobName = "未知岗位";
                        if (companyName.isEmpty()) companyName = "未知公司";
                        // 关键词过滤（任意关键词通过）
                        boolean matchAnyKeyword = false;
                        for (String k : config.getKeywords()) {
                            if (jobName != null && jobName.toLowerCase().contains(k.toLowerCase())) {
                                matchAnyKeyword = true;
                                break;
                            }
                        }
                        if (!matchAnyKeyword) {
                            totalFiltered++; filteredByKeyword++;
                            log.info("❌ 推荐岗位被过滤: {}-{}，原因：关键词不符", companyName, jobName);
                            jobPage.close();
                            continue;
                        }
                        // 岗位黑名单
                        if (isInBlackList(blackJobItems, jobName, "岗位", jobName)) {
                            totalFiltered++; filteredByJobBlacklist++;
                            log.info("❌ 推荐岗位被过滤: {}-{}，原因：岗位黑名单", companyName, jobName);
                            jobPage.close();
                            continue;
                        }
                        // 已投递过滤
                        if (!allowRepeatApply && isAppliedJobValid(getUniqueKey(job))) {
                            totalFiltered++; filteredByApplied++;
                            log.info("❌ 推荐岗位被过滤: {}-{}，原因：已投递过", companyName, jobName);
                            jobPage.close();
                            continue;
                        }
                        // 公司黑名单
                        if (isInBlackList(blackCompanyItems, companyName, "公司", jobName)) {
                            totalFiltered++; filteredByCompanyBlacklist++;
                            log.info("❌ 推荐岗位被过滤: {}-{}，原因：公司黑名单", companyName, jobName);
                            jobPage.close();
                            continue;
                        }
                        // 薪资过滤
                        if (isSalaryNotExpected(salary)) {
                            totalFiltered++; filteredBySalary++;
                            log.info("❌ 推荐岗位被过滤: {}-{}，原因：薪资不符", companyName, jobName);
                            jobPage.close();
                            continue;
                        }
                        // 经验过滤（如有）
                        try {
                            java.lang.reflect.Method getExp = job.getClass().getMethod("getExperience");
                            Object jobExp = getExp.invoke(job);
                            if (jobExp != null && config.getExperience() != null && !config.getExperience().isEmpty()) {
                                boolean match = false;
                                for (String exp : config.getExperience()) {
                                    if (jobExp.toString().contains(exp)) {
                                        match = true;
                                        break;
                                    }
                                }
                                if (!match) {
                                    totalFiltered++; filteredByExperience++;
                                    log.info("❌ 推荐岗位被过滤: {}-{}，原因：经验不符", companyName, jobName);
                                    jobPage.close();
                                    continue;
                                }
                            }
                        } catch (Exception ignore) {}
                        // 通过所有过滤，投递
                        if (success) {
                            totalDelivered++;
                            resultList.add(job);
                            recommendJobs.add(job);
                            log.info("🎉 推荐岗位投递成功: {}-{} (第{}个成功投递)", companyName, jobName, totalDelivered);
                            // 实时写入md和HTML
                            java.util.List<String> jobDutyList = new java.util.ArrayList<>();
                            if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                                for (String duty : job.getJobInfo().split("[\n;；]")) {
                                    String d = duty.trim();
                                    if (!d.isEmpty()) jobDutyList.add(d);
                                }
                            }
                            utils.JobDetailsMdWriter.appendRecord(job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea(), job.getRecruiter(), "自动投递", jobDutyList, "success", null);
                            try { utils.DeliveryHtmlReporter.generateHtmlFromMd(); } catch (Exception e) { log.error("实时更新HTML失败: {}", e.getMessage()); }
                        } else {
                            totalFiltered++;
                            log.info("❌ 推荐岗位投递失败或被过滤: {}-{}", companyName, jobName);
                        }
                        jobPage.close();
                        Thread.sleep(3000 + (int)(Math.random() * 2000));
                    } catch (Exception e) {
                        log.error("处理推荐岗位卡片失败: {}", e.getMessage());
                    }
                }
                if (currentJobCount > previousJobCount) {
                    previousJobCount = currentJobCount;
                    unchangedCount = 0;
                    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                    log.info("继续滚动加载更多推荐岗位...");
                    page.waitForTimeout(3000 + (int)(Math.random() * 2000));
                } else {
                    unchangedCount++;
                    if (unchangedCount < 3) {
                        log.info("下拉后推荐岗位数量未增加，再次尝试...");
                        page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                        page.waitForTimeout(3000 + (int)(Math.random() * 2000));
                    } else {
                        log.info("连续{}次滚动后岗位数量未增加，停止加载", unchangedCount);
                        break;
                    }
                }
            }
            log.info("Tab[{}]推荐岗位处理完成 - 总计：{}，过滤：{}，投递：{}", tabName, totalProcessed, totalFiltered, totalDelivered);
            log.info("过滤明细：岗位黑名单:{}，关键词:{}，已投递:{}，公司黑名单:{}，薪资:{}，经验:{}", filteredByJobBlacklist, filteredByKeyword, filteredByApplied, filteredByCompanyBlacklist, filteredBySalary, filteredByExperience);
        } catch (Exception e) {
            log.error("收集推荐岗位失败: {}", e.getMessage());
        }
    }

    // 字符串清洗方法
    private static String cleanString(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\p{C};]", "").trim();
    }

    // === AI岗位匹配与打招呼语生成 ===
    private static ai.AiFilter checkJob(String keyword, String jobName, String jd) {
        ai.AiConfig aiConfig = ai.AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd, config.getSayHi());
        String result = ai.AiService.sendRequest(requestMessage);
        if (result.startsWith("AI服务未配置") || result.startsWith("AI请求失败") || result.startsWith("AI请求异常") || result.startsWith("AI请求超时")) {
            log.warn("AI服务调用失败: {}", result);
            return new ai.AiFilter(true);
        }
        return result.contains("false") ? new ai.AiFilter(false) : new ai.AiFilter(true, result);
    }
}