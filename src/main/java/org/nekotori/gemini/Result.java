package org.nekotori.gemini;

public class Result {
    private String url;
    private String content;

    // 无参构造器
    public Result() {
    }

    // 全参构造器
    public Result(String url, String content) {
        this.url = url;
        this.content = content;
    }

    // getter 和 setter 方法
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // toString 方法
    @Override
    public String toString() {
        return "Result{" +
                "url='" + url + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}