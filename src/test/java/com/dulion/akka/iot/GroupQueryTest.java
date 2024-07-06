package com.dulion.akka.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import com.dulion.akka.iot.Device.Request;
import com.dulion.akka.iot.GroupQuery.TemperatureReplyWrapper;
import com.dulion.akka.iot.Manager.AllTemperaturesReply;
import com.dulion.akka.iot.Manager.Temperature;
import com.dulion.akka.iot.Manager.TemperatureReading;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;

public class GroupQueryTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReturnValueForWorkingDevices() {
    var requester = testKit.createTestProbe(AllTemperaturesReply.class);
    var device1 = testKit.createTestProbe(Request.class);
    var device2 = testKit.createTestProbe(Request.class);
    var device3 = testKit.createTestProbe(Request.class);
    var deviceToActor = ImmutableMap.<String, ActorRef<Request>>builder()
        .put("device1", device1.ref())
        .put("device2", device2.ref())
        .put("device3", device3.ref())
        .build();
    var query = testKit.spawn(GroupQuery.create(
        requester.getRef(),
        1L,
        deviceToActor,
        Duration.ofSeconds(3)));

    device1.expectMessageClass(Device.ReadTemperatureRequest.class);
    device2.expectMessageClass(Device.ReadTemperatureRequest.class);
    device3.expectMessageClass(Device.ReadTemperatureRequest.class);

    query.tell(TemperatureReplyWrapper.of(
        Device.ReadTemperatureReply.builder()
            .requestId(1L)
            .deviceId("device1")
            .temperature(1.0)
            .build()));
    query.tell(TemperatureReplyWrapper.of(
        Device.ReadTemperatureReply.builder()
            .requestId(1L)
            .deviceId("device2")
            .temperature(2.0)
            .build()));
    query.tell(TemperatureReplyWrapper.of(
        Device.ReadTemperatureReply.builder()
            .requestId(1L)
            .deviceId("device3")
            .temperature(3.0)
            .build()));
    var expected = ImmutableMap.<String, TemperatureReading>builder()
        .put("device1", Temperature.builder().value(1.0).build())
        .put("device2", Temperature.builder().value(2.0).build())
        .put("device3", Temperature.builder().value(3.0).build())
        .build();

    var reply = requester.receiveMessage();
    assertEquals(1L, reply.getRequestId());
    assertEquals(expected, reply.getTemperatures());
  }
}
