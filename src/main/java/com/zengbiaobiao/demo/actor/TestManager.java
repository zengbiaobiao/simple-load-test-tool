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

public class TestManager extends AbstractBehavior<TestManager.Command> {

    public interface Command {
    }

    public static class TestParameter implements Command {
        public final String url;
        public final int concurrency;
        public final int total;

        public TestParameter(String url, int concurrency, int total) {
            this.url = url;
            this.concurrency = concurrency;
            this.total = total;
        }
    }

    public static class TestResponse implements Command {
        public final String from;
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

    private Set<TestResponse> testResult = new HashSet<>();

    private long startTime;

    private TestManager(ActorContext<Command> context, TestParameter parameter) {
        super(context);
        this.parameter = parameter;
        getContext().getLog().info("Start TestManager...");
        startTime = System.currentTimeMillis();

        PoolRouter<TestWorker.Command> pool = Routers.pool(
                parameter.concurrency,
                // make sure the workers are restarted if they fail
                Behaviors.supervise(TestWorker.create()).onFailure(SupervisorStrategy.restart()));
        ActorRef<TestWorker.Command> router = context.spawn(pool, "worker-pool");

        for (int i = 0; i < parameter.total; i++) {
            router.tell(new TestWorker.TestRequest(parameter.url, getContext().getSelf()));
        }
    }

    public static Behavior<Command> create(TestParameter parameter) {
        return Behaviors.setup(cxt -> new TestManager(cxt, parameter));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(TestResponse.class, r -> onTestResponse(r))
                .onSignal(PostStop.class, singal -> onPostStop())
                .build();
    }

    private Behavior<Command> onPostStop() {
        long timeElapsed = System.currentTimeMillis() - startTime;
        getContext().getLog().info("TestManager stopped, time elapsed: {}ms", timeElapsed);
        return this;
    }

    private Behavior<Command> onTestResponse(TestResponse response) {
        testResult.add(response);
        if (testResult.size() < parameter.total) {
            return this;
        } else {
            printStatistic();
            return Behaviors.stopped();
        }
    }

    private void printStatistic() {
        Long[] resultArray = testResult.stream().map(r -> r.timeElapsed).collect(Collectors.toList()).toArray(new Long[0]);
        Arrays.sort(resultArray);
        getContext().getLog().info("Request result: {}", resultArray);
        long sum = Arrays.stream(resultArray).reduce(0L, Long::sum);

        long average = sum / resultArray.length;
        int percentageIndex = (int) (resultArray.length * 0.95 - 1);
        long percentage = resultArray[percentageIndex];

        getContext().getLog().info("min request time: {}, max request time: {}, average time:{}, 95% request time:{} ",
                resultArray[0], resultArray[resultArray.length - 1], average, percentage);
    }
}
