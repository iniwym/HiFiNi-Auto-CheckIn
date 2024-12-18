package cloud.ohiyou;

import cloud.ohiyou.utils.ConfigReader;
import cloud.ohiyou.utils.DingTalkUtils;
import cloud.ohiyou.utils.LarkUtils;
import cloud.ohiyou.utils.TelegramUtils;
import cloud.ohiyou.utils.WeChatWorkUtils;
import cloud.ohiyou.vo.CookieSignResult;
import cloud.ohiyou.vo.SignResultVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * @Description: 主方法
 * @Author: iniwym
 * @Date: 2024-12-18
 */
public class IniMain {

    private static final Logger logger = LoggerFactory.getLogger(IniMain.class);

    /**
     * ↓↓↓↓↓↓↓↓↓↓ 正式 ↓↓↓↓↓↓↓↓↓↓
     */
    private static final String DINGTALK_WEBHOOK = System.getenv("DINGTALK_WEBHOOK"); // 钉钉机器人 access_token 的值
    private static final String WXWORK_WEBHOOK = System.getenv("WXWORK_WEBHOOK"); // 企业微信机器人 key 的值
    private static final String SERVER_CHAN_KEY = System.getenv("SERVER_CHAN"); // Service酱推送的key
    private static final String TG_CHAT_ID = System.getenv("TG_CHAT_ID"); // Telegram Chat ID
    private static final String TG_BOT_TOKEN = System.getenv("TG_BOT_TOKEN"); // Telegram Bot Token
    private static final String LARK_KEY = System.getenv("LARK_KEY"); // 飞书机器人
    /**
     * ↑↑↑↑↑↑↑↑↑↑ 正式 ↑↑↑↑↑↑↑↑↑↑
     */
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void main(String[] args) {
        List<CookieSignResult> results = Collections.synchronizedList(new ArrayList<>());

        JSONArray users = ConfigReader.getJsonArray("info");

        if (users == null) {
            throw new RuntimeException("未设置JsonInfo");
        }

        int size = users.size();
        logger.info("检测到 " + size + " 个用户");

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            JSONObject user = users.getJSONObject(i);


            final int index = i;
            Future<?> future = executor.submit(() -> {
                try {
                    processCookie(user, index, results);
                } catch (Exception e) {
                    logger.info("Error processing cookie at index " + index + ": " + e.getMessage());
                    // 添加消息失败的结果
                    results.add(new CookieSignResult(new SignResultVO(401, "签到失败,cookie失效"), 0));
                }
            });
            futures.add(future);
        }

        // 关闭线程池，使其不再接受新任务
        executor.shutdown();

        // 等待所有任务完成
        for (Future<?> future : futures) {
            try {
                // 等待并获取任务执行结果,这会阻塞，直到任务完成
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 重新中断当前线程
                // 处理中断异常，例如记录日志或者根据业务需求进行其他处理
                logger.info("当前线程在等待任务完成时被中断。");
            } catch (ExecutionException e) {
                // 获取实际导致任务执行失败的异常
                Throwable cause = e.getCause();
                logger.info("执行任务时出错：" + cause.getMessage());
            }
        }

        // 等待线程池完全终止
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();

        publishResults(results);
    }

    private static void processCookie(JSONObject user, int index, List<CookieSignResult> results) {
        long startTime = System.currentTimeMillis();

        String cookie = user.getString("cookie");
        String formattedCookie = formatCookie(cookie.trim(), index);
        if (formattedCookie != null) {
            SignResultVO signResultVO = sendSignInRequest(user, formattedCookie);
            long endTime = System.currentTimeMillis();
            results.add(new CookieSignResult(signResultVO, endTime - startTime));
        }
    }

    private static void publishResults(List<CookieSignResult> results) {
        StringBuilder messageBuilder = new StringBuilder();
        boolean allSuccess = true; // 假设所有签到都成功，直到发现失败的签到

        for (CookieSignResult result : results) {
            messageBuilder.append(result.getSignResult().getUserName()).append(": ")
                    .append("\n签到结果: ").append(result.getSignResult().getMessage())
                    .append("\n耗时: ").append(result.getDuration()).append("ms\n\n");
            // 检查每个签到结果，如果有失败的，则设置allSuccess为false
            if (!(result.getSignResult().getMessage().contains("成功签到")
                    || result.getSignResult().getMessage().contains("今天已经签过啦！"))) {
                allSuccess = false;
            }
        }

        String title = allSuccess ? "HiFiNi签到成功" : "HiFiNi签到失败"; // 根据所有签到结果决定标题

        logger.info("\nHiFiNi签到消息: \n" + title + "：\n" + messageBuilder.toString());
        // 推送
        WeChatWorkUtils.pushWechatServiceChan(SERVER_CHAN_KEY, title, messageBuilder.toString()); // 推送微信公众号Service酱
        WeChatWorkUtils.pushBotMessage(WXWORK_WEBHOOK, title, messageBuilder.toString(), "markdown"); // 推送企业微信机器人
        LarkUtils.larkBotMessage(LARK_KEY, title, messageBuilder.toString()); // 飞书机器人
        DingTalkUtils.pushBotMessage(DINGTALK_WEBHOOK, title, messageBuilder.toString(), "", "markdown"); // 推送钉钉机器人
        TelegramUtils.publishTelegramBot(TG_CHAT_ID, TG_BOT_TOKEN, "HiFiNi签到消息: \n" + title + "：\n" + messageBuilder.toString()); // push telegram bot
    }


    private static SignResultVO sendSignInRequest(JSONObject user, String cookie) {

        String userName = user.getString("name");
        String sign = user.getString("sign");

        RequestBody formBody = new FormBody.Builder()
                .add("sign", sign)
                .build();
        // 发送签到请求
        Request request = new Request.Builder()
                .url("https://www.hifini.com/sg_sign.htm")
                .post(formBody)
                .addHeader("Cookie", cookie)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String result = readResponse(response);
            SignResultVO signResultVO = stringToObject(result, SignResultVO.class);
            signResultVO.setUserName(userName);
            return signResultVO;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    private static String formatCookie(String cookie, int index) {
        String bbsSid = null;
        String bbsToken = null;

        // 分割cookie字符串
        String[] split = cookie.split(";");

        // 遍历分割后的字符串数组
        for (String s : split) {
            s = s.trim(); // 去除可能的前后空格
            // 检查当前字符串是否包含bbs_sid或bbs_token
            if (s.startsWith("bbs_sid=")) {
                bbsSid = s; // 存储bbs_sid
            } else if (s.startsWith("bbs_token=")) {
                bbsToken = s; // 存储bbs_token
            }
        }

        // 确保bbs_sid和bbs_token都不为空
        if (bbsSid != null && bbsToken != null) {
            logger.info("成功解析第 " + (index + 1) + " 个cookie");
            // 拼接bbs_sid和bbs_token并返回
            return bbsSid + ";" + bbsToken + ";";
        } else {
            logger.info("解析第 " + (index + 1) + " 个cookie失败");
            return null; // 或者根据需要抛出异常
        }
    }


    private static String readResponse(Response response) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
        StringBuilder result = new StringBuilder();
        String readLine;
        while ((readLine = reader.readLine()) != null) {
            result.append(readLine);
        }
        return result.toString();
    }


    private static <T> T stringToObject(String result, Class<T> clazz) {
        return JSON.parseObject(result, clazz);
    }
}
