package com.dulion.akka;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dulion.akka.Device.Request;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

public class Device extends AbstractBehavior<Request> {

  public interface Request {

  }

  @Value
  @Builder
  public static class RequestTemperature implements Request {

    long requestId;
    ActorRef<TemperatureReply> replyTo;
  }

  @Value
  @Builder
  public static class TemperatureReply {

    long requestId;
    Optional<Double> temperature;
  }

  @Value
  @Builder
  public static class RecordTemperature implements Request {

    long requestId;
    double temperature;
    ActorRef<TemperatureRecorded> replyTo;
  }

  @Value
  @Builder
  public static class TemperatureRecorded {

    long requestId;
  }

  /**
   * Create behavior for temperature Device.
   *
   * @param groupId  - Group to which this device belongs.
   * @param deviceId - Identifier for device.
   * @return Behavior - instance representing device.
   */
  public static Behavior<Request> create(String groupId, String deviceId) {
    return Behaviors.setup(context -> new Device(context, groupId, deviceId));
  }

  private final String groupId;
  private final String deviceId;
  private Optional<Double> temperature = Optional.empty();

  public Device(ActorContext<Request> context, String groupId, String deviceId) {
    super(context);
    this.groupId = groupId;
    this.deviceId = deviceId;
    context.getLog().info("Device actor {}-{} started", groupId, deviceId);
  }

  @Override
  public Receive<Request> createReceive() {
    return newReceiveBuilder()
        .onMessage(RecordTemperature.class, this::onRecordTemperature)
        .onMessage(RequestTemperature.class, this::onReadTemperature)
        .onSignal(PostStop.class, this::onPostStop)
        .build();
  }

  private Behavior<Request> onRecordTemperature(RecordTemperature request) {
    getContext().getLog().info(
        "Temperature recording id: {}, temperatures {},  actor: {}-{} ",
        request.getRequestId(),
        request.getTemperature(),
        groupId,
        deviceId);
    temperature = Optional.of(request.getTemperature());
    request.replyTo.tell(
        TemperatureRecorded.builder()
            .requestId(request.getRequestId())
            .build());
    return this;
  }

  private Behavior<Request> onReadTemperature(RequestTemperature request) {
    getContext().getLog().info(
        "Temperature requested id: {}, temperatures {},  actor: {}-{} ",
        request.getRequestId(),
        temperature.toString(),
        groupId,
        deviceId);
    request.getReplyTo().tell(
        TemperatureReply.builder()
            .requestId(request.getRequestId())
            .temperature(temperature)
            .build());
    return this;
  }

  private Behavior<Request> onPostStop(PostStop signal) {
    getContext().getLog().info("Device actor {}-{} stopped", groupId, deviceId);
    return this;
  }
}
