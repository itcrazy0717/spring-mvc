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
    public String add(@DevRequestParam("name") String name) {
        String result = testService.testMethod();
        System.out.println(result);
        return result;
    }

    @DevRequestMapping("/delete")
    public void delete(@DevRequestParam("name") String name) {
        String result = testService.testMethod();
        System.out.println("delete method invoke");
    }

    @DevRequestMapping("/sub")
    public void sub(HttpServletResponse response, @DevRequestParam("a") Integer a, @DevRequestParam("b") Integer b) {
        try {
            System.out.println("a=" + a + " b=" + b);
            response.getWriter().write(a + "-" + b + "=" + (a - b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
