package com.dulion.akka.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dulion.akka.iot.Manager.DeviceListReply;
import com.dulion.akka.iot.Manager.DeviceListRequest;
import com.dulion.akka.iot.Manager.RegisterDeviceReply;
import com.dulion.akka.iot.Manager.RegisterDeviceRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;

public class Group extends AbstractBehavior<Group.Request> {

  public interface Request {}

  @Value(staticConstructor = "of")
  private static class DeviceTerminated implements Request {
    String groupId;
    String deviceId;
  }

  /**
   * Create behavior for device group.
   *
   * @param groupId - Identifies group to which devices will belong.
   * @return Behavior - reference to group supplier.
   */
  public static Behavior<Request> create(String groupId) {
    return Behaviors.setup(context -> new Group(context, groupId));
  }

  private final String groupId;
  private final Map<String, ActorRef<Device.Request>> deviceToActor = new HashMap<>();

  private Group(ActorContext<Request> context, String groupId) {
    super(context);
    this.groupId = groupId;
    context.getLog().info("Group actor {} ({}) created", groupId, System.identityHashCode(this));
  }

  @Override
  public Receive<Request> createReceive() {
    return newReceiveBuilder()
        .onMessage(RegisterDeviceRequest.class, this::onRegisterDevice)
        .onMessage(DeviceListRequest.class, this::onDeviceList)
        .onMessage(DeviceTerminated.class, this::onDeviceTerminated)
        .onSignal(PostStop.class, this::onPostStop)
        .build();
  }

  private Behavior<Request> onRegisterDevice(RegisterDeviceRequest request) {
    if (!groupId.equals(request.getGroupId())) {
      getContext().getLog().warn(
          "Group actor {} ({}): RegisterDevice request for device {}-{} ignored",
          groupId,
          System.identityHashCode(this),
          request.getDeviceId(),
          request.getGroupId());
      return this;
    }

    ActorRef<Device.Request> device = deviceToActor.computeIfAbsent(
        request.getDeviceId(), this::createDevice);

    request.getReplyTo().tell(RegisterDeviceReply.of(device));
    return this;
  }

  private ActorRef<Device.Request> createDevice(String deviceId) {
    ActorRef<Device.Request> device = getContext().spawn(Device.create(groupId, deviceId), "device-" + deviceId);
    getContext().watchWith(
        device,
        DeviceTerminated.of(groupId, deviceId));
    return device;
  }

  private Behavior<Request> onDeviceList(DeviceListRequest request) {
    request.getReplyTo().tell(DeviceListReply.of(request.getRequestId(), deviceToActor.keySet()));
    return this;
  }

  private Behavior<Request> onDeviceTerminated(DeviceTerminated request) {
    getContext().getLog().info(
        "Group actor {} ({}): Device {}-{} terminated",
        groupId,
        System.identityHashCode(this),
        request.getDeviceId(),
        request.getGroupId());
    deviceToActor.remove(request.getDeviceId());
    return this;
  }

  private Behavior<Request> onPostStop(PostStop signal) {
    getContext().getLog().info(
        "Group actor {} ({}) stopped",
        groupId,
        System.identityHashCode(this));
    return this;
  }
}
