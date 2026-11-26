# Overview

MicroEJ Application: `SmartGreenhouse` (Sink Node, FRDM-RW612).

The Sink Node receives sensor data over LoRa and exchanges data with the cloud, the
Collaboration Agent and the Unit Kontrol over WiFi / Internet using MQTT
(`services/mqtt/MqttSubscribeService.java`).

# MQTT broker address (set from the phone)

The broker is no longer hard-coded. It is entered on the WiFi provisioning portal:

1. On the board, open WiFi provisioning; the board starts the `smartgreenhouse` hotspot and shows a QR code.
2. Connect the phone to that hotspot and open `http://192.168.1.1/`.
3. Pick the WiFi network. The password page has a **Broker MQTT** field: enter `192.168.1.23` or
   `192.168.1.23:1883` (the field shows the currently stored broker).
4. Press Connect. After the WiFi connects, the broker is saved in flash with the WiFi credentials and MQTT
   reconnects to it. It stays in use after a reboot.

Leave the field empty to use the built-in default `168.110.214.70:1883` (`BrokerConfig.DEFAULT_BROKER_ADDRESS`).
To change only the broker, run WiFi provisioning again with the same network. The broker block is stored after the
WiFi entries in the existing 512-byte credential storage, so previously saved WiFi credentials remain valid.

# Message schema and data contract (MQTT over WiFi)

All collaboration payloads are flat JSON objects. Broker, topics, QoS and size limits are
constants at the top of `MqttSubscribeService.java`.

| Channel & flow | MQTT topic(s) | Payload (JSON data contract) | Size |
|---|---|---|---|
| `telemetry/env` Sensor LoRa → Sink Node → Cloud | publish `gh01/telemetry/env` | `{"h_id":"GH01","n_id":1,"seq":104,"ts":1761200000,"sm":68.5,"st":27.1,"ec":1200,"ph":6.5,"n":42,"p":18,"k":35,"at":28.4,"ah":74.0,"lux":3200,"rssi":-67}` | ≤ 192 byte (event-driven, once per LoRa packet received) |
| `mission/command` Cloud → Sink Node → Collaboration Agent | subscribe `gh01/mission/command`, forward to `gh01/agent/mission/command` | `{"m_id":"M20261023-01","h_id":"GH01","cmd":"INSPECT","trays":[1,2],"crit_lvl":1,"ts":1761200010}` | ≤ 192 byte (event-driven) |
| `mission/status` Collaboration Agent → Sink Node → Cloud | subscribe `gh01/agent/mission/status`, forward to `gh01/mission/status` | `{"m_id":"M20261023-01","r_id":"RB01","st":"IN_PROGRESS","cur_tray":2,"pct":50,"bat":84,"seq":12,"ts":1761200045}` | ≤ 128 byte (2–5 s while a mission is active) |
| `mission/result` Collaboration Agent → Sink Node → Cloud | subscribe `gh01/agent/mission/result`, forward to `gh01/mission/result` | `{"m_id":"M20261023-01","h_id":"GH01","tray_id":2,"label":"BAGUS","conf":0.94,"anom":0,"seq":3,"ts":1761200050}` | ≤ 256 byte (event-driven per tray) |
| `actuator/control` Cloud / Sink Node → Unit Kontrol | subscribe `gh01/actuator/control`, translated to `gh01/node/255/control/valve<n>` or `.../control/pump` with payload `1` / `0` | `{"act_id":"VALVE1","action":"ON","dur_s":30,"trig":"SOIL_M_LOW","seq":8,"ts":1761200060}` | ≤ 128 byte (event-driven) |

Notes:

- `telemetry/env` attributes follow the existing LoRa sensor payload: `sm` soil moisture (%),
  `st` soil temperature (°C), `ec` conductivity, `ph`, `n`/`p`/`k`, `at` air temperature (°C),
  `ah` air humidity (%), `lux` light intensity, `rssi` LoRa RSSI (dBm). `n_id` is the LoRa node id,
  `seq` the Sink Node packet counter and `ts` the Unix time in milliseconds.
- Mission messages are forwarded unchanged. A message is dropped when it exceeds the size limit or
  misses `m_id` (plus `cmd`, `st` or `tray_id` respectively).
- `act_id` is `VALVE1`, `VALVE2` or `PUMP`; `action` is `ON` or `OFF`. When `dur_s` > 0 the Sink Node
  sends the OFF command itself after `dur_s` seconds. The Unit Kontrol keeps its existing protocol and
  still reports its state on `gh01/node/255/status/valve<n>` (and `.../status/pump`).
- Outbound Cloud messages (`telemetry/env`, `mission/status`, `mission/result`, actuator commands) pass
  through a 48-slot priority RAM queue (`command/actuator` > `status/result` > `telemetry` > `stress/diag`)
  and a 128-slot FIFO Store-and-Forward buffer when disconnected, replayed in order on reconnect.
- Optional **2-broker mode**: enter an Edge MQTT broker IP in the WiFi provisioning portal
  (`edge_broker`) so the Sink Node bridges local robot traffic (`gh01/agent/mission/*`) on the Edge
  broker to the Cloud broker while buffering during Cloud backhaul outages.
- Diagnostics are published every 1 s on `gh01/sys/diag` (`ram_q`, `sf_q`, `drops`, `lora_drops`,
  `tx_ok`, `heap_free`, `native_free`, `native_min`, `cpu_pct`, `up`, `ts`).

# Scenario test (laptop as Cloud, Collaboration Agent and Unit Kontrol)

`scripts/mqtt_scenario_test.py` (Python 3.9+, `python3 -m pip install paho-mqtt`) runs, against the
deployed Sink Node: 4 virtual sink nodes sending `telemetry/env` at the same time, one robot (RB01)
inspection mission relayed through the Sink Node, and pump ON/OFF tests (auto-OFF with `dur_s` and
manual ON then OFF). All messages and a PASS/FAIL summary with latencies are saved in `results/<timestamp>/`.

```bash
python3 scripts/mqtt_scenario_test.py --broker <broker-ip>
```
