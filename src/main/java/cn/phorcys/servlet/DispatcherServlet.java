package cn.phorcys.servlet;

import cn.phorcys.annotation.PhorcysAutowired;
import cn.phorcys.annotation.PhorcysController;
import cn.phorcys.annotation.PhorcysParam;
import cn.phorcys.annotation.PhorcysRequestMapping;
import cn.phorcys.annotation.PhorcysService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: Wonder
 * @Date: Created on 2020/10/6 4:07 下午
 */
public class DispatcherServlet extends HttpServlet {
    private Set<String> beanNameSet = new HashSet<String>();
    private Map<String, Method> pathHandleMap = new ConcurrentHashMap<String, Method>();
    private Map<String, Object> beanMap = new ConcurrentHashMap<String, Object>();

    @Override
    public void init(ServletConfig config) throws ServletException {

        try {
            /*1 扫描包*/
            scanPackage("cn.phorcys");
            /*2.实例化bean*/
            doInstance();
            /*3.依赖注入*/
            doDependencyInsert();
            /*4.生成路径映射*/
            doPathMapping();
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.init(config);
    }

    private void doPathMapping() {
        for (Map.Entry<String, Object> node : beanMap.entrySet()) {
            Object value = node.getValue();
            Class<?> clazz = value.getClass();
            if (clazz.isAnnotationPresent(PhorcysController.class)) {
                String path = "";
                if (clazz.isAnnotationPresent(PhorcysRequestMapping.class)) {
                    path += clazz.getAnnotation(PhorcysRequestMapping.class).value();
                }
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(PhorcysRequestMapping.class)) {
                        PhorcysRequestMapping annotation = method.getAnnotation(PhorcysRequestMapping.class);
                        String methodPath = path + annotation.value();
                        pathHandleMap.put(methodPath, method);
                    }
                }
            }
        }
    }

    private void doDependencyInsert() {
        for (Map.Entry<String, Object> node : beanMap.entrySet()) {
            Object value = node.getValue();
            Class<?> clazz = value.getClass();
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(PhorcysAutowired.class)) {
                    Class<?> fieldDeclaringClass = field.getDeclaringClass();
                    String name = fieldDeclaringClass.getName();
                    field.setAccessible(true);
                    try {
                        // TODO解决循环依赖*/
                        field.set(value, beanMap.get(name));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doInstance() {
        for (String name : beanNameSet) {
            try {
                Class<?> clazz = Class.forName(name);
                // 把我定义好的注解标示的类实例化
                if (clazz.isAnnotationPresent(PhorcysController.class) || clazz.isAnnotationPresent(PhorcysService.class)) {
                    Object instance = clazz.newInstance();
                    beanMap.put(clazz.getName(), instance);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    private void scanPackage(String basePackage) throws Exception {
        URL resource = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        if (resource == null) throw new Exception("找不到扫描的包根路径");
        String fileStr = resource.getFile();
        File file = new File(fileStr);
        String[] list = file.list();
        assert list != null;
        for (String path : list) {
            String childPath = fileStr + "/" + path;
            File childFile = new File(childPath);
            if (!childFile.isDirectory()) {
                /*是文件，判断是不是class文件*/
                String fileName = childFile.getName();
                if (fileName.endsWith(".class")) {
                    beanNameSet.add(basePackage + "." + fileName.substring(0, fileName.length() - 6));
                }
            } else {
                scanPackage(basePackage + "." + path);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI();
        Method method = pathHandleMap.get(path);
        String name = method.getDeclaringClass().getName();
        Object o = beanMap.get(name);
        Parameter[] parameters = method.getParameters();

        Object[] args = handleParameters(req, resp, method);
        try {
            Object result = method.invoke(o, args);
            PrintWriter writer = resp.getWriter();
            writer.print(result);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private Object[] handleParameters(HttpServletRequest req, HttpServletResponse resp, Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Parameter[] parameters = method.getParameters();

        Object[] args = new Object[parameterTypes.length];
        int index = 0;
        for (Parameter parameter : parameters) {
            if (ServletRequest.class.isAssignableFrom(parameter.getType())) {
                args[index++] = req;
            }
            if (ServletResponse.class.isAssignableFrom(parameter.getType())) {
                args[index++] = resp;
            }
            if (parameter.isAnnotationPresent(PhorcysParam.class)) {
                PhorcysParam annotation = parameter.getAnnotation(PhorcysParam.class);
                args[index++] = req.getParameter(annotation.value());
            } else {
                args[index++] = req.getParameter(parameter.getName());
            }
        }
        return args;
    }
}
