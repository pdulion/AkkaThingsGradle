package com.dulion.akka.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

public class Manager extends AbstractBehavior<Manager.Request> {

  public interface Request {

  }

  @Value
  @Builder
  public static class RegisterDevice implements Manager.Request, Group.Request {
    String groupId;
    String deviceId;
    ActorRef<DeviceRegistered> replyTo;
  }

  @Value
  @Builder
  public static class DeviceRegistered {
    ActorRef<Device.Request> device;
  }

  @Value
  @Builder
  public static class RequestDeviceList implements Manager.Request, Group.Request {
    long requestId;
    String groupId;
    ActorRef<DeviceListReply> replyTo;
  }

  @Value
  @Builder
  public static class DeviceListReply {
    long requestId;
    Set<String> deviceIds;
  }

  private Manager(ActorContext<Request> context) {
    super(context);
  }

  @Override
  public Receive<Request> createReceive() {
    return newReceiveBuilder()
        .build();
  }
}
