package com.zengbiaobiao.demo.actor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * @author zengsam
 * 测试执行类，负责接收测试任务，访问指定url，并返回测试结果。
 * 同样是一个Behavior
 */
public class TestWorker extends AbstractBehavior<TestWorker.Command> {

    /***
     * 定义规范，当前类只能接受TestWorker.Command类型数据。
     */
    public interface Command {
    }

    /***
     * 这个类是要被TestWorker接收的，必须实现TestWorker.Command接口
     */
    public static class TestRequest implements Command {
        public final String url;
        // 我们看到，这里定义了一个replyTo，表明要将此消息的执行结果返回给哪个actor。
        // 在新的API中，没有getSender()这样的方法了，需要回发的数据由message来指定，更强调了协议优先原则。
        public final ActorRef<TestManager.Command> replyTo;

        public TestRequest(String url, ActorRef<TestManager.Command> replyTo) {
            this.url = url;
            this.replyTo = replyTo;
        }
    }

    /***
     * 参考TestManger的解释
     * @return
     */
    public static Behavior<Command> create() {
        return Behaviors.setup(cxt -> new TestWorker(cxt));
    }


    /***
     * 参考TestManger的解释
     * @param context
     */
    private TestWorker(ActorContext<Command> context) {
        super(context);
    }

    /***
     * 参考TestManger的解释
     * @return
     */
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().onMessage(TestRequest.class, r -> onTestRequest(r)).build();
    }

    /***
     * 执行测试，并返回测试结果。
     * 这个actor没有自己关闭，但是它的父actor关闭的时候，它就会被关闭。
     * @param request
     * @return
     */
    private Behavior<Command> onTestRequest(TestRequest request) {
        // 统计测试时间
        long startTime = System.currentTimeMillis();
        // 读取url内容
        read(request.url);
        long endTime = System.currentTimeMillis();
        long timeElapsed = endTime - startTime;
        // 返回测试结果
        request.replyTo.tell(new TestManager.TestResponse(getContext().getSelf().path().toStringWithoutAddress(), timeElapsed));
        // 测试结束，返回相同的behavior。actor的new API有两种编程方式，面向对象和函数式编程。我们用的是面向对象的编程，所以调用return this会比较符合规范。
        // 在函数式编程中，可以调用return Behaviors.same()。它们的效果是一样的。
        // return this
        return Behaviors.same();
    }

    /***
     * 这就是个读取url内容的方法。
     * @param url
     * @return
     */
    private String read(String url) {
        StringBuilder builder = new StringBuilder();
        try {
            URL target = new URL(url);
            InputStream inputStream = target.openStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = in.readLine()) != null) {
                builder.append(line);
            }
            in.close();
        } catch (Exception e) {
            getContext().getLog().error(e.toString());
        }

        return builder.toString();
    }
}
