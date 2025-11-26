package org.nekotori.gemini;

public class GsrResponseData {
    private Integer code;
    private Data data;
    private String msg;

    // 无参构造器
    public GsrResponseData() {
    }

    // 全参构造器
    public GsrResponseData(Integer code, Data data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    // getter 和 setter 方法
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    // toString 方法
    @Override
    public String toString() {
        return "GsrResponseData{" +
                "code=" + code +
                ", data=" + data +
                ", msg='" + msg + '\'' +
                '}';
    }
}