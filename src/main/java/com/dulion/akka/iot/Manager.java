package com.dulion.akka.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

public class Manager extends AbstractBehavior<Manager.Request> {

  public interface Request {}

  @Value
  @Builder
  public static class RegisterDeviceRequest implements Manager.Request, Group.Request {
    String groupId;
    String deviceId;
    ActorRef<RegisterDeviceReply> replyTo;
  }

  @Value
  @Builder
  public static class RegisterDeviceReply {
    ActorRef<Device.Request> device;
  }

  @Value
  @Builder
  public static class DeviceListRequest implements Manager.Request, Group.Request {
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

  @Value
  @Builder
  private static class GroupTerminated implements Request {
    String groupId;
  }

  @Value
  @Builder
  public static class AllTemperaturesRequest implements GroupQuery.Request, Group.Request, Request {
    long requestId;
    String groupId;
    ActorRef<AllTemperaturesReply> replyTo;
  }

  @Value
  @Builder
  public static class AllTemperaturesReply {
    long requestId;
    Map<String, TemperatureReading> temperatures;
  }

  public interface TemperatureReading {}

  @Value
  @Builder
  public class Temperature implements TemperatureReading {
    double value;
  }

  public enum TemperatureNotAvailable implements TemperatureReading {
    INSTANCE
  }

  public enum DeviceNotAvailable implements TemperatureReading {
    INSTANCE
  }

  public enum DeviceTimedOut implements TemperatureReading {
    INSTANCE
  }

  /**
   * Create behavior for device manager.
   *
   * @return Behavior - reference to manager supplier.
   */
  public static Behavior<Request> create() {
    return Behaviors.setup(Manager::new);
  }

  private final Map<String, ActorRef<Group.Request>> groupIdToActor = new HashMap<>();

  private Manager(ActorContext<Request> context) {
    super(context);
    context.getLog().info("Manager actor ({}) created", System.identityHashCode(this));
  }

  @Override
  public Receive<Request> createReceive() {
    return newReceiveBuilder()
        .onMessage(RegisterDeviceRequest.class, this::onRegisterDevice)
        .onMessage(DeviceListRequest.class, this::onDeviceList)
        .onMessage(GroupTerminated.class, this::onGroupTerminated)
        .onSignal(PostStop.class, this::onPostStop)
        .build();
  }

  private Behavior<Request> onRegisterDevice(RegisterDeviceRequest request) {
    groupIdToActor.computeIfAbsent(request.getGroupId(), this::createGroup).tell(request);
    return this;
  }

  private ActorRef<Group.Request> createGroup(String groupId) {
    ActorRef<Group.Request> group = getContext().spawn(Group.create(groupId), "group-" + groupId);
    getContext().watchWith(group, GroupTerminated.builder().groupId(groupId).build());
    return group;
  }

  private Behavior<Request> onDeviceList(DeviceListRequest request) {
    ActorRef<Group.Request> group = groupIdToActor.get(request.getGroupId());
    if (group != null) {
      group.tell(request);
    } else {
      request.getReplyTo().tell(DeviceListReply.builder()
          .requestId(request.getRequestId())
          .deviceIds(Collections.emptySet())
          .build());
    }
    return this;
  }

  private Behavior<Request> onGroupTerminated(GroupTerminated terminated) {
    getContext().getLog().info(
        "Manager actor ({}): Group {} terminated",
        System.identityHashCode(this),
        terminated.getGroupId());
    groupIdToActor.remove(terminated.getGroupId());
    return this;
  }

  private Behavior<Request> onPostStop(PostStop stop) {
    getContext().getLog().info("Manager actor ({}) stopped", System.identityHashCode(this));
    return this;
  }

}
