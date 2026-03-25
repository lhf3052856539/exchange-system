package com.mnnu.vo;
/**
 统一前后端数据交互对象
 */
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer code;

    private String message;

    private T data;

    private Long timestamp;

    public static <T> JsonVO<T> success(T data) {
        return create(data, ResultStatus.SUCCESS);
    }

    public static <T> JsonVO<T> success() {
        return success(null);
    }

    public static <T> JsonVO<T> error(ResultStatus status) {
        return create(null, status);
    }

    public static <T> JsonVO<T> error(ResultStatus status, T data) {
        return create(data, status);
    }

    public static <T> JsonVO<T> error(int code, String message) {
        return create(null, code, message);
    }

    public static <T> JsonVO<T> error(String message) {
        return error(500, message);
    }

    public static <T> JsonVO<T> create(T data, ResultStatus resultStatus) {
        JsonVO<T> result = new JsonVO<>();
        result.setCode(resultStatus.getCode());
        result.setMessage(resultStatus.getMessage());
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> JsonVO<T> create(T data, int code, String message) {
        JsonVO<T> result = new JsonVO<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
}
