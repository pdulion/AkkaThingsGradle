package com.dulion.akka.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import com.dulion.akka.iot.Device.Request;
import com.dulion.akka.iot.GroupQuery.TemperatureReplyWrapper;
import com.dulion.akka.iot.Manager.AllTemperaturesReply;
import com.dulion.akka.iot.Manager.DeviceTimedOut;
import com.dulion.akka.iot.Manager.Temperature;
import com.dulion.akka.iot.Manager.TemperatureNotAvailable;
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

    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device1", 1.0)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device2", 2.0)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device3", 3.0)));
    var expected = ImmutableMap.<String, TemperatureReading>builder()
        .put("device1", Temperature.of(1.0))
        .put("device2", Temperature.of(2.0))
        .put("device3", Temperature.of(3.0))
        .build();

    var reply = requester.receiveMessage();
    assertEquals(1L, reply.getRequestId());
    assertEquals(expected, reply.getTemperatures());
  }

  @Test
  public void testReturnNotAvailableForDevicesWithNoReadings() {
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

    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device1", 1.0)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device2", null)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device3", 3.0)));
    var expected = ImmutableMap.<String, TemperatureReading>builder()
        .put("device1", Temperature.of(1.0))
        .put("device2", TemperatureNotAvailable.READING_NOT_AVAILABLE)
        .put("device3", Temperature.of(3.0))
        .build();

    var reply = requester.receiveMessage();
    assertEquals(1L, reply.getRequestId());
    assertEquals(expected, reply.getTemperatures());
  }

  @Test
  public void testReturnValueEvenIfDeviceStopsAfterAnswering() {
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

    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device1", 1.0)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device2", 2.0)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device3", 3.0)));
    device1.stop();
    device2.stop();
    var expected = ImmutableMap.<String, TemperatureReading>builder()
        .put("device1", Temperature.of(1.0))
        .put("device2", Temperature.of(2.0))
        .put("device3", Temperature.of(3.0))
        .build();

    var reply = requester.receiveMessage();
    assertEquals(1L, reply.getRequestId());
    assertEquals(expected, reply.getTemperatures());
  }

  @Test
  public void testReturnDeviceTimedOutIfNoAnswerInTime() {
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
        Duration.ofMillis(300)));

    device1.expectMessageClass(Device.ReadTemperatureRequest.class);
    device2.expectMessageClass(Device.ReadTemperatureRequest.class);
    device3.expectMessageClass(Device.ReadTemperatureRequest.class);

    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device1", 1.0)));
    query.tell(TemperatureReplyWrapper.of(Device.ReadTemperatureReply.of(1L, "device3", 3.0)));
    var expected = ImmutableMap.<String, TemperatureReading>builder()
        .put("device1", Temperature.of(1.0))
        .put("device2", DeviceTimedOut.DEVICE_TIMED_OUT)
        .put("device3", Temperature.of(3.0))
        .build();

    var reply = requester.receiveMessage();
    assertEquals(1L, reply.getRequestId());
    assertEquals(expected, reply.getTemperatures());
  }
}
