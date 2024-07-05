package com.dulion.akka.iot;

import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Supervisor extends AbstractBehavior<Void> {

  public static Behavior<Void> create() {
    return Behaviors.setup(Supervisor::new);
  }

  private Supervisor(ActorContext<Void> context) {
    super(context);
    getContext().getLog().info("IoT Application Started");
  }

  @Override
  public Receive<Void> createReceive() {
    return newReceiveBuilder().onSignal(PostStop.class, this::onPostStop).build();
  }

  private Behavior<Void> onPostStop(PostStop signal) {
    getContext().getLog().info("IoT Application Stopped");
    return this;
  }
}
