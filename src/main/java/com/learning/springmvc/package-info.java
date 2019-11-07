/**
 * @author: dengxin.chen
 * @date: 2019-11-05 22:26
 * @description:
 */
package com.learning.springmvc;

/**
 * spring mvc总结
 * spring mvc主要有三个阶段：
 * 1.配置阶段
 * 配置web.xml-->DispatcherServlet
 * 设定init-param-->contextConfigLocation
 * 设定url-pattern-->/*
 * 配置Annotation-->@Controller @Service @Autowired @RequestMapping等注解，自定义注解
 * 2.初始化阶段
 * 调用init()方法-->因为DispatcherServlet继承了HttpServlet，所以会先从init()方法加载配置文件
 * IOC容器初始化-->用Map<String,Object>来存储注册式单例
 * 扫描相关类--> 根据配置文件中的包路径，扫描相关类，存储在一个集合中
 * 创建实例并保存至IOC容器-->通过反射机制将类实例化进IOC容器中，这里根据注解进行反射实例化
 * 进行DI操作-->扫描IOC容器中的实例，给没有赋值的属性自动赋值，比如加了@Autowired注解的属性
 * 根据类型或者具体名称进行赋值
 * 初始化HandlerMapping-->将URL和Method进行映射
 * 这里就是对MVC进行初始化
 * 3.运行阶段
 * 调用doPost()或doGet()方法-->Web容器调用doPost/doGet方法，获得request/response对象
 * 匹配HandlerMapping-->从request对象中获取用户输入的url，然后匹配对应的Method
 * 反射调用method.invoke()-->利用反射调用方法并返回结果
 * 最后将结果进行输出-->response.getWrite().write()
 */