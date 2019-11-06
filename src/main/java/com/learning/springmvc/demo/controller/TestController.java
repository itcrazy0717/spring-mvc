package com.learning.springmvc.demo.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.learning.springmvc.annotation.DevAutowired;
import com.learning.springmvc.annotation.DevController;
import com.learning.springmvc.annotation.DevRequestMapping;
import com.learning.springmvc.annotation.DevRequestParam;
import com.learning.springmvc.demo.service.TestService;

/**
 * @author: dengxin.chen
 * @date: 2019-11-06 16:11
 * @description:
 */
@DevController
@DevRequestMapping("/test")
public class TestController {

    @DevAutowired
    private TestService testService;

    @DevRequestMapping("/add")
    public void add(HttpServletResponse resp, @DevRequestParam("name") String name) {
        String result = testService.testMethod();
        try {
            resp.getWriter().write("testMethod invoke");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(result);
    }

    @DevRequestMapping("/delete")
    public void delete(@DevRequestParam("name") String name) {
        String result = testService.testMethod();
        System.out.println("delete method invoke");
    }
}
