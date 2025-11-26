package org.nekotori.openai;

public class OpenAIClientExample {
    public static void main(String[] args) {
        // 创建客户端
        OpenAIClient client = new OpenAIClient(
            "test",  // 替换为您的API密钥
            "http://localhost:5102/v1"  // 可根据镜像站修改
        );
        
        // 输入图片路径和提示文本
//        String imagePath = "xxxxxx";  // 替换为您的图片路径
        String userText = "生成一张香蕉的图片";  // 替换为您的提示文本
        
        // 发起流式请求
        client.streamChatCompletion(
            "gemini-2.5-pro",  // 选择模型
            "You are a helpful assistant.",  // 系统消息
            userText,  // 用户文本
            null,  // 图片路径
            new OpenAIClient.StreamResponseHandler() {
                @Override
                public void onContent(String content) {
                    // 实时输出每个内容块
                    System.out.print(content);
                }
                
                @Override
                public void onComplete(String fullReply) {
                    // 输出完整回复
                    System.out.println("\n\n完整回复: " + fullReply);
                }
                
                @Override
                public void onError(Throwable error) {
                    // 处理错误
                    error.printStackTrace();
                }
            }
        );
        
        // 等待异步请求完成（在实际应用中可能需要更复杂的同步机制）
        try {
            Thread.sleep(30000);  // 等待30秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}