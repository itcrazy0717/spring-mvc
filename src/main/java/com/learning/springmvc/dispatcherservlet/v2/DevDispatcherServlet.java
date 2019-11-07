package com.learning.springmvc.dispatcherservlet.v2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.learning.springmvc.annotation.DevAutowired;
import com.learning.springmvc.annotation.DevController;
import com.learning.springmvc.annotation.DevRequestMapping;
import com.learning.springmvc.annotation.DevRequestParam;
import com.learning.springmvc.annotation.DevService;
import com.learning.springmvc.util.CommonUtils;

/**
 * @author: dengxin.chen
 * @date: 2019-11-05 19:00
 * @description: 手写dispatcherServlet
 */
public class DevDispatcherServlet extends HttpServlet {

    private static final String LOCATION = "contextConfigLocation";

    /**
     * 扫描基础包，类似在spring-mvc.xml中配置的具体扫描哪些包
     */
    private static final String SCANNER_BASEPACKAGE = "scanPackage";

    /**
     * 保存配置文件中的键值对
     */
    private Properties properties = new Properties();

    /**
     * 保存扫描到的所有类名称
     */
    private List<String> classNames = Lists.newArrayList();

    /**
     * 存储所有实例化对象
     */
    private Map<String, Object> ioc = Maps.newHashMap();

    /**
     * handlerMapping集合
     * 存储url与handlerMapping的映射
     */
    private Map<String, HandlerMapping> handlerMappingMap = Maps.newHashMap();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:" + Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * 初始化，加载所需的资源
     *
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        // 1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        // 2.扫描所有相关类
        doScanner(properties.getProperty(SCANNER_BASEPACKAGE));

        // 3.初始化相关类，并存储到ioc容器中
        doInstance();

        // 4.依赖注入，这里就是注入Controller的service等对象
        doAutoWired();

        // 5.构造HandlerMapping
        initHandlerMapping();

