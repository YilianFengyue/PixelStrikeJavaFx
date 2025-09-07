// main/java/org/csu/pixelstrikejavafx/net/CommonResponse.java
package org.csu.pixelstrikejavafx.net;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.lang.reflect.Type;

// 这个类必须和后端返回的JSON结构完全对应
@Getter
public class CommonResponse<T> {

    private int status;
    private String message;
    private T data;

    // 一个辅助方法，用于让GSON正确解析泛型
    public static <T> Type getTypeToken(Class<T> innerClass) {
        return TypeToken.getParameterized(CommonResponse.class, innerClass).getType();
    }
}