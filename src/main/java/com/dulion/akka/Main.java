package com.dulion.akka;

import akka.actor.typed.ActorSystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
  public static void main(String[] args) {
    log.info("IoT Application Starting");
    ActorSystem.create(IotSupervisor.create(), "iot-system");
  }
}