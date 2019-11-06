package com.learning.springmvc.dispatcherservlet.v1;

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
import java.util.Properties;

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
     * 存储url映射
     */
    private Map<String, Method> handlerMapping = Maps.newHashMap();

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
     * 任务分发，委派模式
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String uri = req.getRequestURI();
        String url = uri.replaceAll("/+", "/");
        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found");
            return;
        }

        Method method = handlerMapping.get(url);

        Object[] paramValues = buildParams(req, resp, method);

        // 通过反射获取当前对象的名称
        String beanName = CommonUtils.toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        // 通过反射执行方法 
        method.invoke(ioc.get(beanName), paramValues);
    }

    /**
     * 构建请求参数
     *
     * @param req
     * @param resp
     * @param method
     * @return
     */
    private Object[] buildParams(HttpServletRequest req, HttpServletResponse resp, Method method) {
        // 获取参数键值集合
        Map<String, String[]> params = req.getParameterMap();
        // 获取方法上的参数集合
        Class<?>[] parameterTypes = method.getParameterTypes();
        // 存储参数值
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramValues[i] = req;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
            }
            // 目前只处理String类型的参数
            else if (parameterType == String.class) {
                // 获取方法上的注解参数
                Annotation[][] annotations = method.getParameterAnnotations();
                // 循环为参数设置
                for (int index = 0; index < annotations.length; index++) {
                    // 获取当前注解参数
                    Annotation[] annotation = annotations[i];
                    for (Annotation item : annotation) {
                        // 判断注解类型，因为可能存在其他注解
                        if (item instanceof DevRequestParam) {
                            // 获取参数名
                            String paramName = ((DevRequestParam) item).value();
                            if (StringUtils.isNotEmpty(paramName)) {
                                // 设置参数值 
                                // 这里是为了得到所有参数，对参数进行了一个拼接
                                // 因为可以这样写?name=1&name=2&name=3
                                String[] values = params.get(paramName);
                                String value = Joiner.on(",").join(values);
                                paramValues[i] = value;
                            }
                        }
                    }
                }
            }

        }
        return paramValues;
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
                    handlerMapping.put(url, method);
                    System.out.println("Mapping:" + url + " Method:" + method);
                }

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
}
