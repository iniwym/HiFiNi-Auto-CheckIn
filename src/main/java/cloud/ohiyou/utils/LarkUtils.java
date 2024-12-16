package cloud.ohiyou.utils;

import okhttp3.*;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;

/**
 * 飞书机器人推送工具类
 */
public class LarkUtils {

    public static void larkBotMessage(String larkKey, String messageTitle, String messageBody) {
        // 构建完整的webhook URL
        String webhookUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/" + larkKey;

        // 使用 fastjson 构建 JSON 对象
        JSONObject content = new JSONObject();
        content.put("text", messageTitle + "\n" + messageBody);

        JSONObject json = new JSONObject();
        json.put("msg_type", "text");
        json.put("content", content);

        // 打印JSON请求体以便调试
        // System.out.println("Sending JSON: " + json.toJSONString());

        // 创建RequestBody对象
        RequestBody body = RequestBody.create(json.toJSONString(), MediaType.get("application/json; charset=utf-8"));

        // 使用Request.Builder()构建请求
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build();

        // 发送请求并处理响应
        OkHttpClient client = new OkHttpClient();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("消息发送成功！");
                // 打印响应体以便调试
                // System.out.println("Response Body: " + response.body().string());
            } else {
                System.err.println("消息发送失败，HTTP响应码: " + response.code());
                // 打印响应体以便调试
                // System.err.println("Response Body: " + response.body().string());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 测试方法
    public static void main(String[] args) {
        String larkKey = ""; // 替换为您的飞书机器人Webhook中的key
        String title = "通知标题";
        String body = "这里是消息正文内容。";

        larkBotMessage(larkKey, title, body);
    }
}