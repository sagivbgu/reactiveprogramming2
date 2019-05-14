package com.mlss.whatsapp_common;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

//#greeter-messages
public class UserFeatures extends AbstractActor {
//#greeter-messages
  static public Props props(String message, ActorRef printerActor) {
    return Props.create(UserFeatures.class, () -> new UserFeatures(message, printerActor));
  }

  //#greeter-messages
  static public class WhoToGreet {
    public final String who;

    public WhoToGreet(String who) {
        this.who = who;
    }
  }

  static public class Greet {
    public Greet() {
    }
  }
  //#greeter-messages

  private final String message;
  private final ActorRef printerActor;
  private String greeting = "";

  public UserFeatures(String message, ActorRef printerActor) {
    this.message = message;
    this.printerActor = printerActor;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(WhoToGreet.class, wtg -> {
          this.greeting = message + ", " + wtg.who;
        })
        .match(Greet.class, x -> {
          //#greeter-send-message
          printerActor.tell(new UserFeatures(greeting, printerActor), getSelf());
          //#greeter-send-message
        })
        .build();
  }
//#greeter-messages
}
//#greeter-messages
