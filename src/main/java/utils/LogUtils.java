package utils;

import org.slf4j.MDC;

/**
 * 简化的日志工具类
 * 只保留基本的MDC上下文管理功能，用于结构化日志记录
 */
public class LogUtils {
    
    /**
     * MDC上下文管理
     */
    public static class Context {
        
        /**
         * 设置平台上下文
         */
        public static void setPlatform(String platform) {
            MDC.put("platform", platform);
        }
        
        /**
         * 设置操作上下文
         */
        public static void setAction(String action) {
            MDC.put("action", action);
        }
        
        /**
         * 设置岗位ID
         */
        public static void setJobId(String jobId) {
            MDC.put("jobId", jobId);
        }
        
        /**
         * 设置公司名
         */
        public static void setCompany(String company) {
            MDC.put("company", company);
        }
        
        /**
         * 设置岗位名
         */
        public static void setPosition(String position) {
            MDC.put("position", position);
        }
        
        /**
         * 清除所有上下文
         */
        public static void clear() {
            MDC.clear();
        }
        
        /**
         * 清除特定键的上下文
         */
        public static void remove(String key) {
            MDC.remove(key);
        }
    }
} 