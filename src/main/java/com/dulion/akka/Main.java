package com.dulion.akka;

import akka.actor.typed.ActorSystem;
import com.dulion.akka.iot.Supervisor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
  public static void main(String[] args) {
    log.info("IoT Application Starting");
    ActorSystem.create(Supervisor.create(), "iot-system");
  }
}