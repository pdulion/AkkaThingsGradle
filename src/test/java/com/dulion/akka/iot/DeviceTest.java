package com.dulion.akka.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import com.dulion.akka.iot.Device.Passivate;
import com.dulion.akka.iot.Device.ReadTemperatureReply;
import com.dulion.akka.iot.Device.ReadTemperatureRequest;
import com.dulion.akka.iot.Device.RecordTemperatureReply;
import com.dulion.akka.iot.Device.RecordTemperatureRequest;
import com.dulion.akka.iot.Manager.AllTemperaturesReply;
import com.dulion.akka.iot.Manager.AllTemperaturesRequest;
import com.dulion.akka.iot.Manager.DeviceListReply;
import com.dulion.akka.iot.Manager.DeviceListRequest;
import com.dulion.akka.iot.Manager.RegisterDeviceReply;
import com.dulion.akka.iot.Manager.RegisterDeviceRequest;
import com.dulion.akka.iot.Manager.Temperature;
import com.dulion.akka.iot.Manager.TemperatureNotAvailable;
import com.dulion.akka.iot.Manager.TemperatureReading;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DeviceTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
    var readProbe = testKit.createTestProbe(ReadTemperatureReply.class);
    var deviceActor = testKit.spawn(Device.create("group", "device"));
    deviceActor.tell(ReadTemperatureRequest.of(42L, readProbe.getRef()));
    var reply = readProbe.receiveMessage();
    assertEquals(42L, reply.getRequestId());
    assertNull(reply.getTemperature());
  }

  @Test
  public void testReplyWithLatestTemperatureReading() {
    var recordProbe = testKit.createTestProbe(RecordTemperatureReply.class);
    var readProbe = testKit.createTestProbe(ReadTemperatureReply.class);
    var deviceActor = testKit.spawn(Device.create("group", "device"));

    deviceActor.tell(RecordTemperatureRequest.of(1L, 24.0, recordProbe.getRef()));
    assertEquals(1L, recordProbe.receiveMessage().getRequestId());

    deviceActor.tell(ReadTemperatureRequest.of(2L, readProbe.getRef()));
    var temperature = readProbe.receiveMessage();
    assertEquals(2L, temperature.getRequestId());
    assertEquals(24.0, temperature.getTemperature(), 0.0);
  }

  @Test
  public void testReplyToRegistrationRequests() {
    var registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    var groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    var registered1 = registeredProbe.receiveMessage();
    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    var registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    var readProbe = testKit.createTestProbe(ReadTemperatureReply.class);
    registered1.getDevice().tell(ReadTemperatureRequest.of(1L, readProbe.getRef()));
    assertEquals(1L, readProbe.receiveMessage().getRequestId());
    registered2.getDevice().tell(ReadTemperatureRequest.of(2L, readProbe.getRef()));
    assertEquals(2L, readProbe.receiveMessage().getRequestId());
  }

  @Test
  public void testIgnoreWrongRegistrationRequests() {
    var registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    var groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("wrong","device", registeredProbe.getRef()));
    registeredProbe.expectNoMessage();
  }

  @Test
  public void testReturnSameActorForSameDeviceId() {
    var registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    var groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device", registeredProbe.getRef()));
    var registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device", registeredProbe.getRef()));
    var registered2 = registeredProbe.receiveMessage();

    assertEquals(registered1.getDevice(), registered2.getDevice());
  }

  @Test
  public void testListActiveDevices() {
    var registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    var groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    var registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    var registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    var deviceListProbe = testKit.createTestProbe(DeviceListReply.class);
    groupActor.tell(DeviceListRequest.of(1L, "group", deviceListProbe.getRef()));
    var deviceList = deviceListProbe.receiveMessage();
    assertEquals(1L, deviceList.getRequestId());
    assertEquals(
        Stream.of("device1", "device2").collect(Collectors.toSet()),
        deviceList.getDeviceIds());
  }

  @Test
  public void testListActiveDevicesAfterOneShutsDown() {
    var registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);
    var groupActor = testKit.spawn(Group.create("group"));

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    var registered1 = registeredProbe.receiveMessage();

    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    var registered2 = registeredProbe.receiveMessage();
    assertNotEquals(registered1.getDevice(), registered2.getDevice());

    var deviceListProbe = testKit.createTestProbe(DeviceListReply.class);
    groupActor.tell(DeviceListRequest.of(1L, "group", deviceListProbe.getRef()));
    var deviceList1 = deviceListProbe.receiveMessage();
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

  @Test
  public void testColletTemperaturesFromAllActiveDevices() {
    var groupActor = testKit.spawn(Group.create("group"));

    // Add devices to group
    var registeredProbe = testKit.createTestProbe(RegisterDeviceReply.class);

    groupActor.tell(RegisterDeviceRequest.of("group", "device1", registeredProbe.getRef()));
    var device1 = registeredProbe.receiveMessage();
    groupActor.tell(RegisterDeviceRequest.of("group","device2", registeredProbe.getRef()));
    var device2 = registeredProbe.receiveMessage();
    groupActor.tell(RegisterDeviceRequest.of("group","device3", registeredProbe.getRef()));
    registeredProbe.receiveMessage();

    // Add temperature to devices
    var recordProbe = testKit.createTestProbe(Device.RecordTemperatureReply.class);

    device1.getDevice().tell(Device.RecordTemperatureRequest.of(1L, 1.0, recordProbe.getRef()));
    assertEquals(1L, recordProbe.receiveMessage().getRequestId());
    device2.getDevice().tell(Device.RecordTemperatureRequest.of(2L, 2.0, recordProbe.getRef()));
    assertEquals(2L, recordProbe.receiveMessage().getRequestId());
    // No temperature for device 3

    var temperatureReply = testKit.createTestProbe(AllTemperaturesReply.class);
    groupActor.tell(AllTemperaturesRequest.of(3L, "group", temperatureReply.getRef()));

    var expected = ImmutableMap.<String, TemperatureReading>builder()
        .put("device1", Temperature.of(1.0))
        .put("device2", Temperature.of(2.0))
        .put("device3", TemperatureNotAvailable.READING_NOT_AVAILABLE)
        .build();

    var reply = temperatureReply.receiveMessage();
    assertEquals(3L, reply.getRequestId());
    assertEquals(expected, reply.getTemperatures());
  }
}