        System.out.println("dev spring mvc init successful......");
    }

    /**
     * 加载配置文件
     *
     * @param initParameter
     */
    private void doLoadConfig(String initParameter) {
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(initParameter)) {
            // 读取配置文件到Properties对象中
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描相关类
     *
     * @param basepackage
     */
    private void doScanner(String basepackage) {
        // 将包路径转换成文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + basepackage.replaceAll("\\.", "/"));
        if (Objects.isNull(url)) {
            return;
        }
        File classFile = new File(url.getFile());
        for (File file : classFile.listFiles()) {
            // 如果是文件夹，则递归
            if (file.isDirectory()) {
                doScanner(basepackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith("class")) {
                    continue;
                }
                String className = (basepackage + "." + file.getName()).replace(".class", "");
                classNames.add(className);
            }

        }
    }

    /**
     * 初始化相关类，进行IOC存储
     * 将类的初始化交给容器，这就是控制反转的过程
     */
    private void doInstance() {

        if (CollectionUtils.isNotEmpty(classNames)) {
            try {
                for (String className : classNames) {
                    Class<?> clazz = Class.forName(className);
                    // 如果是Controller
                    if (clazz.isAnnotationPresent(DevController.class)) {
                        // 将第一个字母小写作为beanName
                        String beanName = CommonUtils.toLowerFirstCase(clazz.getSimpleName());
                        Object instance = clazz.newInstance();
                        ioc.put(beanName, instance);
                    } else if (clazz.isAnnotationPresent(DevService.class)) {
                        // 这里Service注解有不同情况，因为可以自定义Service的名字
                        DevService service = clazz.getAnnotation(DevService.class);
                        // 如果存在自定义Service名字
                        if (StringUtils.isNotEmpty(service.value())) {
                            ioc.put(service.value(), clazz.newInstance());
                            continue;
                        }
                        // 如果不存在自定义Service名称，则按照接口创建实例
                        Class<?>[] interfaces = clazz.getInterfaces();
                        for (Class<?> itemInterface : interfaces) {
                            if (ioc.containsKey(itemInterface.getName())) {
                                throw new RuntimeException("This bean already exists!");
                            }
                            ioc.put(itemInterface.getName(), clazz.newInstance());
                        }
                    } else {
                        continue;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 依赖注入过程
     */
    private void doAutoWired() {
        if (MapUtils.isNotEmpty(ioc)) {
            for (Map.Entry<String, Object> entry : ioc.entrySet()) {
                // 获取实例对象中的所有属性
                Field[] fields = entry.getValue().getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (!field.isAnnotationPresent(DevAutowired.class)) {
                        continue;
                    }
                    DevAutowired autowired = field.getAnnotation(DevAutowired.class);
                    String beanName = autowired.value().trim();
                    if (StringUtils.isAllBlank(beanName)) {
                        // 获取类型的名称
                        beanName = field.getType().getName();
                    }
                    field.setAccessible(true);
                    try {
                        // 为对象的属性设值 注入依赖
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        throw new IllegalStateException("自动注入属性异常");
                    }
                }
            }
        }
    }

    /**
     * 初始化HandlerMapping，保存url映射关系
     */
    private void initHandlerMapping() {
        if (MapUtils.isNotEmpty(ioc)) {
            for (Map.Entry<String, Object> entry : ioc.entrySet()) {
                Class<?> clazz = entry.getValue().getClass();
                if (!clazz.isAnnotationPresent(DevController.class)) {
                    continue;
                }
                String baseUrl = "";
                // 获取Controller上设置的url
                if (clazz.isAnnotationPresent(DevRequestMapping.class)) {
                    DevRequestMapping requestMapping = clazz.getAnnotation(DevRequestMapping.class);
                    baseUrl = requestMapping.value();
                }

                // 获取Method上的url配置
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (!method.isAnnotationPresent(DevRequestMapping.class)) {
                        continue;
                    }
                    // 映射url
                    DevRequestMapping requestMapping = method.getAnnotation(DevRequestMapping.class);
                    String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                    // 构建handlerMapping
                    if (handlerMappingMap.containsKey(url)) {
                        throw new IllegalArgumentException("已存在相同url请求");
                    }
                    handlerMappingMap.put(url, new HandlerMapping(url, entry.getValue(), method));
                    System.out.println("Mapping:" + url + " Method:" + method);
                }

            }
        }

    }

    /**
     * 任务分发，委派模式
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String uri = req.getRequestURI();
        String url = uri.replaceAll("/+", "/");

        HandlerMapping handlerMapping = filterHandlerMapping(url);
        if (Objects.isNull(handlerMapping)) {
            resp.getWriter().write("404 Not Found");
            return;
        }

        Method method = handlerMapping.method;

        // 获取参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 构建参数值数组
        Object[] paramValues = new Object[parameterTypes.length];

        Map<String, String[]> params = req.getParameterMap();

        for (Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Joiner.on(",").join(param.getValue());
            if (!handlerMapping.paramIndexMap.containsKey(param.getKey())) {
                continue;
            }
            Integer index = handlerMapping.paramIndexMap.get(param.getKey());
            paramValues[index] = convert(parameterTypes[index], value);
        }

        // 对两个特殊参数的处理
        Integer reqIndex = handlerMapping.paramIndexMap.get(HttpServletRequest.class.getName());
        if (Objects.nonNull(reqIndex)) {
            paramValues[reqIndex] = req;
        }
        Integer resIndex = handlerMapping.paramIndexMap.get(HttpServletResponse.class.getName());
        if (Objects.nonNull(resIndex)) {
            paramValues[resIndex] = resp;
        }
        // 通过反射执行方法 
        Object result = method.invoke(handlerMapping.controller, paramValues);
        if (Objects.nonNull(result)) {
            resp.getWriter().write(result.toString());
        }
    }

    /**
     * 参数类型转换 由于通过http协议传过来的数据都是字符串的形式，这里需要根据参数的具体类型
     * 进行转换，目前只支持String和Integer的转换，后续可根据策略模式进行添加
     *
     * @param type
     * @param value
     * @return
     */
    private Object convert(Class<?> type, String value) {

        if (type == Integer.class) {
            return Integer.valueOf(value);
        }
        return value;
    }

    /**
     * 过滤HandlerMapping
     *
     * @param url
     * @return
     */
    private HandlerMapping filterHandlerMapping(String url) {
        return handlerMappingMap.getOrDefault(url, null);
    }

    /**
     * 用HandlerMapping来记录Controller中RequestMapping和Method的对应关系
     */
    private class HandlerMapping {

        protected String url;

        /**
         * 保存Controller实例
         */
        protected Object controller;
        protected Method method;

        /**
         * 保存参数顺序
         */
        protected Map<String, Integer> paramIndexMap;

        public HandlerMapping(String url, Object controller, Method method) {
            this.url = url;
            this.controller = controller;
            this.method = method;

            paramIndexMap = Maps.newHashMap();

            buildParamIndex(paramIndexMap);

        }

        private void buildParamIndex(Map<String, Integer> paramIndexMap) {
            // 获取方法上的注解
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof DevRequestParam) {
                        String paramName = ((DevRequestParam) annotation).value();
                        if (StringUtils.isNotEmpty(paramName)) {
                            paramIndexMap.put(paramName, i);
                        }
                    }
                }
            }

            // 提取方法中的Request和Response
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMap.put(type.getName(), i);
                }
            }
        }
    }

}
