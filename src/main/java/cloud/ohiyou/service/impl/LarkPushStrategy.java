package cloud.ohiyou.service.impl;

import okhttp3.*;
import cloud.ohiyou.config.EnvConfig;
import cloud.ohiyou.service.IMessagePushStrategy;
import cloud.ohiyou.utils.ConfigReader;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;

/**
 * 飞书机器人推送工具类
 */
public class LarkPushStrategy implements IMessagePushStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LarkPushStrategy.class);

    private final OkHttpClient client;

    public LarkPushStrategy(OkHttpClient client) {
        this.client = client;
    }

    /**
     * 推送消息
     *
     * @param title   推送的标题
     * @param message 推送的消息（默认是markdown格式）
     */
    @Override
    public void pushMessage(String title, String message) {
        // 构建完整的webhook URL
        String webhookUrl = ConfigReader.getPropertyKey("api.url_lark") + EnvConfig.get().getLarkKey();

        // 使用 fastjson 构建 JSON 对象
        JSONObject content = new JSONObject();
        content.put("text", message);

        JSONObject json = new JSONObject();
        json.put("msg_type", "text");
        json.put("content", content);

        // 创建RequestBody对象
        RequestBody body = RequestBody.create(json.toJSONString(), MediaType.get("application/json; charset=utf-8"));

        // 使用Request.Builder()构建请求
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build();

        // 发送请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                logger.info("飞书机器人消息发送成功！");
            } else {
                logger.error("飞书机器人消息发送失败，HTTP响应码: " + response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}