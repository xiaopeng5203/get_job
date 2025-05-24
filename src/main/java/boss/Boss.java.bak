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

    public static void main(String[] args) {
        loadData(dataPath);
        loadBlacklistTime();
        loadBlackItems();
        // 新增：加载去重配置
        loadRepeatConfig();
        PlaywrightUtil.init();
        startDate = new Date();
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
        if (!config.getDebugger()) {
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
                
                // 在第一个Tab处理前进行城市切换，确保切换完成后再继续
                boolean citySwitched = switchCityInRecommendPage(page, configCity);
                if (citySwitched) {
                    log.info("第一个Tab城市切换成功，等待页面数据刷新...");
                    // 额外等待时间，确保城市切换后的数据完全加载
                    page.waitForLoadState();
                    PlaywrightUtil.sleep(3); // 增加等待时间，确保数据刷新完成
                } else {
                    log.warn("第一个Tab城市切换失败，但继续处理当前Tab的岗位");
                }
                
                if (isJobsPresent()) {
                    try {
                        log.info("开始获取{}下的推荐岗位信息...", firstTabText);
                        int previousJobCount = 0;
                        int currentJobCount = 0;
                        int unchangedCount = 0;
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
                        log.info("已获取所有可加载推荐岗位，共计: " + currentJobCount + " 个");
                        Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, BossElementLocators.JOB_CARD_BOX);
                        int count = jobLocators.count();
                        List<Job> jobs = new ArrayList<>();
                        for (int j = 0; j < count; j++) {
                            try {
                                Locator jobCard = jobLocators.nth(j);
                                String jobName = jobCard.locator(BossElementLocators.JOB_NAME).textContent();
                                if (blackJobs.stream().anyMatch(jobName::contains)) {
                                    continue;
                                }
                                String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                    continue;
                                }
                                Job job = new Job();
                                job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                                job.setCompanyName(companyName);
                                job.setJobName(jobName);
                                job.setJobArea(jobCard.locator(BossElementLocators.JOB_AREA).textContent());
                                
                                // 新增：抓取推荐岗位的薪资信息（多选择器兜底）
                                String salary = "";
                                String[] salarySelectors = {
                                    ".job-limit .red", ".salary", ".job-salary", ".price", ".red"
                                };
                                for (String selector : salarySelectors) {
                                    try {
                                        Locator salaryLoc = jobCard.locator(selector);
                                        if (salaryLoc.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                            salary = salaryLoc.first().textContent().trim();
                                            if (!salary.isEmpty()) {
                                                break;
                                            }
                                        }
                                    } catch (Exception ex) {
                                        // 继续尝试下一个选择器
                                    }
                                }
                                job.setSalary(salary);
                                
                                Locator tagElements = jobCard.locator(BossElementLocators.TAG_LIST);
                                int tagCount = tagElements.count();
                                StringBuilder tag = new StringBuilder();
                                for (int k = 0; k < tagCount; k++) {
                                    tag.append(tagElements.nth(k).textContent()).append("·");
                                }
                                if (tag.length() > 0) {
                                    job.setCompanyTag(tag.substring(0, tag.length() - 1));
                                } else {
                                    job.setCompanyTag("");
                                }
                                jobs.add(job);
                            } catch (Exception e) {
                                log.debug("处理岗位卡片失败: {}", e.getMessage());
                            }
                        }
                        if (!jobs.isEmpty()) {
                            recommendJobs.clear();
                            recommendJobs.addAll(jobs);
                            log.info("开始投递Tab[{}]下的推荐岗位...", firstTabText);
                            processRecommendJobs();
                        } else {
                            log.info("Tab[{}]下无可投递岗位", firstTabText);
                        }
                    } catch (Exception e) {
                        log.error("滚动加载数据异常: {}", e.getMessage());
                    }
                }
                
                processed.add(firstTabText);
            }
            
            // 步骤2: 然后按照配置文件中指定的Tab优先级处理（模糊匹配）
            if (tabPriority != null && !tabPriority.isEmpty()) {
                for (String tabName : tabPriority) {
                    // 遍历所有Tab，找到第一个包含关键字的Tab
                    ElementHandle tab = null;
                    tabs = page.querySelectorAll("a.expect-item"); // 重新获取，防止DOM刷新
                    for (ElementHandle t : tabs) {
                        String tabText = t.textContent().trim();
                        if (tabText.contains(tabName) && !processed.contains(tabText)) { // 模糊匹配且未处理过
                            tab = t;
                            tabName = tabText; // 用实际Tab文本作为日志
                            break;
                        }
                    }
                    if (tab == null) continue;
                    log.info("按配置优先投递Tab(模糊匹配): {}", tabName);
                    tab.click();
                    page.waitForLoadState();
                    page.waitForTimeout(1000);
                    
                    // 在每个Tab处理前进行城市切换，确保切换完成后再继续
                    boolean citySwitched = switchCityInRecommendPage(page, configCity);
                    if (citySwitched) {
                        log.info("城市切换成功，等待页面数据刷新...");
                        // 额外等待时间，确保城市切换后的数据完全加载
                        page.waitForLoadState();
                        PlaywrightUtil.sleep(3); // 增加等待时间，确保数据刷新完成
                    } else {
                        log.warn("城市切换失败，但继续处理当前Tab的岗位");
                    }
                    if (isJobsPresent()) {
                        try {
                            log.info("开始获取推荐岗位信息...");
                            int previousJobCount = 0;
                            int currentJobCount = 0;
                            int unchangedCount = 0;
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
                            log.info("已获取所有可加载推荐岗位，共计: " + currentJobCount + " 个");
                            Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, BossElementLocators.JOB_CARD_BOX);
                            int count = jobLocators.count();
                            List<Job> jobs = new ArrayList<>();
                            for (int j = 0; j < count; j++) {
                                try {
                                    Locator jobCard = jobLocators.nth(j);
                                    String jobName = jobCard.locator(BossElementLocators.JOB_NAME).textContent();
                                    if (blackJobs.stream().anyMatch(jobName::contains)) {
                                        continue;
                                    }
                                    String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                                    if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                        continue;
                                    }
                                    Job job = new Job();
                                    job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                                    job.setCompanyName(companyName);
                                    job.setJobName(jobName);
                                    job.setJobArea(jobCard.locator(BossElementLocators.JOB_AREA).textContent());
                                    
                                    // 新增：抓取推荐岗位的薪资信息（多选择器兜底）
                                    String salary = "";
                                    String[] salarySelectors = {
                                        ".job-limit .red", ".salary", ".job-salary", ".price", ".red"
                                    };
                                    for (String selector : salarySelectors) {
                                        try {
                                            Locator salaryLoc = jobCard.locator(selector);
                                            if (salaryLoc.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                                salary = salaryLoc.first().textContent().trim();
                                                if (!salary.isEmpty()) {
                                                    break;
                                                }
                                            }
                                        } catch (Exception ex) {
                                            // 继续尝试下一个选择器
                                        }
                                    }
                                    job.setSalary(salary);
                                    
                                    Locator tagElements = jobCard.locator(BossElementLocators.TAG_LIST);
                                    int tagCount = tagElements.count();
                                    StringBuilder tag = new StringBuilder();
                                    for (int k = 0; k < tagCount; k++) {
                                        tag.append(tagElements.nth(k).textContent()).append("·");
                                    }
                                    if (tag.length() > 0) {
                                        job.setCompanyTag(tag.substring(0, tag.length() - 1));
                                    } else {
                                        job.setCompanyTag("");
                                    }
                                    jobs.add(job);
                                } catch (Exception e) {
                                    log.debug("处理岗位卡片失败: {}", e.getMessage());
                                }
                            }
                            if (!jobs.isEmpty()) {
                                recommendJobs.clear();
                                recommendJobs.addAll(jobs);
                                log.info("开始投递Tab[{}]下的推荐岗位...", tabName);
                                processRecommendJobs();
                            } else {
                                log.info("Tab[{}]下无可投递岗位", tabName);
                            }
                        } catch (Exception e) {
                            log.error("滚动加载数据异常: {}", e.getMessage());
                        }
                    }
                    processed.add(tabName);
                }
            }
            
            // 步骤3: 处理未在配置中的Tab，按页面顺序（跳过已处理的）
            // 重新获取Tab列表，确保获取到最新的DOM状态
            tabs = page.querySelectorAll("a.synthesis, a.expect-item");
            for (ElementHandle tab : tabs) {
                String tabText = tab.textContent().trim();
                if (processed.contains(tabText)) continue;
                log.info("投递剩余Tab: {}", tabText);
                tab.click();
                page.waitForLoadState();
                page.waitForTimeout(1000);
                
                // 在每个Tab处理前进行城市切换，确保切换完成后再继续
                boolean citySwitched = switchCityInRecommendPage(page, configCity);
                if (citySwitched) {
                    log.info("城市切换成功，等待页面数据刷新...");
                    // 额外等待时间，确保城市切换后的数据完全加载
                    page.waitForLoadState();
                    PlaywrightUtil.sleep(3); // 增加等待时间，确保数据刷新完成
                } else {
                    log.warn("城市切换失败，但继续处理当前Tab的岗位");
                }
                if (isJobsPresent()) {
                    try {
                        log.info("开始获取推荐岗位信息...");
                        int previousJobCount = 0;
                        int currentJobCount = 0;
                        int unchangedCount = 0;
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
                        log.info("已获取所有可加载推荐岗位，共计: " + currentJobCount + " 个");
                        Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, BossElementLocators.JOB_CARD_BOX);
                        int count = jobLocators.count();
                        List<Job> jobs = new ArrayList<>();
                        for (int j = 0; j < count; j++) {
                            try {
                                Locator jobCard = jobLocators.nth(j);
                                String jobName = jobCard.locator(BossElementLocators.JOB_NAME).textContent();
                                if (blackJobs.stream().anyMatch(jobName::contains)) {
                                    continue;
                                }
                                String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                    continue;
                                }
                                Job job = new Job();
                                job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                                job.setCompanyName(companyName);
                                job.setJobName(jobName);
                                job.setJobArea(jobCard.locator(BossElementLocators.JOB_AREA).textContent());
                                
                                // 新增：抓取推荐岗位的薪资信息（多选择器兜底）
                                String salary = "";
                                String[] salarySelectors = {
                                    ".job-limit .red", ".salary", ".job-salary", ".price", ".red"
                                };
                                for (String selector : salarySelectors) {
                                    try {
                                        Locator salaryLoc = jobCard.locator(selector);
                                        if (salaryLoc.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                            salary = salaryLoc.first().textContent().trim();
                                            if (!salary.isEmpty()) {
                                                break;
                                            }
                                        }
                                    } catch (Exception ex) {
                                        // 继续尝试下一个选择器
                                    }
                                }
                                job.setSalary(salary);
                                
                                Locator tagElements = jobCard.locator(BossElementLocators.TAG_LIST);
                                int tagCount = tagElements.count();
                                StringBuilder tag = new StringBuilder();
                                for (int k = 0; k < tagCount; k++) {
                                    tag.append(tagElements.nth(k).textContent()).append("·");
                                }
                                if (tag.length() > 0) {
                                    job.setCompanyTag(tag.substring(0, tag.length() - 1));
                                } else {
                                    job.setCompanyTag("");
                                }
                                jobs.add(job);
                            } catch (Exception e) {
                                log.debug("处理岗位卡片失败: {}", e.getMessage());
                            }
                        }
                        if (!jobs.isEmpty()) {
                            recommendJobs.clear();
                            recommendJobs.addAll(jobs);
                            log.info("开始投递Tab[{}]下的推荐岗位...", tabText);
                            processRecommendJobs();
                        } else {
                            log.info("Tab[{}]下无可投递岗位", tabText);
                        }
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
        Object salaryObj = config.getSalary();
        if (salaryObj instanceof List) {
            return (List<String>) salaryObj;
        } else if (salaryObj instanceof String) {
            return java.util.Collections.singletonList((String) salaryObj);
        } else {
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
            
            // 等待页面完全加载
            page.waitForLoadState();
            PlaywrightUtil.sleep(2); // 增加等待时间，确保页面完全加载
            
            // 查找页面头部城市按钮（多种可能的选择器）
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
                } catch (Exception ex) {
                    // 继续尝试下一个选择器
                }
            }
            
            if (cityBtn == null) {
                log.error("未找到页面头部城市按钮");
                // 输出页面中所有可能的城市相关元素
                try {
                    List<ElementHandle> allElements = page.querySelectorAll("*");
                    for (ElementHandle element : allElements) {
                        String text = element.textContent();
                        String className = element.getAttribute("class");
                        if ((text != null && (text.contains("城市") || text.contains("成都") || text.contains("香港") || text.contains("北京") || text.contains("上海"))) ||
                            (className != null && (className.contains("city") || className.contains("location")))) {
                            log.info("可能的城市元素: class={}, text={}", className, text);
                        }
                    }
                } catch (Exception e) {
                    log.error("调试输出失败: {}", e.getMessage());
                }
                return false;
            } else if (currentCity.contains(targetCity) || targetCity.contains(currentCity) || 
                       (currentCity.length() <= 2 && targetCity.contains(currentCity))) {
                log.info("页面当前城市已是[{}]，无需切换", targetCity);
                return true;
            } else {
                log.info("当前城市[{}]与目标城市[{}]不符，开始切换", currentCity, targetCity);
                
                // 点击城市按钮，弹出城市选择弹窗
                cityBtn.click();
                PlaywrightUtil.sleep(1);
                
                // 等待城市选择弹窗出现（多种可能的弹窗选择器）
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
                    } catch (Exception e) {
                        // 继续尝试下一个选择器
                    }
                }
                
                if (!dialogFound) {
                    log.error("城市选择弹窗未出现");
                    // 输出页面中所有可能的弹窗元素
                    try {
                        List<ElementHandle> allElements = page.querySelectorAll("div, section, aside");
                        for (ElementHandle element : allElements) {
                            String className = element.getAttribute("class");
                            String id = element.getAttribute("id");
                            if ((className != null && (className.contains("dialog") || className.contains("modal") || className.contains("popup") || className.contains("city"))) ||
                                (id != null && (id.contains("dialog") || id.contains("modal") || id.contains("popup") || id.contains("city")))) {
                                log.info("可能的弹窗元素: class={}, id={}, visible={}", className, id, element.isVisible());
                            }
                        }
                    } catch (Exception e) {
                        log.error("调试输出弹窗失败: {}", e.getMessage());
                    }
                    return false;
                }
                
                // 在弹窗中查找并点击目标城市（支持热门城市和按拼音首字母分类的城市）
                boolean citySelected = false;
                
                // 1. 首先尝试在热门城市中查找
                String[] hotCitySelectors = {
                    "ul.city-list-hot > li:has-text(\"" + targetCity + "\")",
                    ".hot-city li:has-text(\"" + targetCity + "\")",
                    ".city-item:has-text(\"" + targetCity + "\")",
                    "li:has-text(\"" + targetCity + "\")",
                    "span:has-text(\"" + targetCity + "\")",
                    "div:has-text(\"" + targetCity + "\")",
                    "text=" + targetCity,
                    "[data-city=\"" + targetCity + "\"]",
                    "[data-city-name=\"" + targetCity + "\"]"
                };
                
                for (String citySelector : hotCitySelectors) {
                    try {
                        Locator cityOption = page.locator(citySelector);
                        if (cityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                            cityOption.click();
                            log.info("在热门城市中找到并点击[{}]，选择器：{}", targetCity, citySelector);
                            citySelected = true;
                            break;
                        }
                    } catch (Exception ex) {
                        // 继续尝试下一个选择器
                    }
                }
                
                // 2. 如果热门城市中没有找到，则按拼音首字母查找
                if (!citySelected) {
                    log.info("热门城市中未找到[{}]，尝试按拼音首字母查找", targetCity);
                    
                    // 获取城市拼音首字母
                    String firstLetter = getCityFirstLetter(targetCity);
                    if (firstLetter != null) {
                        log.info("城市[{}]的拼音首字母为：{}", targetCity, firstLetter);
                        
                        // 点击对应的字母Tab
                        String[] letterTabSelectors = {
                            "span:has-text(\"" + firstLetter + "\")",
                            ".letter-tab:has-text(\"" + firstLetter + "\")",
                            ".city-letter:has-text(\"" + firstLetter + "\")",
                            "li:has-text(\"" + firstLetter + "\")",
                            "[data-letter=\"" + firstLetter + "\"]"
                        };
                        
                        boolean letterTabClicked = false;
                        for (String tabSelector : letterTabSelectors) {
                            try {
                                Locator letterTab = page.locator(tabSelector);
                                if (letterTab.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                    letterTab.click();
                                    log.info("已点击字母Tab[{}]，选择器：{}", firstLetter, tabSelector);
                                    letterTabClicked = true;
                                    PlaywrightUtil.sleep(1); // 等待城市列表加载
                                    break;
                                }
                            } catch (Exception ex) {
                                // 继续尝试下一个选择器
                            }
                        }
                        
                        // 点击字母Tab后，在对应的城市列表中查找目标城市
                        if (letterTabClicked) {
                            // 使用更精确的选择器，确保完全匹配城市名称
                            String[] cityInLetterSelectors = {
                                "li:text-is(\"" + targetCity + "\")",  // 精确文本匹配
                                "span:text-is(\"" + targetCity + "\")",
                                "div:text-is(\"" + targetCity + "\")",
                                "a:text-is(\"" + targetCity + "\")",
                                ".city-list li:text-is(\"" + targetCity + "\")",
                                ".city-item:text-is(\"" + targetCity + "\")",
                                ".city-list li:has-text(\"" + targetCity + "\")",
                                ".city-item:has-text(\"" + targetCity + "\")",
                                "li:has-text(\"" + targetCity + "\")",
                                "span:has-text(\"" + targetCity + "\")",
                                "div:has-text(\"" + targetCity + "\")",
                                "a:has-text(\"" + targetCity + "\")",
                                "text=" + targetCity,
                                "[data-city-name=\"" + targetCity + "\"]",
                                "[data-city=\"" + targetCity + "\"]"
                            };
                            
                            for (String citySelector : cityInLetterSelectors) {
                                try {
                                    Locator cityOption = page.locator(citySelector);
                                    if (cityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                                        cityOption.click();
                                        log.info("在字母[{}]分类下找到并点击城市[{}]，选择器：{}", firstLetter, targetCity, citySelector);
                                        citySelected = true;
                                        break;
                                    }
                                } catch (Exception ex) {
                                    // 继续尝试下一个选择器
                                }
                            }
                        }
                    }
                }
                
                // 3. 如果按字母分类还是没找到，就在整个弹窗中搜索
                if (!citySelected) {
                    log.info("按字母分类未找到[{}]，尝试在整个弹窗中搜索", targetCity);
                    
                    String[] globalCitySelectors = {
                        "li:text-is(\"" + targetCity + "\")",  // 精确文本匹配
                        "span:text-is(\"" + targetCity + "\")",
                        "div:text-is(\"" + targetCity + "\")",
                        "a:text-is(\"" + targetCity + "\")",
                        ".city-name:text-is(\"" + targetCity + "\")",
                        "li:has-text(\"" + targetCity + "\")",
                        "span:has-text(\"" + targetCity + "\")",
                        "div:has-text(\"" + targetCity + "\")",
                        "a:has-text(\"" + targetCity + "\")",
                        ".city-name:has-text(\"" + targetCity + "\")",
                        "[title=\"" + targetCity + "\"]",
                        "[alt=\"" + targetCity + "\"]",
                        "text=" + targetCity
                    };
                    
                    for (String citySelector : globalCitySelectors) {
                        try {
                            Locator cityOption = page.locator(citySelector);
                            if (cityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                                cityOption.click();
                                log.info("在整个弹窗中找到并点击城市[{}]，选择器：{}", targetCity, citySelector);
                                citySelected = true;
                                break;
                            }
                        } catch (Exception ex) {
                            // 继续尝试下一个选择器
                        }
                    }
                }
                
                if (!citySelected) {
                    log.error("未找到弹窗中的城市选项[{}]", targetCity);
                    
                    // 尝试关闭弹窗并重试一次
                    try {
                        log.info("尝试关闭弹窗并重试城市切换");
                        // 尝试按ESC键关闭弹窗
                        page.keyboard().press("Escape");
                        PlaywrightUtil.sleep(1);
                        
                        // 重新点击城市按钮
                        cityBtn.click();
                        PlaywrightUtil.sleep(2);
                        
                        // 再次尝试查找城市
                        String[] retryCitySelectors = {
                            "li:text-is(\"" + targetCity + "\")",
                            "span:text-is(\"" + targetCity + "\")", 
                            "div:text-is(\"" + targetCity + "\")",
                            "li:has-text(\"" + targetCity + "\")",
                            "span:has-text(\"" + targetCity + "\")",
                            "div:has-text(\"" + targetCity + "\")",
                            "text=" + targetCity
                        };
                        
                        for (String citySelector : retryCitySelectors) {
                            try {
                                Locator cityOption = page.locator(citySelector);
                                if (cityOption.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                                    cityOption.click();
                                    log.info("重试成功：找到并点击城市[{}]，选择器：{}", targetCity, citySelector);
                                    citySelected = true;
                                    break;
                                }
                            } catch (Exception ex) {
                                // 继续尝试下一个选择器
                            }
                        }
                    } catch (Exception retryEx) {
                        log.error("重试城市切换失败: {}", retryEx.getMessage());
                    }
                    
                    if (!citySelected) {
                        log.error("重试后仍未找到城市选项[{}]，跳过此次城市切换", targetCity);
                        // 尝试关闭弹窗
                        try {
                            page.keyboard().press("Escape");
                            PlaywrightUtil.sleep(1);
                        } catch (Exception e) {
                            // 忽略关闭弹窗的错误
                        }
                                                 return false;
                     }
                }
                
                // 城市选择成功后的处理逻辑（无论是第一次成功还是重试成功）
                if (citySelected) {
                    // 等待页面刷新和数据加载
                    log.info("等待页面刷新和城市数据加载...");
                    page.waitForLoadState();
                    PlaywrightUtil.sleep(5); // 增加等待时间，确保城市切换后的数据重新加载
                    
                    // 强化城市切换验证机制，多次验证确保切换成功
                    boolean cityVerified = false;
                    int verifyAttempts = 0;
                    int maxVerifyAttempts = 3;
                    
                    while (!cityVerified && verifyAttempts < maxVerifyAttempts) {
                        verifyAttempts++;
                        log.info("第{}次验证城市切换结果...", verifyAttempts);
                        
                        try {
                            // 等待页面稳定
                            PlaywrightUtil.sleep(2);
                            
                            for (String selector : cityButtonSelectors) {
                                try {
                                    Locator btn = page.locator(selector);
                                    if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                                        String newCurrentCity = btn.textContent().trim();
                                        log.info("城市切换后验证，当前显示城市：{}", newCurrentCity);
                                        
                                        // 更严格的城市验证逻辑
                                        if (newCurrentCity.contains(targetCity) || targetCity.contains(newCurrentCity) ||
                                            (newCurrentCity.length() > 1 && !newCurrentCity.equals("城市") && 
                                             (newCurrentCity.contains(targetCity.substring(0, Math.min(2, targetCity.length()))) ||
                                              targetCity.contains(newCurrentCity.substring(0, Math.min(2, newCurrentCity.length())))))) {
                                            cityVerified = true;
                                            log.info("城市切换验证成功，已切换到[{}]", targetCity);
                                            break;
                                        }
                                    }
                                } catch (Exception ex) {
                                    // 继续尝试下一个选择器
                                }
                            }
                            
                            if (!cityVerified && verifyAttempts < maxVerifyAttempts) {
                                log.info("第{}次验证失败，等待后重试...", verifyAttempts);
                                PlaywrightUtil.sleep(3); // 等待更长时间再次验证
                            }
                        } catch (Exception e) {
                            log.warn("第{}次城市切换验证过程出错: {}", verifyAttempts, e.getMessage());
                        }
                    }
                    
                    if (cityVerified) {
                        log.info("城市切换验证成功，目标城市[{}]", targetCity);
                        return true;
                    } else {
                        log.warn("经过{}次验证，城市切换验证失败，可能页面显示有延迟或切换未成功", maxVerifyAttempts);
                        return false; // 验证失败，返回false
                    }
                }
                
                // 如果城市选择失败，返回false
                return false;
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
            // 判断页面是否存在岗位的元素
            PlaywrightUtil.waitForElement(JOB_LIST_CONTAINER);
            return true;
        } catch (Exception e) {
            log.error("加载岗位区块失败:{}", e.getMessage());
            return false;
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
                    
                    // 处理职位详情页 (包括抓取字段、投递、写入文件等完整流程)
                    int result = processJobDetail(jobPage, job, keyword);
                    if (result < 0) {
                        jobPage.close();
                        if (result == -1) {
                            log.info("在非推荐岗位投递中检测到投递已达每日上限，准备关闭所有窗口并退出程序...");
                            // 构造推送内容
                            StringBuilder sb = new StringBuilder();
                            sb.append("【Boss直聘】今日投递汇总\n");
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
                        continue;
                    }
                    
                    if (result == 0) {
                        // 投递成功，记录唯一标识+时间戳
                        appliedJobs.add(uniqueKey + "|" + System.currentTimeMillis());
                        saveAppliedJobs();
                    }
                    
                    // 关闭页面
                    jobPage.close();
                    
                    // 非推荐岗位投递间隔，避免风控
                    PlaywrightUtil.sleep(2 + (int)(Math.random() * 2)); // 随机等待2-4秒
                    
                    // 调试模式下退出循环
                    if (config.getDebugger()) {
                        break;
                    }
                } catch (Exception e) {
                    log.error("处理岗位详情页异常：公司[{}] 岗位[{}]，异常：{}", job.getCompanyName(), job.getJobName(), e.getMessage());
                    if (jobPage != null) {
                        try {
                            jobPage.close();
                        } catch (Exception ex) {
                            log.error("关闭页面异常：{}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("处理推荐岗位详情页异常：公司[{}] 岗位[{}] 招聘者[{}]，异常：{}", job.getCompanyName(), job.getJobName(), job.getRecruiter(), e.getMessage());
                if (jobPage != null) {
                    try {
                        jobPage.close();
                    } catch (Exception ex) {
                        log.error("关闭页面异常：{}", ex.getMessage());
                    }
                }
            }
        }
        return 0;
    }

    // 保留 isValidString 方法
    public static boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    // 清理文本内容，去除HTML标签和多余空白字符
    private static String cleanText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // 去除HTML标签
        String cleaned = text.replaceAll("<[^>]*>", "");
        
        // 去除多余的空白字符和换行符
        cleaned = cleaned.replaceAll("\\s+", " ");
        
        // 去除首尾空白
        cleaned = cleaned.trim();
        
        // 去除特殊的HTML实体
        cleaned = cleaned.replaceAll("&nbsp;", " ");
        cleaned = cleaned.replaceAll("&amp;", "&");
        cleaned = cleaned.replaceAll("&lt;", "<");
        cleaned = cleaned.replaceAll("&gt;", ">");
        cleaned = cleaned.replaceAll("&quot;", "\"");
        
        return cleaned;
    }

    // 保留 isDeadHR 方法
    private static boolean isDeadHR(Page page) {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            // 尝试获取 HR 的活跃时间
            Locator activeTimeElement = page.locator(HR_ACTIVE_TIME).first();
            String outerHtml = activeTimeElement.evaluate("el => el.outerHTML").toString();

            if (activeTimeElement.isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
                String activeTimeText = activeTimeElement.textContent();
                log.info("{}：{}", getCompanyAndHR(page).replaceAll("\\s+", ""), activeTimeText);
                // 如果 HR 活跃状态符合预期，则返回 true
                return containsDeadStatus(activeTimeText, config.getDeadStatus());
            }
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", getCompanyAndHR(page).replaceAll("\\s+", ""));
        }
        return false;
    }

    public static boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;// 一旦找到包含的值，立即返回 true
            }
        }
        return false;// 如果没有找到，返回 false
    }

    private static String getCompanyAndHR(Page page) {
        try {
            Locator element = page.locator(RECRUITER_INFO).first();
            if (element.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                return element.textContent().replaceAll("\n", "");
            }
        } catch (Exception e) {
            log.debug("获取公司和HR信息失败: {}", e.getMessage());
        }
        return "未知公司和HR";
    }

    private static AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd,
                config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        
        // 检查AI服务返回是否包含错误信息
        if (result.startsWith("AI服务未配置") || result.startsWith("AI请求失败") || result.startsWith("AI请求异常") || result.startsWith("AI请求超时")) {
            log.warn("AI服务调用失败: {}", result);
            // 如果AI服务不可用，返回一个默认的匹配结果，确保投递流程能继续
            return new AiFilter(true);
        }
        
        // 正常处理AI返回结果
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
    }

    private static boolean isTargetJob(String keyword, String jobName) {
        boolean keywordIsAI = false;
        for (String target : new String[]{"大模型", "AI"}) {
            if (keyword.contains(target)) {
                keywordIsAI = true;
                break;
            }
        }

        boolean jobIsDesign = false;
        for (String designOrVision : new String[]{"设计", "视觉", "产品", "运营"}) {
            if (jobName.contains(designOrVision)) {
                jobIsDesign = true;
                break;
            }
        }

        boolean jobIsAI = false;
        for (String target : new String[]{"AI", "人工智能", "大模型", "生成"}) {
            if (jobName.contains(target)) {
                jobIsAI = true;
                break;
            }
        }

        if (keywordIsAI) {
            if (jobIsDesign) {
                return false;
            } else if (!jobIsAI) {
                return true;
            }
        }
        return true;
    }

    private static Integer[] parseSalaryRange(String salaryText) {
        try {
            if (salaryText == null || salaryText.trim().isEmpty()) return null;
            // 只保留"数字-数字K"或"数字K"部分，忽略"·XX薪"等
            String mainPart = salaryText.split("·")[0]; // 只取"10-14K"
            String[] parts = mainPart.split("-");
            if (parts.length == 2) {
                String minStr = parts[0].replaceAll("[^0-9]", "");
                String maxStr = parts[1].replaceAll("[^0-9]", "");
                if (minStr.isEmpty() || maxStr.isEmpty()) return null;
                int min = Integer.parseInt(minStr);
                int max = Integer.parseInt(maxStr);
                return new Integer[]{min, max};
            } else if (parts.length == 1) {
                String valStr = parts[0].replaceAll("[^0-9]", "");
                if (valStr.isEmpty()) return null;
                int val = Integer.parseInt(valStr);
                return new Integer[]{val};
            }
        } catch (Exception e) {
            log.error("薪资解析异常！{}，原始薪资文本：{}", e.getMessage(), salaryText, e);
        }
        return null;
    }

    private static boolean isLimit() {
        try {
            PlaywrightUtil.sleep(1);
            com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
            Locator dialogElement = page.locator(DIALOG_CON);
            if (dialogElement.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                String text = dialogElement.textContent();
                if (text.contains("已达上限")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("检测投递上限时发生异常: {}", e.getMessage());
            return false;
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("打开Boss直聘网站中...");

        // 使用Playwright打开网站
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(homeUrl);
        Page h5Page = PlaywrightUtil.getPageObject(PlaywrightUtil.DeviceType.MOBILE);
        if (!ObjectUtils.isEmpty(h5Page)) {
            h5Page.navigate(homeUrl);
        }


        // 检查并加载Cookie
        if (isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            page.reload();
            if (!ObjectUtils.isEmpty(h5Page)) {
                h5Page.reload();
            }
            PlaywrightUtil.sleep(2);
        }

        // 检查是否需要登录
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
        
        // 账号登录后刷新配置
        refreshConfig();
    }
    
    /**
     * 刷新配置，确保账号切换后配置正确加载
     */
    private static void refreshConfig() {
        try {
            // 重新加载各项配置
            config = BossConfig.init();
            h5Config = H5BossConfig.init();
            loadBlackItems();
            loadRepeatConfig();
            log.info("已重新加载配置");
            
            // 清理和重新加载申请记录
            loadData(dataPath);
            log.info("已重新加载黑名单和申请记录");
        } catch (Exception e) {
            log.error("刷新配置失败: {}", e.getMessage());
        }
    }

    // 检查cookie是否有效的方法，替换SeleniumUtil的实现
    private static boolean isCookieValid(String cookiePath) {
        try {
            String cookieContent = new String(Files.readAllBytes(Paths.get(cookiePath)));
            return cookieContent != null && !cookieContent.equals("[]") && cookieContent.contains("name");
        } catch (Exception e) {
            log.error("读取cookie文件失败: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isLoginRequired() {
        try {
            Page page = PlaywrightUtil.getPageObject();

            // 检查是否有登录按钮
            Locator loginButton = page.locator(BossElementLocators.LOGIN_BTNS);
            if (loginButton.isVisible() && loginButton.textContent().contains("登录")) {
                return true;
            }

            // 检查是否有错误页面
            try {
                Locator pageHeader = page.locator(BossElementLocators.PAGE_HEADER);
                if (pageHeader.isVisible()) {
                    Locator errorPageLogin = page.locator(BossElementLocators.ERROR_PAGE_LOGIN);
                    if (errorPageLogin.isVisible()) {
                        errorPageLogin.click();
                        return true;
                    }
                }
            } catch (Exception ex) {
                log.info("没有出现403访问异常");
            }

            log.info("cookie有效，已登录...");
            return false;
        } catch (Exception e) {
            log.error("检查登录状态出错: {}", e.getMessage());
            return true; // 遇到错误，默认需要登录
        }
    }

    @SneakyThrows
    private static void scanLogin() {
        // 使用Playwright进行登录操作
        Page page = PlaywrightUtil.getPageObject();
        // 访问登录页面
        page.navigate(homeUrl + "/web/user/?ka=header-login");
        PlaywrightUtil.sleep(3);

        // 1. 如果已经登录，则直接返回
        try {
            Locator loginBtn = page.locator(BossElementLocators.LOGIN_BTN);
            if (loginBtn.isVisible() && !loginBtn.textContent().equals("登录")) {
                log.info("已经登录，直接开始投递...");
                return;
            }
        } catch (Exception ignored) {
        }

        log.info("等待登录...");

        // 2. 定位二维码登录的切换按钮
        Locator scanButton = page.locator(BossElementLocators.LOGIN_SCAN_SWITCH);
        boolean scanButtonVisible = scanButton.isVisible(new Locator.IsVisibleOptions().setTimeout(30000));
        if (!scanButtonVisible) {
            log.error("未找到二维码登录按钮，登录失败");
            return;
        }

        // 3. 登录逻辑
        boolean login = false;

        // 4. 记录开始时间，用于判断10分钟超时
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 10 * 60 * 1000; // 10分钟

        // 5. 用于监听用户是否在控制台回车
        Scanner scanner = new Scanner(System.in);

        while (!login) {
            // 如果已经超过10分钟，退出程序
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= TIMEOUT) {
                log.error("超过10分钟未完成登录，程序退出...");
                System.exit(1);
            }

            try {
                // 尝试点击二维码按钮并等待页面出现已登录的元素
                scanButton.click();
                // 等待登录成功标志
                boolean loginSuccess = page.locator(BossElementLocators.LOGIN_SUCCESS_HEADER)
                        .isVisible(new Locator.IsVisibleOptions().setTimeout(2000));

                // 如果找到登录成功元素，说明登录成功
                if (loginSuccess) {
                    login = true;
                    log.info("登录成功！保存cookie...");
                } else {
                    // 登录失败
                    log.error("登录失败，等待用户操作或者 2 秒后重试...");

                    // 每次登录失败后，等待2秒，同时检查用户是否按了回车
                    boolean userInput = waitForUserInputOrTimeout(scanner);
                    if (userInput) {
                        log.info("检测到用户输入，继续尝试登录...");
                    }
                }
            } catch (Exception e) {
                // scanButton.click() 可能已经登录成功，没有这个扫码登录按钮
                boolean loginSuccess = page.locator(BossElementLocators.LOGIN_SUCCESS_HEADER)
                        .isVisible(new Locator.IsVisibleOptions().setTimeout(2000));
                if (loginSuccess) {
                    login = true;
                    log.info("登录成功！保存cookie...");
                }
            }
        }

        // 登录成功后，保存Cookie
        PlaywrightUtil.saveCookies(cookiePath);
    }

    /**
     * 在指定的毫秒数内等待用户输入回车；若在等待时间内用户按回车则返回 true，否则返回 false。
     *
     * @param scanner 用于读取控制台输入
     * @return 用户是否在指定时间内按回车
     */
    private static boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                // 判断输入流中是否有可用字节
                if (System.in.available() > 0) {
                    // 读取一行（用户输入）
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                // 读取输入流异常，直接忽略
            }

            // 小睡一下，避免 CPU 空转
            SeleniumUtil.sleep(1);
        }
        return false;
    }


    private static void postH5JobByCityByPlaywright(String cityCode) {
        // 新增：根据cityCode反查城市名
        String cityName = null;
        for (H5BossEnum.CityCode cc : H5BossEnum.CityCode.values()) {
            if (cc.getCode().equals(cityCode)) {
                cityName = cc.getName();
                break;
            }
        }
        if (cityName == null) cityName = cityCode; // 兜底

        Page page = PlaywrightUtil.getPageObject(PlaywrightUtil.DeviceType.MOBILE);
        for (String keyword : h5Config.getKeywords()) {
            String searchUrl = getH5SearchUrl(cityCode, keyword);
            log.info("查询url:{}", searchUrl);
            try {
                log.info("开始投递，页面url：{}", searchUrl);
                page.navigate(searchUrl);
                if (isH5JobsPresent(page)) {
                    Set<String> processedJobIds = new HashSet<>();
                    int previousCount = 0;
                    int retry = 0;
                    while (true) {
                        // 1. 获取当前页面所有岗位卡片
                        List<ElementHandle> jobCards = page.querySelectorAll("ul li.item");
                        int currentCount = jobCards.size();
                        // 2. 对新出现的岗位做本地过滤和投递
                        List<Job> jobs = new ArrayList<>();
                        for (ElementHandle jobCard : jobCards) {
                            String jobHref = jobCard.querySelector("a").getAttribute("href");
                            if (processedJobIds.contains(jobHref)) continue;
                            processedJobIds.add(jobHref);
                            // 复用h5ResumeSubmission的过滤逻辑
                            // 只收集通过本地过滤的岗位
                            boolean pass = true;
                            String recruiterText = jobCard.querySelector("div.recruiter div.name").textContent();
                            String salary = jobCard.querySelector("div.title span.salary").textContent();
                            String jobName = jobCard.querySelector("div.title span.title-text").textContent();
                            String companyName = jobCard.querySelector("div.name span.company").textContent();
                            if (h5Config.getStrictLocalFilter() != null && h5Config.getStrictLocalFilter()) {
                                if (isInBlackList(blackRecruiterItems, recruiterText, "招聘者", jobHref)) { pass = false; }
                                if (isInBlackList(blackJobItems, jobName, "岗位", jobName) || !isTargetJob(keyword, jobName)) { pass = false; }
                                if (isInBlackList(blackCompanyItems, companyName, "公司", jobName)) { pass = false; }
                                if (isSalaryNotExpected(salary)) { pass = false; }
                                if (config.getKeyFilter()) {
                                    boolean matchAnyKeyword = false;
                                    for (String k : config.getKeywords()) {
                                        if (jobName.toLowerCase().contains(k.toLowerCase())) {
                                            matchAnyKeyword = true;
                                            break;
                                        }
                                    }
                                    if (!matchAnyKeyword) { pass = false; }
                                }
                            }
                            if (pass) {
                                Job job = new Job();
                                job.setHref(jobHref);
                                job.setJobName(jobName);
                                job.setJobArea(jobCard.querySelector("div.name span.workplace").textContent());
                                job.setSalary(salary);
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
                                job.setCompanyName(companyName);
                                job.setRecruiter(recruiterText);
                                jobs.add(job);
                            }
                        }
                        // 3. 对通过过滤的岗位立即投递
                        if (!jobs.isEmpty()) {
                            // 只处理新加载的岗位
                            processJobListDetails(jobs, keyword, page, cityName);
                        }
                        // 4. 下拉加载更多
                        page.evaluate("window.scrollTo(0, document.documentElement.scrollHeight + 100)");
                        page.waitForTimeout(10000);
                        if (currentCount == previousCount) {
                            retry++;
                            log.info("第{}次下拉重试", retry);
                            if (retry >= 2) {
                                log.info("尝试2次下拉后无新增岗位，退出");
                                break;
                            }
                        } else {
                            retry = 0;
                        }
                        previousCount = currentCount;
                        if (config.getDebugger()) {
                            break;
                        }
                    }
                    log.info("已加载全部岗位，总数量: " + previousCount);
                }
                // chat页面进行消息沟通，传递cityName
                h5ResumeSubmission(keyword, page, cityName);
            } catch (Exception e) {
                log.error("使用Playwright处理页面时出错: {}", e.getMessage(), e);
            }
        }
    }

    private static String getH5SearchUrl(String cityCode, String keyword) {
        // 经验
        List<String> experience = h5Config.getExperience();
        // 学历
        List<String> degree = h5Config.getDegree();
        // 薪资
        Object salaryObj = h5Config.getSalary();
        String salary;
        if (salaryObj instanceof List) {
            List<?> salaryList = (List<?>) salaryObj;
            salary = salaryList.isEmpty() ? "" : String.valueOf(salaryList.get(0));
        } else if (salaryObj != null) {
            salary = String.valueOf(salaryObj);
        } else {
            salary = "";
        }
        // 规模
        List<String> scale = h5Config.getScale();

        String searchUrl = baseUrl;

        log.info("cityCode:{}", cityCode);
        log.info("experience:{}", experience);
        log.info("degree:{}", degree);
        log.info("salary:{}", salary);
        if (!H5BossEnum.CityCode.NULL.equals(cityCode)) {
            searchUrl = searchUrl + "/" + cityCode + "/";
        }

        Set<String> ydeSet = new LinkedHashSet<>();
        if (!experience.isEmpty()) {
            if (!H5BossEnum.Salary.NULL.equals(salary)) {
                ydeSet.add(salary);
            }
        }

        if (!degree.isEmpty()) {
            String degreeStr = degree.stream().findFirst().get();
            if (!H5BossEnum.Degree.NULL.equals(degreeStr)) {
                ydeSet.add(degreeStr);
            }
        }
        if (!experience.isEmpty()) {
            String experienceStr = experience.stream().findFirst().get();
            if (!H5BossEnum.Experience.NULL.equals(experienceStr)) {
                ydeSet.add(experienceStr);
            }
        }

        if (!scale.isEmpty()) {
            String scaleStr = scale.stream().findFirst().get();
            if (!H5BossEnum.Scale.NULL.equals(scaleStr)) {
                ydeSet.add(scaleStr);
            }
        }


        String yde = ydeSet.stream().collect(Collectors.joining("-"));
        log.info("yde:{}", yde);
        if (StringUtils.hasLength(yde)) {
            if (!searchUrl.endsWith("/")) {
                searchUrl = searchUrl + "/" + yde + "/";
            } else {
                searchUrl = searchUrl + yde + "/";
            }
        }

        searchUrl = searchUrl + "?query=" + keyword;
        searchUrl = searchUrl + "&ka=sel-salary-" + salary.split("_")[1];
        return searchUrl;
    }


    private static boolean isH5JobsPresent(Page page) {
        try {
            page.waitForSelector("li.item", new Page.WaitForSelectorOptions().setTimeout(40000));
            return true;
        } catch (Exception e) {
            log.warn("页面上没有找到职位列表: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断薪资是否不符合期望
     * @param salaryText 薪资文本
     * @return 是否不符合期望
     */
    private static boolean isSalaryNotExpected(String salaryText) {
        try {
            // 解析薪资范围
            Integer[] range = parseSalaryRange(salaryText);
            if (range == null || range.length == 0) {
                return false;
            }
            
            // 获取期望薪资
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (expectedSalary == null || expectedSalary.isEmpty()) {
                return false;
            }
            
            // 如果只有一个值，认为是最低期望薪资
            if (expectedSalary.size() == 1) {
                int minExpect = expectedSalary.get(0);
                // 如果实际薪资上限低于期望下限，不符合要求
                return range[range.length - 1] < minExpect;
            }
            
            // 如果有两个值，认为是期望薪资范围
            if (expectedSalary.size() >= 2) {
                int minExpect = expectedSalary.get(0);
                int maxExpect = expectedSalary.get(1);
                // 检查薪资范围是否有交集
                if (range.length >= 2) {
                    int minActual = range[0];
                    int maxActual = range[range.length - 1];
                    // 没有交集则不符合要求
                    return maxActual < minExpect || minActual > maxExpect;
                } else if (range.length == 1) {
                    // 单值薪资与期望范围比较
                    return range[0] < minExpect || range[0] > maxExpect;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("薪资检查异常: {}", e.getMessage());
            return false;
        }
    }

    // === 新增：黑名单过滤逻辑 ===
    static boolean isInBlackList(List<BlackItem> list, String name, String type, String jobName) {
        String typeKey = type.equals("公司") ? "companies" : type.equals("岗位") ? "jobs" : "recruiters";
        if (name == null) return false;
        String n = name.replaceAll("[\\s\\u00A0]", "").replaceAll("[·/|\\-]", "");
        for (BlackItem item : list) {
            if (item.name != null) {
                String regex = item.name;
                // 自动加忽略大小写前缀
                if (!regex.startsWith("(?i)")) regex = "(?i)" + regex;
                try {
                    if (java.util.regex.Pattern.compile(regex).matcher(n).find()) {
                        // 日志：打印黑名单和岗位名
                        log.info("正则黑名单项: [{}], 当前岗位名: [{}]", regex, n);
                // 首次命中记录 addTime 并持久化
                if (item.days != null && item.addTime == null) {
                    item.addTime = System.currentTimeMillis();
                    Map<String, Object> map = blacklistTimeData.getOrDefault(typeKey, new HashMap<>());
                    Map<String, Object> v = new HashMap<>();
                    v.put("addTime", item.addTime);
                    v.put("days", item.days);
                    map.put(item.name, v);
                    blacklistTimeData.put(typeKey, map);
                    saveBlacklistTime();
                }
                if (item.isExpired()) continue;
                long remain = item.remainDays();
                        log.info("已过滤：{}正则黑名单命中【{}】，剩余有效天数：{}，岗位【{}】", type, item.name, remain == Long.MAX_VALUE ? "永久" : remain + "天", jobName);
                return true;
                    }
                } catch (Exception e) {
                    log.warn("黑名单正则有误: [{}]", regex);
                }
            }
        }
        return false;
    }

    static boolean isInBlackCompanyRecruiterList(List<BlackCompanyRecruiterItem> list, String company, String recruiter, String jobName) {
        String typeKey = "companyRecruiters";
        for (BlackCompanyRecruiterItem item : list) {
            if (company != null && recruiter != null && company.equals(item.company) && recruiter.equals(item.recruiter)) {
                // 首次命中记录 addTime 并持久化
                if (item.days != null && item.addTime == null) {
                    item.addTime = System.currentTimeMillis();
                    Map<String, Object> map = blacklistTimeData.getOrDefault(typeKey, new HashMap<>());
                    Map<String, Object> v = new HashMap<>();
                    v.put("addTime", item.addTime);
                    v.put("days", item.days);
                    map.put(item.company + "|" + item.recruiter, v);
                    blacklistTimeData.put(typeKey, map);
                    saveBlacklistTime();
                }
                if (item.isExpired()) continue;
                long remain = item.remainDays();
                log.info("已过滤：公司+招聘者黑名单命中【{}|{}】，剩余有效天数：{}，岗位【{}】", item.company, item.recruiter, remain == Long.MAX_VALUE ? "永久" : remain + "天", jobName);
                return true;
            }
        }
        return false;
    }

    // 新增方法
    static void addToBlackCompanyRecruiterBlacklist(String company, String recruiter, int days) {
        // 先查重
        for (BlackCompanyRecruiterItem item : blackCompanyRecruiterItems) {
            if (company.equals(item.company) && recruiter.equals(item.recruiter)) {
                return;
            }
        }
        BlackCompanyRecruiterItem newItem = new BlackCompanyRecruiterItem(company, recruiter, days, System.currentTimeMillis());
        blackCompanyRecruiterItems.add(newItem);
        // 持久化到blacklist_time.json
        Map<String, Object> map = blacklistTimeData.getOrDefault("companyRecruiters", new HashMap<>());
        Map<String, Object> v = new HashMap<>();
        v.put("addTime", newItem.addTime);
        v.put("days", newItem.days);
        map.put(company + "|" + recruiter, v);
        blacklistTimeData.put("companyRecruiters", map);
        saveBlacklistTime();
        log.info("自动加入公司+招聘者黑名单：【{}|{}】，有效期{}天", company, recruiter, days);
    }

    /**
     * 判断岗位是否属于指定城市（兼容多字段、多城市、代码/名称、逗号分隔等）
     */
    private static boolean isJobInCity(Job job, String configCity) {
        if (job == null || configCity == null) return false;
        // 支持多字段
        String[] cityFields = new String[] {
            job.getJobArea(),
            job.getJobInfo(),
            job.getDetailAddress()
        };
        // 支持多城市配置
        String[] configCities = configCity.split(",|，|/|;| |");
        for (String field : cityFields) {
            if (field == null) continue;
            String[] jobCities = field.split(",|，|/|;| |");
            for (String jc : jobCities) {
                String jcNorm = jc.replaceAll("[市区县]", "").trim();
                for (String cc : configCities) {
                    String ccNorm = cc.replaceAll("[市区县]", "").trim();
                    if (!ccNorm.isEmpty() && (jcNorm.contains(ccNorm) || ccNorm.contains(jcNorm))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断岗位是否严格命中关键词（名称、描述/职责，完整词组匹配）
     */
    private static boolean isJobMatchKeyword(Job job, List<String> keywords) {
        if (job == null || keywords == null || keywords.isEmpty()) return true;
        String jobName = job.getJobName() == null ? "" : job.getJobName();
        String jobDesc = job.getJobKeywordTag() == null ? "" : job.getJobKeywordTag();
        for (String kw : keywords) {
            if (kw == null || kw.trim().isEmpty()) continue;
            // 名称严格完整词组匹配
            if (jobName.contains(kw)) return true;
        }
        // 名称未命中，再查描述/职责
        for (String kw : keywords) {
            if (kw == null || kw.trim().isEmpty()) continue;
            if (jobDesc.contains(kw)) return true;
        }
        return false;
    }

    // 3. 新增方法：加载去重配置
    static void loadRepeatConfig() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> data = mapper.readValue(new java.io.File(dataPath), java.util.HashMap.class);
            allowRepeatApply = config.getAllowRepeatApply() != null && config.getAllowRepeatApply();
            appliedJobs.clear();
            if (data.get(appliedJobsKey) instanceof java.util.List) {
                for (Object o : (java.util.List<?>) data.get(appliedJobsKey)) {
                    if (o != null) appliedJobs.add(o.toString());
                }
            }
        } catch (Exception e) {
            log.warn("加载appliedJobs失败: {}", e.getMessage());
        }
    }

    // 4. 新增方法：保存 appliedJobs 到 data.json
    static void saveAppliedJobs() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> data = mapper.readValue(new java.io.File(dataPath), java.util.HashMap.class);
            data.put(appliedJobsKey, new java.util.ArrayList<>(appliedJobs));
            // 使用美化输出，纵向排列
            mapper.writerWithDefaultPrettyPrinter().writeValue(new java.io.File(dataPath), data);
        } catch (Exception e) {
            log.warn("保存appliedJobs失败: {}", e.getMessage());
        }
    }

    // 修改appliedJobs为带时间戳的唯一标识：公司|岗位核心名|薪资|时间戳
    // 保存时 appliedJobs.add(uniqueKey + "|" + System.currentTimeMillis());
    // 判断时，解析时间戳，若未过期则跳过，过期则允许重新投递
    private static boolean isAppliedJobValid(String uniqueKey) {
        long expireDays = config.getDeliverExpireDays() == null ? 30 : config.getDeliverExpireDays();
        long now = System.currentTimeMillis();
        for (String record : appliedJobs) {
            String[] arr = record.split("\\|");
            if (arr.length < 4) continue;
            String key = arr[0] + "|" + arr[1] + "|" + arr[2];
            long ts = 0;
            try { ts = Long.parseLong(arr[3]); } catch (Exception ignore) {}
            if (uniqueKey.equals(key)) {
                if (now - ts < expireDays * 24 * 60 * 60 * 1000L) {
                    return true; // 未过期，不可重复投递
                }
            }
        }
        return false;
    }
    // 在 processJobListDetails、processRecommendJobs 等所有投递前判断 isAppliedJobValid(uniqueKey)
    // 投递成功后保存 appliedJobs.add(uniqueKey + "|" + System.currentTimeMillis());

    /**
     * 处理推荐岗位列表，进行过滤和投递
     * 流程：
     * 1. 遍历所有推荐岗位列表(recommendJobs)
     * 2. 针对每个岗位进行多重过滤：
     *    - 检查是否已投递过(去重)
     *    - 检查岗位名称是否包含关键词(keyFilter)
     *    - 检查是否在黑名单中
     * 3. 对通过过滤的岗位进行投递
     * 
     * 注意：推荐岗位投递与普通岗位投递共用同一个过滤和投递逻辑，
     * 只是获取岗位列表的方式不同
     */
    private static void processRecommendJobs() {
        log.info("开始处理推荐岗位，共{}个", recommendJobs.size());
        // 先将所有岗位按关键词过滤
        List<Job> filteredJobs = new ArrayList<>();
        for (Job job : recommendJobs) {
            // 首先检查是否已投递过
            String uniqueKey = getUniqueKey(job);
            if (!allowRepeatApply && isAppliedJobValid(uniqueKey)) {
                log.info("已投递过该岗位，跳过：{}", uniqueKey);
                continue;
            }
            
            // 检查岗位名称是否包含关键词（保证逻辑与postJobByCityByPlaywright一致）
            if (config.getKeyFilter()) {
                boolean matchAnyKeyword = false;
                for (String k : config.getKeywords()) {
                    if (job.getJobName().toLowerCase().contains(k.toLowerCase())) {
                        matchAnyKeyword = true;
                        break;
                    }
                }
                if (!matchAnyKeyword) {
                    log.info("已过滤：岗位【{}】名称不包含任意关键字{}", job.getJobName(), config.getKeywords());
                    continue;
                }
            }
            
            // 检查是否在黑名单中
            if (isInBlackList(blackJobItems, job.getJobName(), "岗位", job.getJobName())) {
                log.info("已过滤：岗位在黑名单中【{}】", job.getJobName());
                continue;
            }
            
            if (isInBlackList(blackCompanyItems, job.getCompanyName(), "公司", job.getJobName())) {
                log.info("已过滤：公司在黑名单中【{}】", job.getCompanyName());
                continue;
            }
            
            // 通过了所有过滤条件，加入待处理列表
            filteredJobs.add(job);
        }
        
        log.info("过滤后剩余待投递岗位：{}个", filteredJobs.size());
        
        // 处理过滤后的岗位
        for (Job job : filteredJobs) {
            Page jobPage = null;
            try {
                String uniqueKey = getUniqueKey(job);
                
                jobPage = PlaywrightUtil.getPageObject().context().newPage();
                jobPage.navigate(homeUrl + job.getHref());
                
                // 等待页面加载
                jobPage.waitForLoadState();
                PlaywrightUtil.sleep(2); // 增加等待时间，避免风控
                
                // 处理职位详情页 (薪资过滤和黑名单公司过滤等)
                int result = processJobDetail(jobPage, job, job.getJobName());
                if (result < 0) {
                    jobPage.close();
                    if (result == -1) {
                        log.info("在推荐岗位投递中检测到投递已达每日上限，准备关闭所有窗口并退出程序...");
                        // 构造推送内容
                        StringBuilder sb = new StringBuilder();
                        sb.append("【Boss直聘】今日投递汇总\n");
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
                    continue;
                }
                
                if (result == 0) {
                    // 投递成功，记录唯一标识+时间戳
                    appliedJobs.add(uniqueKey + "|" + System.currentTimeMillis());
                    saveAppliedJobs();
                }
                
                // 关闭页面
                jobPage.close();
                
                // 推荐岗位投递间隔，避免风控
                PlaywrightUtil.sleep(3);
                
                // 调试模式下退出循环
                if (config.getDebugger()) {
                    break;
                }
            } catch (Exception e) {
                log.error("处理推荐岗位详情页异常：公司[{}] 岗位[{}] 招聘者[{}]，异常：{}", job.getCompanyName(), job.getJobName(), job.getRecruiter(), e.getMessage());
                if (jobPage != null) {
                    try {
                        jobPage.close();
                    } catch (Exception ex) {
                        log.error("关闭页面异常：{}", ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 处理职位详情
     * @param page 页面对象
     * @param job 职位对象
     * @param keyword 搜索关键词
     * @return 处理结果，0表示成功，-1表示达到上限，其他负数表示其他错误
     */
    private static int processJobDetail(Page page, Job job, String keyword) {
        try {
            // 等待页面加载
            page.waitForLoadState();
            
            // 检查是否达到投递上限
            if (isLimit()) {
                log.info("检测到投递上限，停止投递");
                return -1;
            }
            
            // 检查HR活跃度
            if (isDeadHR(page)) {
                log.info("已过滤：HR不活跃，公司[{}] 岗位[{}]", job.getCompanyName(), job.getJobName());
                return -2;
            }
            
            // 检查招聘者是否在黑名单中
            String recruiterInfo = PlaywrightUtil.getRecruiterInfoFromBossJob(page);
            String recruiterName = recruiterInfo.split("\\|")[0].trim();
            job.setRecruiter(recruiterName);
            
            if (isInBlackList(blackRecruiterItems, recruiterName, "招聘者", job.getJobName())) {
                log.info("已过滤：招聘者在黑名单中，公司[{}] 岗位[{}] 招聘者[{}]", job.getCompanyName(), job.getJobName(), recruiterName);
                return -3;
            }
            
            // 在点击"立即沟通"按钮之前，先抓取所有需要的字段信息
            String salary = "";
            String jobDesc = "";
            String recruiter = "";
            
            // 抓取薪资（多选择器兜底）
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
                            log.info("通过选择器[{}]成功抓取薪资：{}", selector, salary);
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // 继续尝试下一个选择器
                }
            }
            if (salary == null || salary.isEmpty()) {
                log.error("未能抓取到薪资字段，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                return -99;
            }
            
            // 抓取职位描述/职责/要求（多选择器兜底）
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
                            log.info("通过选择器[{}]成功抓取职位描述，长度：{}", selector, jobDesc.length());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // 继续尝试下一个选择器
                }
            }
            if (jobDesc == null || jobDesc.isEmpty()) {
                log.error("未能抓取到职位描述/职责/要求字段，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                return -99;
            }
            
            // 抓取招聘者（多选择器兜底）
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
                            log.info("通过选择器[{}]成功抓取招聘者：{}", selector, recruiter);
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // 继续尝试下一个选择器
                }
            }
            if (recruiter == null || recruiter.isEmpty()) {
                log.error("未能抓取到招聘者字段，页面结构可能变更！页面HTML片段：{}", page.content().substring(0, Math.min(1000, page.content().length())));
                return -99;
            }
            
            // 将抓取到的信息保存到job对象中，供后续使用
            job.setSalary(salary);
            job.setJobKeywordTag(jobDesc);
            job.setRecruiter(recruiter);
            
            // 检查是否有"立即沟通"按钮
            Locator chatButton = page.locator(BossElementLocators.CHAT_BUTTON).first();
            if (!chatButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                log.info("无法找到沟通按钮，公司[{}] 岗位[{}]", job.getCompanyName(), job.getJobName());
                return -4;
            }
            
            // 使用AI检查岗位匹配度（如果启用）
            if (config.getEnableAI() && ai.AiConfig.init() != null) {
                ai.AiFilter filter = checkJob(keyword, job.getJobName(), job.getJobKeywordTag());
                if (!filter.isMatch()) {
                    log.info("AI判断不匹配，公司[{}] 岗位[{}]", job.getCompanyName(), job.getJobName());
                    return -5;
                }
                // 使用jobInfo字段保存AI打招呼语
                job.setJobInfo(filter.getResult());
                log.info("AI打招呼语已生成: {}", filter.getResult());
            }
            
            // 点击"立即沟通"按钮
            chatButton.click();
            
            // 等待聊天框出现（支持多种聊天框版本）
            boolean chatInputFound = false;
            Locator chatInput = null;
            
            // 尝试等待旧版聊天输入框出现
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
            
            // 尝试查找新版聊天输入框
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
                    } catch (Exception ex) {
                        // 继续尝试下一个选择器
                    }
                }
            }
            
            // 如果仍未找到聊天输入框，检查是否有弹窗
            if (!chatInputFound) {
                log.info("未找到任何聊天输入框，检查是否有弹窗...");
                // 检查是否出现了弹窗
                try {
                    // 检查各种可能的弹窗
                    Locator dialog = page.locator(BossElementLocators.POPUP_CONTAINER);
                    if (dialog.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                        String dialogText = dialog.textContent();
                        log.info("检测到弹窗内容: {}", dialogText);
                        
                        // 检查是否是问题型对话框
                        Locator questionDialog = page.locator(BossElementLocators.POPUP_QUESTION_DIALOG);
                        if (questionDialog.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                            log.info("检测到问题对话框，尝试填写并提交...");
                            Locator questionInput = page.locator(BossElementLocators.POPUP_QUESTION_INPUT);
                            if (questionInput.isVisible()) {
                                // 填写默认回复
                                questionInput.fill("您好，我对贵公司的岗位非常感兴趣，希望能有机会与您进一步交流。");
                                PlaywrightUtil.sleep(1);
                                
                                // 尝试点击确认按钮
                                Locator confirmBtn = page.locator(BossElementLocators.POPUP_CONFIRM_BUTTON);
                                if (confirmBtn.isVisible()) {
                                    confirmBtn.click();
                                    PlaywrightUtil.sleep(2);
                                }
                            }
                        } else {
                            // 普通弹窗，尝试点击确认或关闭按钮
                            Locator confirmBtn = page.locator(BossElementLocators.POPUP_CONFIRM_BUTTON);
                            if (confirmBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                log.info("点击弹窗确认按钮");
                                confirmBtn.click();
                                PlaywrightUtil.sleep(2);
                            } else {
                                // 尝试点击关闭按钮
                                Locator closeBtn = page.locator(BossElementLocators.POPUP_CLOSE_BUTTON);
                                if (closeBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                    log.info("点击弹窗关闭按钮");
                                    closeBtn.click();
                                    PlaywrightUtil.sleep(2);
                                }
                            }
                            
                            // 处理弹窗后再次检测聊天输入框
                            for (String selector : (BossElementLocators.CHAT_INPUT + ", " + BossElementLocators.CHAT_INPUT_ALT).split(", ")) {
                                try {
                                    Locator input = page.locator(selector);
                                    if (input.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                        chatInput = input;
                                        chatInputFound = true;
                                        log.info("处理弹窗后找到聊天输入框: {}", selector);
                                        break;
                                    }
                                } catch (Exception inputEx) {
                                    // 继续尝试下一个选择器
                                }
                            }
                        }
                        
                        // 处理弹窗后再次检测聊天输入框
                        for (String selector : (BossElementLocators.CHAT_INPUT + ", " + BossElementLocators.CHAT_INPUT_ALT).split(", ")) {
                            try {
                                Locator input = page.locator(selector);
                                if (input.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                    chatInput = input;
                                    chatInputFound = true;
                                    log.info("处理弹窗后找到聊天输入框: {}", selector);
                                    break;
                                }
                            } catch (Exception inputEx) {
                                // 继续尝试下一个选择器
                            }
                        }
                    }
                } catch (Exception dialogEx) {
                    log.error("处理弹窗失败: {}", dialogEx.getMessage());
                }
                
                // 如果所有尝试都失败，则截图并返回错误
                if (!chatInputFound) {
                    log.error("尝试所有方法后仍未找到聊天输入框，放弃当前岗位");
                    
                    // 截图记录问题
                    try {
                        String screenshotPath = "/tmp/boss_chat_error_" + System.currentTimeMillis() + ".png";
                        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)));
                        log.info("已保存页面截图到: {}", screenshotPath);
                    } catch (Exception ssEx) {
                        log.error("保存截图失败: {}", ssEx.getMessage());
                    }
                    
                    return -6; // 无法找到聊天输入框
                }
            }
            
            // 到这里chatInput一定不为null且已找到
            // 从Job对象中获取打招呼语，优先使用jobInfo字段
            String sayHi = config.getSayHi();
            if (job.getJobInfo() != null && !job.getJobInfo().isEmpty()) {
                sayHi = job.getJobInfo();
            }
            
            // 尝试填写聊天内容
            try {
                chatInput.fill(sayHi);
                log.info("已填写打招呼语");
            } catch (Exception e) {
                log.error("填写打招呼语失败，尝试其他方式: {}", e.getMessage());
                
                // 尝试JavaScript方式填写
                try {
                    page.evaluate("el => el.textContent = '" + sayHi.replace("'", "\\'") + "'", chatInput);
                    log.info("通过JavaScript填写打招呼语");
                } catch (Exception jsEx) {
                    log.error("JavaScript填写失败: {}", jsEx.getMessage());
                    return -7; // 无法填写打招呼语
                }
            }
            
            // 点击发送按钮
            boolean sendSuccess = false;
            
            // 尝试旧版发送按钮
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
            
            // 尝试新版发送按钮
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
                    } catch (Exception ex) {
                        // 继续尝试下一个选择器
                    }
                }
            }
            
            // 按Enter键尝试发送
            if (!sendSuccess) {
                try {
                    chatInput.press("Enter");
                    sendSuccess = true;
                    log.info("使用Enter键发送消息");
                } catch (Exception e) {
                    log.error("使用Enter键发送失败: {}", e.getMessage());
                }
            }
            
            // 检查消息是否发送成功
            PlaywrightUtil.sleep(1);
            
            // 添加到结果列表
            resultList.add(job);
            log.info("已向公司[{}] 岗位[{}] 发送消息: {}", job.getCompanyName(), job.getJobName(), sayHi);
            
            // 发送图片简历
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
                    // 1. 尝试标准选择器
                    Locator imageUploadInput = page.locator(BossElementLocators.IMAGE_UPLOAD);
                    if (imageUploadInput.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        File imageFile = new File(resumePath);
                        if (imageFile.exists()) {
                            imageUploadInput.setInputFiles(Paths.get(imageFile.getAbsolutePath()));
                            resumeSent = true;
                            log.info("已通过标准选择器发送图片简历");
                        }
                    }
                    // 2. 备选选择器
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
                    // 3. 图片按钮+上传
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
                    // 4. 仅图片按钮+上传方式才自动点击"发送"按钮
                    if (resumeSent && needSendBtn) {
                        PlaywrightUtil.sleep(2); // 等待图片上传
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
                    // 新增：等待图片缩略图出现或强制sleep，确保图片真正上传
                    if (resumeSent) {
                        try {
                            // 等待图片缩略图出现，确保图片真正上传完成
                            page.waitForSelector(".chat-img, .image-thumb, .img-preview, .image-preview, .uploaded-image", new Page.WaitForSelectorOptions().setTimeout(8000));
                            log.info("图片缩略图已出现，图片上传完成");
                        } catch (Exception e) {
                            // 如果没有找到缩略图，强制等待5秒确保图片上传完成
                            log.info("未找到图片缩略图，强制等待5秒确保图片上传完成");
                            PlaywrightUtil.sleep(5);
                        }
                    }
                } catch (Exception e) {
                    log.error("发送图片简历时出错: {}", e.getMessage());
                }
            }

            // 写入岗位信息到 job_details.md（只保留用户需要的字段）
            try {
                String jobDetailPath = "/Users/gupeng/get_jobs-feature-job-boss-mobile/src/main/resources/job_details/job_details.md";
                
                // 使用之前抓取的字段信息
                String salaryToWrite = job.getSalary() == null ? "" : job.getSalary();
                String jobDescToWrite = job.getJobKeywordTag() == null ? "" : job.getJobKeywordTag();
                String recruiterToWrite = job.getRecruiter() == null ? "" : job.getRecruiter();
                
                // 格式化时间
                String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                // AI打招呼语
                String aiSayHi = job.getJobInfo() == null ? "" : job.getJobInfo();
                
                // 清理抓取的内容，去除HTML标签和多余空白
                String cleanJobName = cleanText(job.getJobName());
                String cleanCompanyName = cleanText(job.getCompanyName());
                String cleanJobArea = cleanText(job.getJobArea());
                String cleanSalary = cleanText(salaryToWrite);
                String cleanRecruiter = cleanText(recruiterToWrite);
                String cleanJobDesc = cleanText(jobDescToWrite);
                String cleanAiSayHi = cleanText(aiSayHi);
                
                // 拼接内容（严格按照用户模板格式）
                String content = String.format(
                    "**岗位名称：**%s\n" +
                    "**公司名称：**%s\n" +
                    "**工作地点：** %s\n" +
                    "**薪资：**%s\n" +
                    "**招聘者：**%s\n" +
                    "**职位描述/职责/要求：**\n%s\n" +
                    "**抓取时间：**%s\n" +
                    "**AI打招呼语：**\n\n%s\n",
                    cleanJobName,
                    cleanCompanyName,
                    cleanJobArea,
                    cleanSalary,
                    cleanRecruiter,
                    cleanJobDesc,
                    now,
                    cleanAiSayHi
                );
                Files.write(Paths.get(jobDetailPath), content.getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                log.info("已写入岗位信息到 job_details.md");
            } catch (Exception e) {
                log.error("写入岗位信息到 job_details.md 失败: {}", e.getMessage());
            }
            
            return 0;
        } catch (Exception e) {
            log.error("处理岗位详情异常: {}", e.getMessage());
            return -99;
        }
    }
}
