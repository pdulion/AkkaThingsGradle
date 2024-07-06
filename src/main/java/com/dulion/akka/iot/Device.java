package com.dulion.akka.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dulion.akka.iot.Device.Request;
import lombok.Builder;
import lombok.Value;

public class Device extends AbstractBehavior<Request> {

  public interface Request {}

  @Value
  @Builder
  public static class ReadTemperatureRequest implements Request {
    long requestId;
    ActorRef<ReadTemperatureReply> replyTo;
  }

  @Value
  @Builder
  public static class ReadTemperatureReply {
    long requestId;
    String deviceId;
    Double temperature;
  }

  @Value
  @Builder
  public static class RecordTemperatureRequest implements Request {
    long requestId;
    double temperature;
    ActorRef<RecordTemperatureReply> replyTo;
  }

  @Value
  @Builder
  public static class RecordTemperatureReply {
    long requestId;
  }

  enum Passivate implements Request {
    INSTANCE
  }

  /**
   * Create behavior for temperature device.
   *
   * @param groupId  - Group to which this device belongs.
   * @param deviceId - Identifier for device.
   * @return Behavior - reference to device supplier.
   */
  public static Behavior<Request> create(String groupId, String deviceId) {
    return Behaviors.setup(context -> new Device(context, groupId, deviceId));
  }

  private final String groupId;
  private final String deviceId;
  private Double temperature = null;

  public Device(ActorContext<Request> context, String groupId, String deviceId) {
    super(context);
    this.groupId = groupId;
    this.deviceId = deviceId;
    context.getLog().info("Device actor {}-{} ({}) created", groupId, deviceId, System.identityHashCode(this));
  }

  @Override
  public Receive<Request> createReceive() {
    return newReceiveBuilder()
        .onMessage(RecordTemperatureRequest.class, this::onRecordTemperature)
        .onMessage(ReadTemperatureRequest.class, this::onReadTemperature)
        .onMessage(Passivate.class, signal -> Behaviors.stopped())
        .onSignal(PostStop.class, this::onPostStop)
        .build();
  }

  private Behavior<Request> onRecordTemperature(RecordTemperatureRequest request) {
    getContext().getLog().info(
        "Recording Temperature requestId: {}, temperatures {}, actor: {}-{} ({})",
        request.getRequestId(),
        request.getTemperature(),
        groupId,
        deviceId,
        System.identityHashCode(this));
    temperature = request.getTemperature();
    request.replyTo.tell(
        RecordTemperatureReply.builder()
            .requestId(request.getRequestId())
            .build());
    return this;
  }

  private Behavior<Request> onReadTemperature(ReadTemperatureRequest request) {
    getContext().getLog().info(
        "Temperature requested requestId: {}, temperatures {}, actor: {}-{} ({})",
        request.getRequestId(),
        temperature,
        groupId,
        deviceId,
        System.identityHashCode(this));
    request.getReplyTo().tell(
        ReadTemperatureReply.builder()
            .requestId(request.getRequestId())
            .deviceId(deviceId)
            .temperature(temperature)
            .build());
    return this;
  }

  private Behavior<Request> onPostStop(PostStop signal) {
    getContext().getLog().info(
        "Device actor {}-{} ({}) stopped",
        groupId,
        deviceId,
        System.identityHashCode(this));
    return this;
  }
}
