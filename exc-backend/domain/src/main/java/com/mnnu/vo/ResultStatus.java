package com.mnnu.vo;

public enum ResultStatus {
    SUCCESS("操作成功", 200),

    BAD_REQUEST("请求参数错误", 400),
    UNAUTHORIZED("暂未登录或 TOKEN 已经过期", 401),
    FORBIDDEN("没有相关权限", 403),
    NOT_FOUND("资源不存在", 404),

    INTERNAL_ERROR("服务器内部错误", 500),
    SERVER_BUSY("服务器繁忙，请稍后重试", 503),

    FAIL("操作失败", 9999),
    PARAMS_INVALID("上传参数异常", 10001),
    CONTENT_TYPE_ERR("ContentType 错误", 10002),
    API_UN_IMPL("功能尚未实现", 10003),

    USER_NOT_FOUND("用户不存在", 20001),
    USER_ALREADY_REGISTERED("用户已注册", 20002),
    USER_BLACKLISTED("用户已被拉黑", 20003),
    INVALID_ADDRESS("无效的钱包地址格式", 20004),

    TRADE_NOT_FOUND("交易不存在", 30001),
    TRADE_AMOUNT_INVALID("交易额度无效", 30002),
    TRADE_AMOUNT_EXCEED("超过可交易额度", 30003),
    TRADE_STATUS_INVALID("交易状态无效", 30004),
    TRADE_EXPIRED("交易已过期", 30005),
    TRADE_VERIFY_FAILED("交易验证失败", 30006),
    TRADE_CREATE_FAILED("创建交易失败", 30007),

    DISPUTE_NOT_FOUND("争议不存在", 40001),
    DISPUTE_STATUS_INVALID("争议状态无效", 40002),

    INSUFFICIENT_BALANCE("余额不足", 50001),
    TRANSFER_FAILED("转账失败", 50002),
    REWARD_DISTRIBUTION_FAILED("奖励发放失败", 50003);

    private final String message;
    private final int code;

    ResultStatus(String message, int code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public int getCode() {
        return code;
    }
}
