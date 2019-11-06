package com.learning.springmvc.demo.service.impl;

import com.learning.springmvc.annotation.DevService;
import com.learning.springmvc.demo.service.TestService;

/**
 * @author: dengxin.chen
 * @date: 2019-11-06 16:10
 * @description:
 */
@DevService
public class TestServiceImpl implements TestService {

    @Override
    public String testMethod() {
        return "testMethod invoke";
    }
}
