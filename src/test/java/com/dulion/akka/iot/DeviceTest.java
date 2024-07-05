package com.dulion.akka.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.dulion.akka.iot.Device.Passivate;
import com.dulion.akka.iot.Device.RecordTemperature;
import com.dulion.akka.iot.Device.Request;
import com.dulion.akka.iot.Device.RequestTemperature;
import com.dulion.akka.iot.Device.TemperatureRecorded;
import com.dulion.akka.iot.Device.TemperatureReply;
import com.dulion.akka.iot.Manager.DeviceListReply;
import com.dulion.akka.iot.Manager.DeviceRegistered;
import com.dulion.akka.iot.Manager.RegisterDevice;
import com.dulion.akka.iot.Manager.RequestDeviceList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DeviceTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
    TestProbe<TemperatureReply> readProbe = testKit.createTestProbe(TemperatureReply.class);
    ActorRef<Request> deviceActor = testKit.spawn(Device.create("group", "device"));
    deviceActor.tell(RequestTemperature.builder().requestId(42L).replyTo(readProbe.getRef()).build());
    TemperatureReply reply = readProbe.receiveMessage();
    assertEquals(42L, reply.getRequestId());
    assertNull(reply.getTemperature());
  }

  @Test
  public void testReplyWithLatestTemperatureReading() {
    TestProbe<TemperatureRecorded> recordProbe = testKit.createTestProbe(TemperatureRecorded.class);
    TestProbe<TemperatureReply> readProbe = testKit.createTestProbe(TemperatureReply.class);
    ActorRef<Request> deviceActor = testKit.spawn(Device.create("group", "device"));

    deviceActor.tell(
        RecordTemperature.builder()
            .requestId(1L)
            .temperature(24.0)
            .replyTo(recordProbe.getRef())
            .build());
    assertEquals(1L, recordProbe.receiveMessage().getRequestId());

    deviceActor.tell(
        RequestTemperature.builder()
            .requestId(2L)
            .replyTo(readProbe.getRef())
            .build());
    TemperatureReply temperature = readProbe.receiveMessage();
    assertEquals(2L, temperature.getRequestId());
    assertEquals(24.0, temperature.getTemperature());
  }

  @Test
  public void testReplyToRegistrationRequests() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device1")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device2")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    TestProbe<TemperatureReply> readProbe = testKit.createTestProbe(TemperatureReply.class);
    registered1.getDevice().tell(RequestTemperature.builder()
        .requestId(1L)
        .replyTo(readProbe.getRef())
        .build());
    assertEquals(1L, readProbe.receiveMessage().getRequestId());

    registered2.getDevice().tell(RequestTemperature.builder()
        .requestId(2L)
        .replyTo(readProbe.getRef())
        .build());
    assertEquals(2L, readProbe.receiveMessage().getRequestId());
  }

  @Test
  public void testIgnoreWrongRegistrationRequests() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDevice.builder()
        .groupId("wrong")
        .deviceId("device")
        .replyTo(registeredProbe.getRef())
        .build());
    registeredProbe.expectNoMessage();
  }

  @Test
  public void testReturnSameActorForSameDeviceId() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered2 = registeredProbe.receiveMessage();

    assertEquals(registered1.getDevice(), registered2.getDevice());
  }

  @Test
  public void testListActiveDevices() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device1")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device2")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    TestProbe<DeviceListReply> deviceListProbe = testKit.createTestProbe(DeviceListReply.class);
    groupActor.tell(RequestDeviceList.builder()
        .requestId(1L)
        .replyTo(deviceListProbe.getRef())
        .build());
    DeviceListReply deviceList = deviceListProbe.receiveMessage();
    assertEquals(1L, deviceList.getRequestId());
    assertEquals(
        Stream.of("device1", "device2").collect(Collectors.toSet()),
        deviceList.getDeviceIds());
  }

  @Test
  public void testListActiveDevicesAfterOneShutsDown() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device1")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDevice.builder()
        .groupId("group")
        .deviceId("device2")
        .replyTo(registeredProbe.getRef())
        .build());
    DeviceRegistered registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    TestProbe<DeviceListReply> deviceListProbe = testKit.createTestProbe(DeviceListReply.class);
    groupActor.tell(RequestDeviceList.builder()
        .requestId(1L)
        .replyTo(deviceListProbe.getRef())
        .build());
    DeviceListReply deviceList1 = deviceListProbe.receiveMessage();
    assertEquals(1L, deviceList1.getRequestId());
    assertEquals(
        Stream.of("device1", "device2").collect(Collectors.toSet()),
        deviceList1.getDeviceIds());

    registered1.getDevice().tell(Passivate.INSTANCE);
    registeredProbe.expectTerminated(
        registered1.getDevice(),
        registeredProbe.getRemainingOrDefault());
    registeredProbe.awaitAssert(
        () -> {
          groupActor.tell(RequestDeviceList.builder()
              .requestId(2L)
              .replyTo(deviceListProbe.getRef())
              .build());
          DeviceListReply deviceList2 = deviceListProbe.receiveMessage();
          assertEquals(2L, deviceList2.getRequestId());
          assertEquals(
              Stream.of("device2").collect(Collectors.toSet()),
              deviceList2.getDeviceIds());
          return null;
        });
  }
}
