/**
 *
 * Copyright (C), 2002-2016, 铜板街.
 */
package com.tongbanjie.test;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

/**
 *
 * @author liyawei
 * @version $Id: GroovyHelp.java, v 0.1 2016年9月13日 下午4:14:39 liyawei Exp $
 */
public class GroovyHelp {

    /**
     * 
     *
     * @param text  groovy:
     * @param calzz
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T parseObject(String text, Class<T> calzz) {

        GroovyShell shell = new GroovyShell(new Binding());
        return (T) shell.evaluate(text.substring(7));
    }

}
