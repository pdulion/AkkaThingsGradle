package com.dulion.akka.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.dulion.akka.iot.Device.RecordTemperature;
import com.dulion.akka.iot.Device.TemperatureRecorded;
import com.dulion.akka.iot.Device.TemperatureReply;
import com.dulion.akka.iot.Device.Request;
import com.dulion.akka.iot.Device.RequestTemperature;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.ClassRule;
import org.junit.Test;

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
    assertEquals(Optional.empty(), reply.getTemperature());
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
    assertEquals(Optional.of(24.0), temperature.getTemperature());
  }
}
