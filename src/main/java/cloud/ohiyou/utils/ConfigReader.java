package cloud.ohiyou.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

/**
 * @Description: 读取配置
 * @Author: iniwym
 * @Date: 2024-12-17
 */
public class ConfigReader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigReader.class);
    private static final Properties propertyConfig = new Properties();
    private static final JSONObject jsonConfig = new JSONObject();
    private static final String ENV_SOURCE = "ENV";
    private static final String FILE_SOURCE = "FILE";

    // 静态代码块，初始化配置
    static {
        try {
            // 1. 读取 Property 文件
            loadPropertyConfig();
            // 2. 从 Property 文件中读取 env 变量
            String env = getEnvFromProperties();
            // 3. 根据 env 变量加载对应的 JSON 数据
            loadJsonConfig(env);
        } catch (Exception e) {
            logger.error("Failed to initialize configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize configuration: " + e.getMessage());
        }
    }

    /**
     * 从 paramMapping.properties 文件中加载固定参数
     */
    private static void loadPropertyConfig() {
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("paramMapping.properties")) {
            if (input == null) {
                throw new IOException("Properties file 'paramMapping.properties' not found.");
            }
            propertyConfig.load(input);
            logger.info("Loaded properties from 'paramMapping.properties'.");
        } catch (IOException e) {
            logger.error("Failed to load properties: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load properties: " + e.getMessage());
        }
    }

    /**
     * 从 Property 文件中读取 env 变量，使用 getPropertyKey 方法实现
     *
     * @return env 变量的值
     */
    private static String getEnvFromProperties() {
        String env = getPropertyKey("env");
        if (env == null || env.trim().isEmpty()) {
            throw new RuntimeException("Environment parameter 'env' not found in paramMapping.properties.");
        }
        logger.info("Environment parameter 'env' set to: {}", env);
        return env;
    }

    /**
     * 根据 env 参数加载对应的 JSON 数据
     *
     * @param env 环境变量或文件来源标识
     */
    private static void loadJsonConfig(String env) {
        try {
            if (ENV_SOURCE.equalsIgnoreCase(env)) {
                // 从环境变量中获取 JSON 配置
                String userJson = System.getenv("USER");
                if (userJson != null && !userJson.trim().isEmpty()) {
                    jsonConfig.putAll(JSON.parseObject(userJson));
                    logger.info("Loaded json from " + ENV_SOURCE);
                } else {
                    throw new RuntimeException("Environment variable 'USER' not found or empty.");
                }
            } else if (FILE_SOURCE.equalsIgnoreCase(env)) {
                // 从文件中读取默认配置
                try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("user_info.json")) {
                    if (input == null) {
                        throw new IOException("Configuration file 'user_info.json' not found.");
                    }
                    Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A");
                    if (scanner.hasNext()) {
                        jsonConfig.putAll(JSON.parseObject(scanner.next()));
                        logger.info("Loaded json from " + FILE_SOURCE);
                    } else {
                        throw new IOException("Configuration file 'user_info.json' is empty.");
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid environment parameter: " + env);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize JSON configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize JSON configuration: " + e.getMessage());
        }
    }

    /**
     * 从 Properties 中获取键值
     *
     * @param key 键名
     * @return 键对应的值
     */
    public static String getPropertyKey(String key) {
        return propertyConfig.getProperty(key);
    }

    /**
     * 从 JSONObject 中获取键值，支持嵌套路径
     *
     * @param key 键名，支持嵌套路径
     * @return 键对应的值
     */
    public static String getJsonKey(String key) {
        String[] keys = key.split("\\.");
        JSONObject current = jsonConfig;
        for (int i = 0; i < keys.length - 1; i++) {
            current = current.getJSONObject(keys[i]);
            if (current == null) {
                return null;
            }
        }
        return current.getString(keys[keys.length - 1]);
    }

    /**
     * 从 JSONObject 中获取 JSON 数组
     *
     * @param key 键名
     * @return JSONArray 对应的值
     */
    public static JSONArray getJsonArray(String key) {
        String[] keys = key.split("\\.");
        JSONObject current = jsonConfig;
        for (int i = 0; i < keys.length - 1; i++) {
            current = current.getJSONObject(keys[i]);
            if (current == null) {
                return null;
            }
        }
        return current.getJSONArray(keys[keys.length - 1]);
    }
}