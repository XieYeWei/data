package com.hermes.util;

import java.util.HashMap;
import java.util.Map;

public class R {

    private int code;
    private String msg;
    private Object data;

    public R() {}

    public R(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static R success(Object data) {
        return new R(0, "success", data);
    }

    public static R success() {
        return new R(0, "success", null);
    }

    public static R error(int code, String msg) {
        return new R(code, msg, null);
    }

    public static R error(String msg) {
        return new R(500, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("msg", msg);
        m.put("data", data);
        return m;
    }
}
