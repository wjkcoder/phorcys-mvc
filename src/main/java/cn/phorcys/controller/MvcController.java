package cn.phorcys.controller;

import cn.phorcys.annotation.PhorcysController;
import cn.phorcys.annotation.PhorcysParam;
import cn.phorcys.annotation.PhorcysRequestMapping;

/**
 * @Author: Wonder
 * @Date: Created on 2020/10/6 11:48 上午
 */
@PhorcysController
public class MvcController {
    @PhorcysRequestMapping("/test")
    public String index(@PhorcysParam("name") String name) {
        return "success : " + name;
    }
}
