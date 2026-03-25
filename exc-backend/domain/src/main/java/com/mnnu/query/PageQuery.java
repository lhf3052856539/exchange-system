package com.mnnu.query;
/**
 分页查询父类对象
 */

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Min;

@Getter
@Setter
@ToString
public class PageQuery {
    @Min(value = 1, message = "页码最小值为1")
    private Long pageIndex;
    @Min(value = 1, message = "条数最小值为1")
    private Long pageSize;
}
