/*
 * Copyright (C), 2002-2015, 铜板街
 * FileName: AbstractDubboServiceClient.java
 * Author:   gaoseng(li.yawei)
 * Date:     2015年11月18日 下午1:05:39
 * Description: //模块目的、功能描述      
 * History: //修改记录
 * <author>      <time>      <version>    <desc>
 * 修改人姓名             修改时间            版本号                  描述
 */
package com.tongbanjie.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StringUtils;

import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * Dubbo服务客户端抽象类
 * 
 * @author liyawei
 */
public class DubboServiceClient extends AbstractJavaSamplerClient {

    private static final ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath*:application-dubbo-jmeter.xml");

    private static final GenericApplicationContext      context            = new GenericApplicationContext();
    static {
        context.setParent(applicationContext);
        context.refresh();
    }

    private <T> T getDubboServiceClient(Class<T> clazz, String version, String url, long timeout) {
        if (getBean(clazz.getName(), version, url, timeout, clazz) == null) {
            return initDubboServiceClient(clazz, version, url, timeout);
        }
        return getBean(clazz.getName(), version, url, timeout, clazz);
    }

    private <T> T getBean(String clazzName, String version, String url, long timeout, Class<T> t) {
        try {
            return context.getBean(clazzName + version + url + timeout, t);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    private synchronized <T> T initDubboServiceClient(Class<T> clazz, String version, String url, long timeout) {
        if (getBean(clazz.getName(), version, url, timeout, clazz) != null) {
            return getBean(clazz.getName(), version, url, timeout, clazz);
        }
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(ReferenceBean.class).addPropertyValue("interface", clazz.getName()).addPropertyValue("version", version)
            .addPropertyValue("timeout", timeout);
        if (!StringUtils.isEmpty(url.trim())) {
            builder = builder.addPropertyValue("url", url);
        }
        context.registerBeanDefinition(clazz.getName() + version + url + timeout, builder.getBeanDefinition());
        return getBean(clazz.getName(), version, url, timeout, clazz);
    }

    @Override
    public SampleResult runTest(JavaSamplerContext arg0) {

        SampleResult sampleResult = new SampleResult();
        sampleResult.setSuccessful(false);
        sampleResult.sampleStart();
        try {
            Iterator<String> iterator = arg0.getParameterNamesIterator();

            Class<?> clazz = null;
            String clazzString = arg0.getParameter(iterator.next());
            try {
                clazz = Class.forName(clazzString);
            } catch (Exception e) {
                sampleResult.sampleEnd();
                sampleResult.setResponseData(("请确认" + clazzString + "是否正确，而且相应包已放入lib目录下").getBytes("UTF-8"));
                return sampleResult;
            }

            Method method = null;
            try {
                String methodName = arg0.getParameter(iterator.next());
                List<String> methodTypes = JSON.parseArray(arg0.getParameter(iterator.next()), String.class);

                if (methodTypes != null && methodTypes.size() > 0) {
                    Class<?>[] methodTypeArray = new Class<?>[methodTypes.size()];
                    for (int i = 0; i < methodTypes.size(); i++) {
                        methodTypeArray[i] = forName(methodTypes.get(i));
                    }
                    method = clazz.getMethod(methodName, methodTypeArray);
                } else {
                    method = clazz.getMethod(methodName);
                }
            } catch (Exception e) {
                sampleResult.sampleEnd();
                sampleResult.setResponseData(listAllMethod(clazz));
                return sampleResult;
            }

            String url = arg0.getParameter(iterator.next());
            String version = arg0.getParameter(iterator.next());
            long timeout = arg0.getLongParameter(iterator.next(), 6000);

            if (method.getParameterTypes() == null || method.getParameterTypes().length == 0) {
                return invoke(sampleResult, method, clazz, version, url, timeout);
            }

            Object[] args = new Object[method.getParameterTypes().length];
            for (int i = 0; i < method.getParameterTypes().length; i++) {
                if (!iterator.hasNext()) {
                    sampleResult.sampleEnd();
                    sampleResult.setResponseData(("参数格式错误,请使用groovy脚本填充值或者拷贝下面对应的参数格式并填充值:" + ParametersJson.getAllJson(method.getGenericParameterTypes())).getBytes("UTF-8"));
                    return sampleResult;
                }

                String value = iterator.next();
                if (StringUtils.startsWithIgnoreCase(arg0.getParameter(value), "groovy:")) {
                    try {
                        args[i] = GroovyHelp.parseObject(arg0.getParameter(value), method.getParameterTypes()[i]);
                    } catch (RuntimeException e) {
                        sampleResult.sampleEnd();
                        sampleResult.setResponseData(("groovy脚本解析异常:" + e.getMessage() + "脚本为:" + value).getBytes("UTF-8"));
                        return sampleResult;
                    }
                } else {
                    try {
                        if (Collection.class.isAssignableFrom(method.getParameterTypes()[i])) {
                            //仅支持list内直接嵌套真实类型
                            args[i] = JSON.parseArray(arg0.getParameter(value), (Class<?>) ((ParameterizedType) method.getGenericParameterTypes()[0]).getActualTypeArguments()[0]);
                        } else {
                            args[i] = JSON.parseObject(arg0.getParameter(value), method.getParameterTypes()[i]);
                        }
                    } catch (RuntimeException e) {
                        sampleResult.sampleEnd();
                        sampleResult.setResponseData(("参数格式错误,请使用groovy脚本填充值或者拷贝下面对应的参数格式并填充值:" + ParametersJson.getAllJson(method.getGenericParameterTypes())).getBytes("UTF-8"));
                        return sampleResult;
                    }
                }

            }

            return invoke(sampleResult, method, clazz, version, url, timeout, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, Class<?>> classForNameMap = new HashMap<String, Class<?>>();
    static {
        classForNameMap.put(int.class.getName(), int.class);
        classForNameMap.put(long.class.getName(), long.class);
        classForNameMap.put(float.class.getName(), float.class);
        classForNameMap.put(double.class.getName(), double.class);
        classForNameMap.put(byte.class.getName(), byte.class);
        classForNameMap.put(boolean.class.getName(), boolean.class);
        classForNameMap.put(short.class.getName(), short.class);
        classForNameMap.put(char.class.getName(), char.class);
    }

    private Class<?> forName(String string) throws ClassNotFoundException {
        if (classForNameMap.get(string) != null) {
            return classForNameMap.get(string);
        }
        return Class.forName(string);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        arguments.addArgument("接口名(必填)", "com.tongbanjie.feeclcn.facade.api.FeeclcnFacade");
        arguments.addArgument("方法名(必填)", "normalFeeclcn");
        arguments.addArgument("方法参数类型数组(必填)", "['com.tongbanjie.feeclcn.facade.request.FeeclcnRequest','java.lang.String']");
        arguments.addArgument("服务器地址(为空则需要设置zk.jmeter对应的ip)", "dubbo://127.0.0.1:18178/");
        arguments.addArgument("版本(必填)", "2.0");
        arguments.addArgument("超时时间,单位ms(必填)", "6000");
        arguments.addArgument("请求参数1", "");
        arguments.addArgument("请求参数2", "");
        arguments.addArgument("请求参数3", "");
        arguments.addArgument("请求参数4", "");
        arguments.addArgument("请求参数5", "");
        arguments.addArgument("请求参数6", "");
        arguments.addArgument("请求参数7", "");
        arguments.addArgument("请求参数8", "");
        arguments.addArgument("请求参数9", "");
        arguments.addArgument("请求参数10", "");
        arguments.addArgument("请求参数11", "");
        arguments.addArgument("请求参数12", "");
        arguments.addArgument("请求参数13", "");
        arguments.addArgument("请求参数14", "");
        arguments.addArgument("请求参数15", "");
        return arguments;
    }

    private byte[] listAllMethod(Class<?> clazz) throws UnsupportedEncodingException {
        JSONArray jsonArray = new JSONArray();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("方法参数类型数组", toList(method.getParameterTypes()));
            jsonObject.put("方法名", method.getName());
            jsonArray.add(jsonObject);
        }
        return ("方法不存在,请核对.接口" + clazz.getName() + "可访问的方法一共有:" + jsonArray.toJSONString()).getBytes("UTF-8");
    }

    private List<String> toList(Class<?>[] parameterTypes) {
        List<String> list = new ArrayList<String>();
        for (Class<?> parameterType : parameterTypes) {
            list.add(parameterType.getName());
        }
        return list;
    }

    private SampleResult invoke(SampleResult sampleResult, Method method, Class<?> clazz, String version, String url, long timeout, Object... args) throws IOException {
        ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        try {
            Object obj = method.invoke(getDubboServiceClient(clazz, version, url, timeout), args);
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(true);
            sampleResult.setResponseData(JSON.toJSONString(obj).getBytes("UTF-8"));
        } catch (Exception e) {
            sampleResult.sampleEnd();
            e.printStackTrace(new java.io.PrintWriter(buf, true));
            sampleResult.setResponseData(("运行错误,错误信息如下:" + buf.toString()).getBytes("UTF-8"));
        } finally {
            buf.close();
        }
        return sampleResult;
    }
}
