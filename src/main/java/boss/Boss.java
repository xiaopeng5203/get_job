package boss;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        if (config.getH5Jobs()) {
            h5Config.getCityCode().forEach(Boss::postH5JobByCityByPlaywright);
        }
        if (recommendJobs.isEmpty() && config.getRecommendJobs()) {
            getRecommendJobs();
            // 处理推荐职位
            int recommendResult = processRecommendJobs();
        }
        config.getCityCode().forEach(Boss::postJobByCityByPlaywright);
        log.info(resultList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}",
                resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        if (!config.getDebugger()) {
            printResult();
        }
    }

    private static void printResult() {
        String message = String.format("\nBoss投递完成，共发起%d个聊天，用时%s", resultList.size(),
                formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        saveData(dataPath);
        resultList.clear();
        if (!config.getDebugger()) {
            PlaywrightUtil.close();
        }
    }

    /**
     * 推荐岗位
     */
    private static void getRecommendJobs() {
        Page page = PlaywrightUtil.getPageObject();
        PlaywrightUtil.loadCookies(cookiePath);
        page.navigate("https://www.zhipin.com/web/geek/jobs");

        // 等待页面加载
        page.waitForLoadState();

        try {
            // 等待元素出现，最多等待10秒
            page.waitForSelector("a.expect-item", new Page.WaitForSelectorOptions().setTimeout(10000));

            // 获取a标签且class是expect-item的元素
            ElementHandle activeElement = page.querySelector("a.expect-item");

            if (activeElement != null) {
                log.info("找到'expect-item'元素，准备点击");
                // 点击该元素
                activeElement.click();
                // 点击后等待页面响应
                page.waitForLoadState();
                log.info("已点击'expect-item'元素");


                if (isJobsPresent()) {
                    // 尝试滚动页面加载更多数据
                    try {
                        // 获取岗位列表并下拉加载更多
                        log.info("开始获取推荐岗位信息...");

                        // 记录下拉前后的岗位数量
                        int previousJobCount = 0;
                        int currentJobCount = 0;
                        int unchangedCount = 0;

                        while (unchangedCount < 2) {
                            // 获取所有岗位卡片
                            List<ElementHandle> jobCards = page.querySelectorAll(JOB_LIST_SELECTOR);
                            currentJobCount = jobCards.size();

                            log.info("当前已加载岗位数量:{} ", currentJobCount);

                            // 判断是否有新增岗位
                            if (currentJobCount > previousJobCount) {
                                previousJobCount = currentJobCount;
                                unchangedCount = 0;

                                // 滚动到页面底部加载更多
                                PlaywrightUtil.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                                log.info("下拉页面加载更多...");

                                // 等待新内容加载
                                page.waitForTimeout(2000);
                            } else {
                                unchangedCount++;
                                if (unchangedCount < 2) {
                                    System.out.println("下拉后岗位数量未增加，再次尝试...");
                                    // 再次尝试滚动
                                    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                                    page.waitForTimeout(2000);
                                } else {
                                    break;
                                }
                            }
                        }

                        log.info("已获取所有可加载推荐岗位，共计: " + currentJobCount + " 个");


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
                                if (blackJobs.stream().anyMatch(jobName::contains)) {
                                    // 排除黑名单岗位
                                    continue;
                                }
                                String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                    // 排除黑名单公司
                                    continue;
                                }


                                Job job = new Job();
                                job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                                job.setCompanyName(companyName);
                                job.setJobName(jobName);
                                job.setJobArea(jobCard.locator(BossElementLocators.JOB_AREA).textContent());
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

                                recommendJobs.add(job);
                            } catch (Exception e) {
                                log.debug("处理岗位卡片失败: {}", e.getMessage());
                            }
                        }

                    } catch (Exception e) {
                        log.error("滚动加载数据异常: {}", e.getMessage());
                    }
                }
            } else {
                log.error("未找到class为'expect-item'的a标签元素");
            }
        } catch (Exception e) {
            log.error("寻找或点击'expect-item'元素时出错: {}", e.getMessage());
        }

    }


    private static void postJobByCityByPlaywright(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        for (String keyword : config.getKeywords()) {
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + "&query=" + encodedKeyword;
            log.info("查询岗位链接:{}", url);
            Page page = PlaywrightUtil.getPageObject();
            PlaywrightUtil.loadCookies(cookiePath);
            page.navigate(url);

            // 记录下拉前后的岗位数量
            int previousJobCount = 0;
            int currentJobCount = 0;
            int unchangedCount = 0;

            if (isJobsPresent()) {
                // 尝试滚动页面加载更多数据
                try {
                    // 获取岗位列表并下拉加载更多
                    log.info("开始获取岗位信息...");

                    while (unchangedCount < 2) {
                        // 获取所有岗位卡片
                        List<ElementHandle> jobCards = page.querySelectorAll(JOB_LIST_SELECTOR);
                        currentJobCount = jobCards.size();

                        log.info("当前已加载岗位数量:{} ", currentJobCount);

                        // 判断是否有新增岗位
                        if (currentJobCount > previousJobCount) {
                            previousJobCount = currentJobCount;
                            unchangedCount = 0;

                            // 滚动到页面底部加载更多
                            PlaywrightUtil.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                            log.info("下拉页面加载更多...");

                            // 等待新内容加载
                            page.waitForTimeout(2000);
                        } else {
                            unchangedCount++;
                            if (unchangedCount < 2) {
                                System.out.println("下拉后岗位数量未增加，再次尝试...");
                                // 再次尝试滚动
                                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                                page.waitForTimeout(2000);
                            } else {
                                break;
                            }
                        }
                    }

                    log.info("已获取所有可加载岗位，共计: " + currentJobCount + " 个");

                    log.info("继续滚动加载更多岗位");
                } catch (Exception e) {
                    log.error("滚动加载数据异常: {}", e.getMessage());
                    break;
                }
            }

            resumeSubmission(keyword);
        }
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

    private static String getSearchUrl(String cityCode) {
        return baseUrl + JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobType()) +
                JobUtils.appendParam("salary", config.getSalary()) +
                JobUtils.appendListParam("experience", config.getExperience()) +
                JobUtils.appendListParam("degree", config.getDegree()) +
                JobUtils.appendListParam("scale", config.getScale()) +
                JobUtils.appendListParam("industry", config.getIndustry()) +
                JobUtils.appendListParam("stage", config.getStage());
    }

    private static void saveData(String path) {
        try {
            updateListData();
            Map<String, Set<String>> data = new HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            String json = customJsonFormat(data);
            Files.write(Paths.get(path), json.getBytes());
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
    }


    @SneakyThrows
    private static Integer h5ResumeSubmission(String keyword) {
        // 查找所有job卡片元素
        Page page = PlaywrightUtil.getPageObject(PlaywrightUtil.DeviceType.MOBILE);
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

            if (blackRecruiters.stream().anyMatch(recruiterText::contains)) {
                // 排除黑名单招聘人员
                continue;
            }
            String jobName = jobCard.querySelector("div.title span.title-text").textContent();
            if (blackJobs.stream().anyMatch(jobName::contains) || !isTargetJob(keyword, jobName)) {
                // 排除黑名单岗位
                continue;
            }
            String companyName = jobCard.querySelector("div.name span.company").textContent();
            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                // 排除黑名单公司
                continue;
            }
            if (isSalaryNotExpected(salary)) {
                // 过滤薪资
                log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", companyName, jobName, salary);
                continue;
            }

            if (config.getKeyFilter()) {
                if (!jobName.toLowerCase().contains(keyword.toLowerCase())) {
                    log.info("已过滤：岗位【{}】名称不包含关键字【{}】", jobName, keyword);
                    continue;
                }
            }

            Job job = new Job();
            // 获取职位链接
            job.setHref(jobHref);
            // 获取职位名称
            job.setJobName(jobName);
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
            job.setCompanyName(companyName);
            // 设置招聘者信息
            job.setRecruiter(recruiterText);
            jobs.add(job);
        }

        // 处理每个职位详情
        int result = processJobList(jobs, keyword);
        if (result < 0) {
            return result;
        }

        return resultList.size();
    }


    @SneakyThrows
    private static Integer resumeSubmission(String keyword) {
        // 查找所有job卡片元素
        Page page = PlaywrightUtil.getPageObject();
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


                if (blackJobs.stream().anyMatch(jobName::contains) || !isTargetJob(keyword, jobName)) {
                    // 排除黑名单岗位
                    continue;
                }


                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                    // 排除黑名单公司
                    continue;
                }

                if (config.getKeyFilter()) {
                    String jobNameLower = jobName.toLowerCase();
                    String jobKeywordTagLower = job.getJobKeywordTag() != null ? job.getJobKeywordTag().toLowerCase() : "";
                    String keywordLower = keyword.toLowerCase();
                    if (!(jobNameLower.contains(keywordLower) || jobKeywordTagLower.contains(keywordLower))) {
                        log.info("已过滤：岗位【{}】名称和描述均不包含关键字【{}】", jobName, keyword);
                        page.close();
                        continue;
                    }
                }

                jobs.add(job);
            } catch (Exception e) {
                log.debug("处理岗位卡片失败: {}", e.getMessage());
            }
        }

        // 处理每个职位详情
        int result = processJobList(jobs, keyword);
        if (result < 0) {
            return result;
        }

        return resultList.size();
    }

    /**
     * 处理推荐职位列表
     *
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processRecommendJobs() {
        for (Job job : recommendJobs) {
            // 使用Playwright在新标签页中打开链接
            Page jobPage = PlaywrightUtil.getPageObject().context().newPage();
            try {
                jobPage.navigate(homeUrl + job.getHref());
            } catch (Exception e) {
                log.error("打开职位详情页超时或失败，已跳过该职位: {}，异常: {}", job.getHref(), e.getMessage());
                jobPage.close();
                continue;
            }

            try {
                // 等待聊天按钮出现
                Locator chatButton = jobPage.locator(BossElementLocators.CHAT_BUTTON);
                if (!chatButton.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                    Locator errorElement = jobPage.locator(BossElementLocators.ERROR_CONTENT);
                    if (errorElement.isVisible() && errorElement.textContent().contains("异常访问")) {
                        jobPage.close();
                        return -2;
                    }
                }
            } catch (Exception e) {
                if (config.getDebugger()) {
                    e.printStackTrace();
                }
                log.error("无法加载岗位详情页: {}", e.getMessage());
                jobPage.close();
                continue;
            }

            // 过滤不活跃HR
            if (isDeadHR(jobPage)) {
                jobPage.close();
                log.info("该HR已过滤");
                PlaywrightUtil.sleep(1);
                continue;
            }

            try {
                // 获取职位描述标签
                Locator tagElements = jobPage.locator(JOB_KEYWORD_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                job.setJobKeywordTag(tag.toString());
            } catch (Exception e) {
                log.info("获取职位描述标签失败:{}", e.getMessage());
            }

            // 推荐岗位 职位名称/职位描述关键字其中一个必须匹配一个岗位关键词才投递
            List<String> keywords = config.getKeywords();
            String jobName = job.getJobName();
            String jobKeywordTag = job.getJobKeywordTag();

            // 检查jobKeywordTag或jobName是否包含关键字列表中的任意一个
            boolean containsKeyword = false;
            if (keywords != null && !keywords.isEmpty()) {
                // 检查jobName
                if (isValidString(jobName)) {
                    for (String keywordItem : keywords) {
                        if (jobName.contains(keywordItem)) {
                            containsKeyword = true;
                            break;
                        }
                    }
                }

                // 如果jobName不包含关键字，检查jobKeywordTag
                if (!containsKeyword && isValidString(jobKeywordTag)) {
                    for (String keywordItem : keywords) {
                        if (jobKeywordTag.contains(keywordItem)) {
                            containsKeyword = true;
                            break;
                        }
                    }
                }
            }

            // 如果不包含任何关键字，则跳过此职位
            if (!keywords.isEmpty() && !containsKeyword) {
                log.info("已过滤:【{}】公司【{}】岗位不包含任何关键字", job.getCompanyName(), jobName);
                jobPage.close();
                continue;
            }

            // 处理职位详情页
            int result = processJobDetail(jobPage, job, null);
            if (result < 0) {
                return result;
            }

            if (config.getDebugger()) {
                break;
            }
        }
        return 0;
    }

    /**
     * 处理职位列表
     *
     * @param jobs    职位列表
     * @param keyword 搜索关键词
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processJobList(List<Job> jobs, String keyword) {
        for (Job job : jobs) {
            // 使用Playwright在新标签页中打开链接
            Page jobPage = PlaywrightUtil.getPageObject().context().newPage();
            try {
                jobPage.navigate(homeUrl + job.getHref());
            } catch (Exception e) {
                log.error("打开职位详情页超时或失败，已跳过该职位: {}，异常: {}", job.getHref(), e.getMessage());
                jobPage.close();
                continue;
            }

            try {
                // 等待聊天按钮出现
                Locator chatButton = jobPage.locator(BossElementLocators.CHAT_BUTTON);
                if (!chatButton.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                    Locator errorElement = jobPage.locator(BossElementLocators.ERROR_CONTENT);
                    if (errorElement.isVisible() && errorElement.textContent().contains("异常访问")) {
                        jobPage.close();
                        return -2;
                    }
                }
            } catch (Exception e) {
                if (config.getDebugger()) {
                    e.printStackTrace();
                }
                log.error("无法加载岗位详情页: {}", e.getMessage());
                jobPage.close();
                continue;
            }

            // 过滤不活跃HR
            if (isDeadHR(jobPage)) {
                jobPage.close();
                log.info("该HR已过滤");
                PlaywrightUtil.sleep(1);
                continue;
            }

            try {
                // 获取职位描述标签
                Locator tagElements = jobPage.locator(JOB_KEYWORD_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                job.setJobKeywordTag(tag.toString());
            } catch (Exception e) {
                log.info("获取职位描述标签失败:{}", e.getMessage());
            }

            if (config.getKeyFilter()) {
                String jobNameLower = job.getJobName().toLowerCase();
                String jobKeywordTagLower = job.getJobKeywordTag() != null ? job.getJobKeywordTag().toLowerCase() : "";
                String keywordLower = keyword.toLowerCase();
                if (!(jobNameLower.contains(keywordLower) || jobKeywordTagLower.contains(keywordLower))) {
                    log.info("已过滤：岗位【{}】名称和描述均不包含关键字【{}】", job.getJobName(), keyword);
                    jobPage.close();
                    continue;
                }
            }

            // 处理职位详情页
            int result = processJobDetail(jobPage, job, keyword);
            if (result < 0) {
                return result;
            }

            if (config.getDebugger()) {
                break;
            }
        }
        return 0;
    }

    /**
     * 处理单个职位详情页 - 共同处理流程
     *
     * @param jobPage 职位详情页面
     * @param job     职位信息
     * @param keyword 搜索关键词（可能为空）
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processJobDetail(com.microsoft.playwright.Page jobPage, Job job, String keyword) {
        // 获取薪资
        try {
            Locator salaryElement = jobPage.locator(BossElementLocators.JOB_DETAIL_SALARY);
            if (salaryElement.isVisible()) {
                String salaryText = salaryElement.textContent();
                job.setSalary(salaryText);
                if (isSalaryNotExpected(salaryText)) {
                    // 过滤薪资
                    log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", job.getCompanyName(), job.getJobName(), salaryText);
                    jobPage.close();
                    return 0;
                }
            }
        } catch (Exception ignore) {
            log.info("获取岗位薪资失败:{}", ignore.getMessage());
        }

        // 获取招聘人员信息
        try {
            Locator recruiterElement = jobPage.locator(BossElementLocators.RECRUITER_INFO);
            if (recruiterElement.isVisible()) {
                String recruiterName = recruiterElement.textContent();
                job.setRecruiter(recruiterName.replaceAll("\\r|\\n", ""));
                if (blackRecruiters.stream().anyMatch(recruiterName::contains)) {
                    // 排除黑名单招聘人员
                    jobPage.close();
                    return 0;
                }
            }
        } catch (Exception ignore) {
            log.info("获取招聘人员信息失败:{}", ignore.getMessage());
        }

        // 模拟用户浏览行为
        jobPage.evaluate("window.scrollBy(0, 300)");
        PlaywrightUtil.sleep(1);
        jobPage.evaluate("window.scrollBy(0, 300)");
        PlaywrightUtil.sleep(1);
        jobPage.evaluate("window.scrollTo(0, 0)");
        PlaywrightUtil.sleep(1);

        Locator chatBtn = jobPage.locator(BossElementLocators.CHAT_BUTTON);
        chatBtn = chatBtn.nth(0);
        boolean debug = config.getDebugger();

        // 每次点击沟通前都休眠5秒 减少调用频率
        PlaywrightUtil.sleep(2);

        if (chatBtn.isVisible() && "立即沟通".equals(chatBtn.textContent().replaceAll("\\s+", ""))) {
            // 优化：缩短等待时间为2秒
            PlaywrightUtil.sleep(2);

            AiFilter filterResult = null;
            if (config.getEnableAI() && keyword != null) {
                // AI检测岗位是否匹配
                Locator jdElements = jobPage.locator(BossElementLocators.JOB_DESCRIPTION);
                if (jdElements.count() > 0) {
                    // 只取第一个岗位描述
                    String jd = jdElements.nth(0).textContent();
                    filterResult = checkJob(keyword, job.getJobName(), jd);
                }
            }

            chatBtn.click();

            if (isLimit()) {
                PlaywrightUtil.sleep(1);
                jobPage.close();
                return -1;
            }

            // 沟通对话框
            try {
                // 不知道是什么情况下可能出现的弹框，执行关闭处理
                try {
                    Locator dialogTitle = jobPage.locator(BossElementLocators.DIALOG_TITLE);
                    if (dialogTitle.nth(0).isVisible()) {
                        Locator closeBtn = jobPage.locator(BossElementLocators.DIALOG_CLOSE);
                        if (closeBtn.nth(0).isVisible()) {
                            closeBtn.nth(0).click();
                            chatBtn.nth(0).click();
                        }
                    }
                } catch (Exception ignore) {
                }

                // 对话文本录入框，增加多次重试和多选择器兜底
                Locator input = null;
                int retry = 0;
                while (retry < 5) {
                    try {
                        input = jobPage.locator(BossElementLocators.CHAT_INPUT);
                        if (input.count() > 0 && input.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
                            input = input.nth(0);
                            break;
                        }
                        // 兜底尝试常见输入框
                        input = jobPage.locator("input, textarea, .chat-input");
                        if (input.count() > 0 && input.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
                            input = input.nth(0);
                            break;
                        }
                    } catch (Exception ignore) {}
                    PlaywrightUtil.sleep(2); // 等待2秒再试
                    retry++;
                }
                if (input != null && input.isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
                    input.click();
                    Locator dialogElement = jobPage.locator(BossElementLocators.DIALOG_CONTAINER);
                    dialogElement = dialogElement.nth(0);
                    if (dialogElement.isVisible() && "不匹配".equals(dialogElement.textContent())) {
                        jobPage.close();
                        return 0;
                    }

                    String sentMsg = filterResult != null && filterResult.getResult() && isValidString(filterResult.getMessage()) ? filterResult.getMessage() : config.getSayHi().replaceAll("\r|\n", "");
                    input.fill(sentMsg);

                    // 定位发送按钮，增加多次重试和多选择器兜底
                    Locator sendBtn = null;
                    int retrySend = 0;
                    String[] selectors = {
                        BossElementLocators.SEND_BUTTON,
                        "button[type='submit']",
                        ".btn-send",
                        ".chat-btn-send",
                        "button:has-text('发送')",
                        "button:has-text('回车')",
                        "button"
                    };
                    while (retrySend < 5) {
                        boolean found = false;
                        for (String selector : selectors) {
                            try {
                                Locator btns = jobPage.locator(selector);
                                int count = btns.count();
                                for (int i = 0; i < count; i++) {
                                    Locator btn = btns.nth(i);
                                    if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                                        String text = btn.textContent();
                                        if (text != null && (text.contains("发送") || text.contains("回车") || text.contains("Send"))) {
                                            sendBtn = btn;
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                                if (found) break;
                            } catch (Exception ignore) {}
                        }
                        if (sendBtn != null && sendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                            break;
                        }
                        PlaywrightUtil.sleep(2);
                        retrySend++;
                    }
                    if (sendBtn != null && sendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                        if (!debug) {
                            sendBtn.click();
                        }
                        // 发送简历图片
                        Boolean imgResume = false;
                        if (config.getSendImgResume()) {
                            try {
                                // 从类路径加载 resume.jpg
                                URL resourceUrl = Boss.class.getResource("/resume.jpg");
                                if (resourceUrl != null) {
                                    File imageFile = new File(resourceUrl.toURI());
                                    // 使用Playwright上传文件
                                    Locator fileInput = jobPage.locator(BossElementLocators.IMAGE_UPLOAD);
                                    if (fileInput.isVisible()) {
                                        fileInput.setInputFiles(new java.nio.file.Path[]{java.nio.file.Paths.get(imageFile.getPath())});
                                        // 等待发送按钮并点击
                                        Locator imageSendBtn = jobPage.locator(".image-uploader-btn");
                                        if (imageSendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                                            // 发送简历图片
                                            if (!debug) {
                                                imageSendBtn.click();
                                                imgResume = true;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("获取简历图片路径失败: {}", e.getMessage());
                            }
                        }
                        PlaywrightUtil.sleep(2);
                        String recruiter = job.getRecruiter();
                        String company = job.getCompanyName();
                        String position = job.getJobName() + " " + job.getSalary() + " " + job.getJobArea();
                        log.info("正在投递【{}】公司，【{}】职位，招聘官:【{}】{}", company, position, recruiter,
                                imgResume ? "发送图片简历成功！" : "");
                        resultList.add(job);
                        if (!debug) {
                            jobPage.close();
                        }
                    } else {
                        log.info("没有定位到对话框回车按钮");
                    }
                } else {
                    log.info("没有定位到对话框文本录入框");
                }
            } catch (Exception e) {
                log.error("发送消息失败:{}", e.getMessage(), e);
            }
        }

        if (!debug) {
            jobPage.close();
        }

        return 0;
    }

    public static boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    public static Boolean sendResume(String company) {
        // 如果 config.getSendImgResume() 为 true，再去找图片
        if (!config.getSendImgResume()) {
            return false;
        }

        try {
            // 从类路径加载 resume.jpg
            URL resourceUrl = Boss.class.getResource("/resume.jpg");
            if (resourceUrl == null) {
                log.error("在类路径下未找到 resume.jpg 文件！");
                return false;
            }

            // 将 URL 转为 File 对象
            File imageFile = new File(resourceUrl.toURI());
            log.info("简历图片路径：{}", imageFile.getAbsolutePath());

            if (!imageFile.exists()) {
                log.error("简历图片不存在！: {}", imageFile.getAbsolutePath());
                return false;
            }

            // 使用 XPath 定位 <input type="file"> 元素
            WebElement fileInput = CHROME_DRIVER
                    .findElement(By.xpath("//div[@aria-label='发送图片']//input[@type='file']"));

            // 上传图片
            fileInput.sendKeys(imageFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("发送简历图片时出错：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查岗位薪资是否符合预期
     *
     * @return boolean
     * true 不符合预期
     * false 符合预期
     * 期望的最低薪资如果比岗位最高薪资还小，则不符合（薪资给的太少）
     * 期望的最高薪资如果比岗位最低薪资还小，则不符合(要求太高满足不了)
     */
    private static boolean isSalaryNotExpected(String salary) {
        try {
            // 1. 如果没有期望薪资范围，直接返回 false，表示"薪资并非不符合预期"
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (!hasExpectedSalary(expectedSalary)) {
                return false;
            }

            // 2. 清理薪资文本（比如去掉 "·15薪"）
            salary = removeYearBonusText(salary);

            // 3. 如果薪资格式不符合预期（如缺少 "K" / "k"），直接返回 true，表示"薪资不符合预期"
            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            // 4. 进一步清理薪资文本，比如去除 "K"、"k"、"·" 等
            salary = cleanSalaryText(salary);

            // 5. 判断是 "月薪" 还是 "日薪"
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary); // 如果是按天，则去除 "元/天"

            // 6. 解析薪资范围并检查是否超出预期
            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);

        } catch (Exception e) {
            log.error("岗位薪资获取异常！薪资文本【{}】,异常信息【{}】", salary, e.getMessage(), e);
            // 出错时，您可根据业务需求决定返回 true 或 false
            // 这里假设出错时无法判断，视为不满足预期 => 返回 true
            return true;
        }
    }

    /**
     * 是否存在有效的期望薪资范围
     */
    private static boolean hasExpectedSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty();
    }

    /**
     * 去掉年终奖信息，如 "·15薪"、"·13薪"。
     */
    private static String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            // 使用正则去除 "·任意数字薪"
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断是否是按天计薪，如发现 "元/天" 则认为是日薪
     */
    private static String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "mouth";
    }

    /**
     * 如果是日薪，则去除 "元/天"
     */
    private static String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    private static Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private static Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    private static boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k") || salaryText.contains("元/天");
    }

    private static String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private static boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary,
                                              String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }
        if (Objects.equals("day", jobType)) {
            // 期望薪资转为平均每日的工资
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
        }
        // 如果职位薪资下限低于期望的最低薪资，返回不符合
        if (jobSalary[1] < miniSalary) {
            return true;
        }
        // 如果职位薪资上限高于期望的最高薪资，返回不符合
        return maxSalary != null && jobSalary[0] > maxSalary;
    }


    private static boolean isDeadHR(Page page) {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            // 尝试获取 HR 的活跃时间
            Locator activeTimeElement = page.locator(HR_ACTIVE_TIME);
            activeTimeElement = activeTimeElement.nth(0);
            String outerHtml = activeTimeElement.first().evaluate("el => el.outerHTML").toString();


            if (activeTimeElement.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
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
            Locator element = page.locator(RECRUITER_INFO);
            element = element.nth(0);
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
            return Arrays.stream(salaryText.split("-")).map(s -> s.replaceAll("[^0-9]", "")) // 去除非数字字符
                    .map(Integer::parseInt) // 转换为Integer
                    .toArray(Integer[]::new); // 转换为Integer数组
        } catch (Exception e) {
            log.error("薪资解析异常！{}", e.getMessage(), e);
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
                return text.contains("已达上限");
            }
            return false;
        } catch (Exception e) {
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

        Page page = PlaywrightUtil.getPageObject(PlaywrightUtil.DeviceType.MOBILE);

        for (String keyword : h5Config.getKeywords()) {
            String searchUrl = getH5SearchUrl(cityCode, keyword);
            log.info("查询url:{}", searchUrl);

            try {
                log.info("开始投递，页面url：{}", searchUrl);
                // 使用PlaywrightUtil获取移动设备页面并导航
                page.navigate(searchUrl);

                // 点击立即沟通，建立chat窗口
                if (isH5JobsPresent(page)) {
                    int previousCount = 0;
                    int retry = 0;
                    // 向下滚动到底部
                    while (true) {
                        // 当前页面中 class="item" 的 li 元素数量
                        int currentCount = (int) page.evaluate("document.querySelectorAll('li.item').length");

                        // 滚动到底部
                        // 滚动到比页面高度更大的值，确保触发加载
                        page.evaluate("window.scrollTo(0, document.documentElement.scrollHeight + 100)");
                        page.waitForTimeout(10000); // 等待数据加载

                        // 检查数量是否变化
                        if (currentCount == previousCount) {
                            retry++;
                            log.info("第{}次下拉重试", retry);
                            if (retry >= 2) {
                                log.info("尝试2次下拉后无新增岗位，退出");
                                break; // 连续两次未加载新数据，认为加载完毕
                            }
                        } else {
                            retry = 0; // 重置尝试次数
                        }

                        previousCount = currentCount;

                        if (config.getDebugger()) {
                            break;
                        }
                    }
                    log.info("已加载全部岗位，总数量: " + previousCount);
                }

                // chat页面进行消息沟通
                h5ResumeSubmission(keyword);
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
        String salary = h5Config.getSalary();
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

}
