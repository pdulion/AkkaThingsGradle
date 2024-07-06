package com.dulion.akka.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.dulion.akka.iot.Device.Passivate;
import com.dulion.akka.iot.Device.RecordTemperatureRequest;
import com.dulion.akka.iot.Device.Request;
import com.dulion.akka.iot.Device.ReadTemperatureRequest;
import com.dulion.akka.iot.Device.RecordTemperatureReply;
import com.dulion.akka.iot.Device.ReadTemperatureReply;
import com.dulion.akka.iot.Manager.DeviceListReply;
import com.dulion.akka.iot.Manager.RegisterDeviceReply;
import com.dulion.akka.iot.Manager.RegisterDeviceRequest;
import com.dulion.akka.iot.Manager.DeviceListRequest;
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
    TestProbe<ReadTemperatureReply> readProbe = testKit.createTestProbe(ReadTemperatureReply.class);
    ActorRef<Request> deviceActor = testKit.spawn(Device.create("group", "device"));
    deviceActor.tell(ReadTemperatureRequest.of(42L, readProbe.getRef()));
    ReadTemperatureReply reply = readProbe.receiveMessage();
    assertEquals(42L, reply.getRequestId());
    assertNull(reply.getTemperature());
  }

  @Test
  public void testReplyWithLatestTemperatureReading() {
    TestProbe<RecordTemperatureReply> recordProbe = testKit.createTestProbe(RecordTemperatureReply.class);
    TestProbe<ReadTemperatureReply> readProbe = testKit.createTestProbe(ReadTemperatureReply.class);
    ActorRef<Request> deviceActor = testKit.spawn(Device.create("group", "device"));

    deviceActor.tell(RecordTemperatureRequest.of(1L, 24.0, recordProbe.getRef()));
    assertEquals(1L, recordProbe.receiveMessage().getRequestId());

    deviceActor.tell(ReadTemperatureRequest.of(2L, readProbe.getRef()));
    ReadTemperatureReply temperature = readProbe.receiveMessage();
    assertEquals(2L, temperature.getRequestId());
    assertEquals(24.0, temperature.getTemperature());
  }

  @Test
  public void testReplyToRegistrationRequests() {
    TestProbe<RegisterDeviceReply> registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    RegisterDeviceReply registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    RegisterDeviceReply registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    TestProbe<ReadTemperatureReply> readProbe = testKit.createTestProbe(ReadTemperatureReply.class);
    registered1.getDevice().tell(ReadTemperatureRequest.of(1L, readProbe.getRef()));
    assertEquals(1L, readProbe.receiveMessage().getRequestId());

    registered2.getDevice().tell(ReadTemperatureRequest.of(2L, readProbe.getRef()));
    assertEquals(2L, readProbe.receiveMessage().getRequestId());
  }

  @Test
  public void testIgnoreWrongRegistrationRequests() {
    TestProbe<RegisterDeviceReply> registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("wrong","device", registeredProbe.getRef()));
    registeredProbe.expectNoMessage();
  }

  @Test
  public void testReturnSameActorForSameDeviceId() {
    TestProbe<RegisterDeviceReply> registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device", registeredProbe.getRef()));
    RegisterDeviceReply registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device", registeredProbe.getRef()));
    RegisterDeviceReply registered2 = registeredProbe.receiveMessage();

    assertEquals(registered1.getDevice(), registered2.getDevice());
  }

  @Test
  public void testListActiveDevices() {
    TestProbe<RegisterDeviceReply> registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    RegisterDeviceReply registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    RegisterDeviceReply registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    TestProbe<DeviceListReply> deviceListProbe = testKit.createTestProbe(DeviceListReply.class);
    groupActor.tell(DeviceListRequest.of(1L, "group", deviceListProbe.getRef()));
    DeviceListReply deviceList = deviceListProbe.receiveMessage();
    assertEquals(1L, deviceList.getRequestId());
    assertEquals(
        Stream.of("device1", "device2").collect(Collectors.toSet()),
        deviceList.getDeviceIds());
  }

  @Test
  public void testListActiveDevicesAfterOneShutsDown() {
    TestProbe<RegisterDeviceReply> registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    ActorRef<Group.Request> groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    RegisterDeviceReply registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    RegisterDeviceReply registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    TestProbe<DeviceListReply> deviceListProbe = testKit.createTestProbe(DeviceListReply.class);
    groupActor.tell(DeviceListRequest.of(1L, "group", deviceListProbe.getRef()));
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
          groupActor.tell(DeviceListRequest.of(2L, "group", deviceListProbe.getRef()));
          DeviceListReply deviceList2 = deviceListProbe.receiveMessage();
          assertEquals(2L, deviceList2.getRequestId());
          assertEquals(
              Stream.of("device2").collect(Collectors.toSet()),
              deviceList2.getDeviceIds());
          return null;
        });
  }
}
