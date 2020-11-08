package com.zengbiaobiao.demo.actor;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


/****
 * TestManager是一个负责压测的Actor，它根据测试参数，将压测请求分发到TestWorker进行压测，并统计测试结果。测试完成后，退出。
 * 使用Akka新API，所以继承了AbstractBehavior，
 */
public class TestManager extends AbstractBehavior<TestManager.Command> {

    public interface Command {
        // 定义接口，TestManager只能接受TestManager.Command类型数据
    }

    /***
     * 所有TestManager接受的参数都定义在这里，这算是一个规范。这样比较容易识别这个Actor可以接收哪些消息
     */
    public static class TestParameter implements Command {
        // 因为这个类是通过构造函数传进来的，所以不继承Command也是可以的。但如果TestParameter通过tell发送消息给TestManager
        // 就必须继承Command接口
        public final String url;
        public final int concurrency;
        public final int total;

        public TestParameter(String url, int concurrency, int total) {
            this.url = url;
            this.concurrency = concurrency;
            this.total = total;
        }
    }

    /****
     * 测试结果统计类，必须继承Command才能被TestManager接收
     */
    public static class TestResponse implements Command {
        // 这里定义一个from，表明这个消息是从哪里发过来的，这里只是为了演示消息可以包含一些描述信息，这个from甚至可以是一个ActorReference。
        public final String from;

        // 访问url消耗的时间
        public final long timeElapsed;

        public TestResponse(String from, long timeElapsed) {
            this.from = from;
            this.timeElapsed = timeElapsed;
        }

        @Override
        public String toString() {
            return "TestResponse{" +
                    "from='" + from + '\'' +
                    ", timeElapsed=" + timeElapsed +
                    '}';
        }
    }

    private final TestParameter parameter;

    // 把所有的测试结果存起来
    private Set<TestResponse> testResult = new HashSet<>();

    // 统计整个测试消耗的时间
    private long startTime;

    /***
     * 这个构造函数很重要，首先它接收一个context参数，另外是把测试参数传进来。把它设置成私有，我们会提供一个静态工厂方法来创建它。
     * 这也算是一个规范。
     * @param context
     * @param parameter
     */
    private TestManager(ActorContext<Command> context, TestParameter parameter) {
        super(context);
        this.parameter = parameter;
        getContext().getLog().info("Start TestManager...");
        startTime = System.currentTimeMillis();

        // 定义一个pool router，这个router会创建10个actor。
        PoolRouter<TestWorker.Command> pool = Routers.pool(
                parameter.concurrency,
                // 我们这里没有使用默认的监管策略，而是当子actor失败时，重启子actor。默认行为是停止子actor。
                Behaviors.supervise(TestWorker.create()).onFailure(SupervisorStrategy.restart()));
        ActorRef<TestWorker.Command> router = context.spawn(pool, "worker-pool");

        // 把100个测试请求发给10个子actor执行，默认是Load Balance的，每个子actor都收到了10个测试请求，每个actor都线性执行测试请求。
        for (int i = 0; i < parameter.total; i++) {
            router.tell(new TestWorker.TestRequest(parameter.url, getContext().getSelf()));
        }
    }

    /***
     * 这个工厂方法也很重要，需要使用Behaviors的相关方法来创建Behavior，把context传进去。
     * 这个方法必须是静态的，外部直接点用它创建TestManager。
     * 这也是一个规范。
     * @param parameter
     * @return
     */
    public static Behavior<Command> create(TestParameter parameter) {
        return Behaviors.setup(cxt -> new TestManager(cxt, parameter));
    }

    /****
     * 重载方法，创建一个Receive<Command>。可以说，这个方法就是Behavior的逻辑。
     * @return
     */
    @Override
    public Receive<Command> createReceive() {
        // 这个方法只处理两种类型的消息，TestResponse和PostStop。PostStop是Actor系统发给它的，当actor停止的时候会受到此消息。
        return newReceiveBuilder()
                .onMessage(TestResponse.class, r -> onTestResponse(r))
                .onSignal(PostStop.class, singal -> onPostStop())
                .build();
    }

    /**
     * Actor时统计测试时间
     * @return
     */
    private Behavior<Command> onPostStop() {
        long timeElapsed = System.currentTimeMillis() - startTime;
        getContext().getLog().info("TestManager stopped, time elapsed: {}ms", timeElapsed);
        return this;
    }

    /***
     * 接收测试结果，并判断测试是否结束，如果结束，则通知actor。
     * 我们看到，每个方法都返回一个Behavior。
     * 这是因为在actor中，Behavior是可以改变的。执行完当前Behavior后，Actor需要知道下一个需要执行的Behavior是什么。
     * @param response
     * @return
     */
    private Behavior<Command> onTestResponse(TestResponse response) {
        testResult.add(response);
        if (testResult.size() < parameter.total) {
            // 测试为完成，继续接受测试参数，Behavior行为不变。
            return this;
        } else {
            // 测试结束，打印统计信息，并通知当前Actor，此时Actor的行为发生了变化，由原来的接受参数变成停止，不再是之前的Behavior，不能接收数据。
            printStatistic();
            return Behaviors.stopped();
        }
    }

    /***
     * 打印统计信息
     */
    private void printStatistic() {
        // 先对测试结果排序
        Long[] resultArray = testResult.stream().map(r -> r.timeElapsed).collect(Collectors.toList()).toArray(new Long[0]);
        Arrays.sort(resultArray);
        // 显示所有测试结果
        StringBuilder builder=new StringBuilder();
        for (int i = 0; i < resultArray.length; i++) {
            builder.append(resultArray[i].toString()).append(" ");
        }
        getContext().getLog().info("Request result: {}", builder.toString());

        //求平均时间
        long sum = Arrays.stream(resultArray).reduce(0L, Long::sum);

        long average = sum / resultArray.length;
        // 求95%时间
        int percentageIndex = (int) (resultArray.length * 0.95 - 1);
        long percentage = resultArray[percentageIndex];

        getContext().getLog().info("min request time: {}, max request time: {}, average time:{}, 95% request time:{} ",
                resultArray[0], resultArray[resultArray.length - 1], average, percentage);
    }
}
