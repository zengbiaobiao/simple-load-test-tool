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
 */
public class TestWorker extends AbstractBehavior<TestWorker.Command> {

    public interface Command {
    }

    public static class TestRequest implements Command {
        public final String url;
        public final ActorRef<TestManager.Command> reployTo;

        public TestRequest(String url, ActorRef<TestManager.Command> reployTo) {
            this.url = url;
            this.reployTo = reployTo;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(cxt -> new TestWorker(cxt));
    }


    private TestWorker(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().onMessage(TestRequest.class, r -> onTestRequest(r)).build();
    }

    private Behavior<Command> onTestRequest(TestRequest request) {
        long startTime = System.currentTimeMillis();
        read(request.url);
        long endTime = System.currentTimeMillis();
        long timeElapsed = endTime - startTime;
        request.reployTo.tell(new TestManager.TestResponse(getContext().getSelf().path().toStringWithoutAddress(), timeElapsed));
        return Behaviors.same();
    }

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
            Thread.sleep(2222);
        } catch (Exception e) {
            getContext().getLog().error(e.toString());
        }

        return builder.toString();
    }
}
