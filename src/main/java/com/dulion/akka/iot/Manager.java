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
import lombok.Value;

public class Manager extends AbstractBehavior<Manager.Request> {

  public interface Request {}

  @Value(staticConstructor = "of")
  public static class RegisterDeviceRequest implements Manager.Request, Group.Request {
    String groupId;
    String deviceId;
    ActorRef<RegisterDeviceReply> replyTo;
  }

  @Value(staticConstructor = "of")
  public static class RegisterDeviceReply {
    ActorRef<Device.Request> device;
  }

  @Value(staticConstructor = "of")
  public static class DeviceListRequest implements Manager.Request, Group.Request {
    long requestId;
    String groupId;
    ActorRef<DeviceListReply> replyTo;
  }

  @Value(staticConstructor = "of")
  public static class DeviceListReply {
    long requestId;
    Set<String> deviceIds;
  }

  @Value(staticConstructor = "of")
  private static class GroupTerminated implements Request {
    String groupId;
  }

  @Value(staticConstructor = "of")
  public static class AllTemperaturesRequest implements GroupQuery.Request, Group.Request, Request {
    long requestId;
    String groupId;
    ActorRef<AllTemperaturesReply> replyTo;
  }

  @Value(staticConstructor = "of")
  public static class AllTemperaturesReply {
    long requestId;
    Map<String, TemperatureReading> temperatures;
  }

  public interface TemperatureReading {}

  @Value(staticConstructor = "of")
  public static class Temperature implements TemperatureReading {
    double value;
  }

  public enum TemperatureNotAvailable implements TemperatureReading {
    READING_NOT_AVAILABLE
  }

  public enum DeviceNotAvailable implements TemperatureReading {
    DEVICE_NOT_AVAILABLE
  }

  public enum DeviceTimedOut implements TemperatureReading {
    DEVICE_TIMED_OUT
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
    getContext().watchWith(group, GroupTerminated.of(groupId));
    return group;
  }

  private Behavior<Request> onDeviceList(DeviceListRequest request) {
    ActorRef<Group.Request> group = groupIdToActor.get(request.getGroupId());
    if (group != null) {
      group.tell(request);
    } else {
      request.getReplyTo().tell(DeviceListReply.of(request.getRequestId(), Collections.emptySet()));
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
