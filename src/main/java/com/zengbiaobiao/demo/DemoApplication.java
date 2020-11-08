package com.zengbiaobiao.demo;

import akka.actor.typed.ActorSystem;
import com.zengbiaobiao.demo.actor.TestManager;

/****
 * 简易压测工具，输入url, concurrency total 对一个网站进行压测。concurrency是并发数，total是总数
 */
public class DemoApplication {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage:");
            System.out.println("java -jar demo.jar <url> <concurrency> <total>");
            return;
        }
        String url = args[0];
        int concurrency = Integer.valueOf(args[1]);
        int total = Integer.valueOf(args[2]);
        // 构建测试参数
        TestManager.TestParameter testParameter = new TestManager.TestParameter(url, concurrency, total);
        // 启动TestManager，进行压测
        ActorSystem.create(TestManager.create(testParameter), "load-test-system");
    }
}
