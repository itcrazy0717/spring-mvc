package com.learning.springmvc.util;

import org.apache.commons.lang3.StringUtils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author: dengxin.chen
 * @date: 2019-11-06 15:29
 * @description:工具类
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonUtils {

    /**
     * 将字符串第一个字母小写
     *
     * @param input
     * @return
     */
    public static String toLowerFirstCase(String input) {
        if (StringUtils.isAllBlank(input)) {
            return StringUtils.EMPTY;
        }
        char[] arrays = input.toCharArray();
        arrays[0] += 32;
        return String.valueOf(arrays);
    }
}
