package com.zengbiaobiao.demo;

import akka.actor.typed.ActorSystem;
import com.zengbiaobiao.demo.actor.TestManager;

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
        TestManager.TestParameter testParameter = new TestManager.TestParameter(url, concurrency, total);
        ActorSystem.create(TestManager.create(testParameter), "load-test-system");
    }
}
