#!/usr/bin/env python3
"""
Skenario uji kontrak data kolaborasi (Tabel 4.6) untuk Sink Node SmartGreenhouse.

Laptop menjalankan semua peran di sekitar Sink Node (FRDM-RW612) yang sudah dideploy:

  * 4 Sink Node virtual  -> mengirim telemetry/env bersamaan (langsung ke broker)
  * Cloud                -> mengirim mission/command dan actuator/control
  * Collaboration Agent  -> 1 robot inspeksi (RB01) yang menjawab mission/status dan mission/result
  * Unit Kontrol         -> menjawab perintah pompa dengan status (bisa dimatikan: --no-unit-sim)
  * Monitor              -> merekam semua pesan gh01/# untuk dianalisis

Alur yang diuji melewati Sink Node asli:

  gh01/mission/command        --(Sink Node)--> gh01/agent/mission/command
  gh01/agent/mission/status   --(Sink Node)--> gh01/mission/status
  gh01/agent/mission/result   --(Sink Node)--> gh01/mission/result
  gh01/actuator/control       --(Sink Node)--> gh01/node/255/control/pump  ("1" / "0")

Hasil disimpan di folder results/<tanggal_jam>/:
  messages.csv   semua pesan TX/RX beserta waktu, ukuran, payload
  events.log     log yang sama dengan yang tampil di layar
  summary.json   hasil tiap pengecekan, latensi, dan konfigurasi
  summary.txt    ringkasan PASS/FAIL yang mudah dibaca

Kebutuhan: Python 3.9+, paho-mqtt (python3 -m pip install paho-mqtt)

Contoh:
  python3 mqtt_scenario_test.py --broker 192.168.1.23
"""

import argparse
import csv
import json
import os
import random
import sys
import threading
import time
from datetime import datetime

try:
    import paho.mqtt.client as mqtt
except ImportError:
    sys.exit("paho-mqtt belum terpasang. Jalankan: python3 -m pip install paho-mqtt")


HOUSE_ID = "GH01"

TOPIC_TELEMETRY = "gh01/telemetry/env"
TOPIC_MISSION_COMMAND = "gh01/mission/command"
TOPIC_AGENT_COMMAND = "gh01/agent/mission/command"
TOPIC_AGENT_STATUS = "gh01/agent/mission/status"
TOPIC_MISSION_STATUS = "gh01/mission/status"
TOPIC_AGENT_RESULT = "gh01/agent/mission/result"
TOPIC_MISSION_RESULT = "gh01/mission/result"
TOPIC_ACTUATOR_CONTROL = "gh01/actuator/control"
TOPIC_PUMP_CONTROL = "gh01/node/255/control/pump"
TOPIC_PUMP_STATUS = "gh01/node/255/status/pump"
TOPIC_UNIT_CONTROL_FILTER = "gh01/node/255/control/+"

# Batas ukuran payload sesuai kontrak data (byte).
MAX_BYTES = {
    TOPIC_TELEMETRY: 192,
    TOPIC_MISSION_COMMAND: 192,
    TOPIC_AGENT_COMMAND: 192,
    TOPIC_AGENT_STATUS: 128,
    TOPIC_MISSION_STATUS: 128,
    TOPIC_AGENT_RESULT: 256,
    TOPIC_MISSION_RESULT: 256,
    TOPIC_ACTUATOR_CONTROL: 128,
}

PASS = "PASS"
FAIL = "FAIL"
INFO = "INFO"


# --------------------------------------------------------------------------- #
# Perekam pesan
# --------------------------------------------------------------------------- #

class Recorder:
    """Menyimpan semua pesan TX/RX (thread-safe) ke memori, CSV, dan log."""

    def __init__(self, output_dir):
        self.lock = threading.Lock()
        self.start = time.monotonic()
        self.rows = []
        self.csv_file = open(os.path.join(output_dir, "messages.csv"), "w", newline="", encoding="utf-8")
        self.csv = csv.writer(self.csv_file)
        self.csv.writerow(["t_s", "wall_time", "dir", "client", "topic", "bytes", "payload"])
        self.log_file = open(os.path.join(output_dir, "events.log"), "w", encoding="utf-8")

    def now(self):
        return time.monotonic() - self.start

    def log(self, text):
        line = "[{:8.3f}] {}".format(self.now(), text)
        with self.lock:
            print(line, flush=True)
            self.log_file.write(line + "\n")
            self.log_file.flush()

    def record(self, direction, client, topic, payload):
        if isinstance(payload, bytes):
            payload = payload.decode("utf-8", errors="replace")

        row = {
            "t": self.now(),
            "wall": datetime.now().isoformat(timespec="milliseconds"),
            "dir": direction,
            "client": client,
            "topic": topic,
            "bytes": len(payload.encode("utf-8")),
            "payload": payload,
        }

        line = "[{:8.3f}] {} {:<14} {:<30} [{:>3} B] {}".format(
            row["t"], direction, client, topic, row["bytes"], payload)

        with self.lock:
            self.rows.append(row)
            self.csv.writerow([
                "{:.3f}".format(row["t"]), row["wall"], direction, client, topic, row["bytes"], payload])
            self.csv_file.flush()
            print(line, flush=True)
            self.log_file.write(line + "\n")
            self.log_file.flush()

    def snapshot(self):
        with self.lock:
            return list(self.rows)

    def close(self):
        self.csv_file.close()
        self.log_file.close()


# --------------------------------------------------------------------------- #
# Klien MQTT
# --------------------------------------------------------------------------- #

def create_paho_client(client_id):
    """Membuat klien yang kompatibel dengan paho-mqtt 1.x maupun 2.x."""
    try:
        return mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
    except AttributeError:
        return mqtt.Client(client_id=client_id)


class Actor:
    """Satu klien MQTT untuk satu peran (sink node virtual, cloud, robot, dll.)."""

    def __init__(self, name, args, recorder, subscriptions=(), on_message=None, record_rx=False):
        self.name = name
        self.recorder = recorder
        self.subscriptions = list(subscriptions)
        self.user_on_message = on_message
        self.record_rx = record_rx
        self.connected = threading.Event()
        self.client = create_paho_client("{}-{}".format(args.client_prefix, name))
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.host = args.broker
        self.port = args.port

        if args.username:
            self.client.username_pw_set(args.username, args.password)

    def start(self, timeout=10.0):
        self.client.connect(self.host, self.port, keepalive=60)
        self.client.loop_start()

        if not self.connected.wait(timeout):
            raise RuntimeError("Klien '{}' tidak mendapat CONNACK dari broker".format(self.name))

    def stop(self):
        self.client.loop_stop()
        self.client.disconnect()

    def publish(self, topic, payload):
        if not isinstance(payload, str):
            payload = json.dumps(payload, separators=(",", ":"))

        self.recorder.record("TX", self.name, topic, payload)
        self.client.publish(topic, payload.encode("utf-8"), qos=0, retain=False)
        return payload

    # paho 1.x: (client, userdata, flags, rc)
    # paho 2.x: (client, userdata, flags, reason_code, properties)
    def _on_connect(self, client, userdata, flags, rc, properties=None):
        code = getattr(rc, "value", rc)

        if code != 0:
            self.recorder.log("{} gagal konek ke broker | rc={}".format(self.name, rc))
            return

        for topic in self.subscriptions:
            client.subscribe(topic, qos=0)

        self.connected.set()

    def _on_message(self, client, userdata, message):
        payload = message.payload.decode("utf-8", errors="replace")

        if self.record_rx:
            self.recorder.record("RX", self.name, message.topic, payload)

        if self.user_on_message is not None:
            try:
                self.user_on_message(self, message.topic, payload)
            except Exception as exception:  # jangan sampai thread paho mati
                self.recorder.log("{} error saat memproses pesan | {}".format(self.name, exception))


# --------------------------------------------------------------------------- #
# Peran simulasi
# --------------------------------------------------------------------------- #

class VirtualSinkNode(threading.Thread):
    """Sink Node virtual yang mengirim telemetry/env sesuai format Sink Node asli."""

    def __init__(self, index, args, recorder, stop_event, rng):
        super().__init__(name="sink-{}".format(index), daemon=True)
        self.sink_id = "SN{:02d}".format(index)
        self.node_id = index
        self.interval = args.telemetry_interval
        self.duration = args.duration
        self.stop_event = stop_event
        self.rng = rng
        self.actor = Actor("sim-" + self.sink_id, args, recorder)
        self.sequence = 0
        self.sent = 0

        self.sm = rng.uniform(55.0, 75.0)
        self.st = rng.uniform(25.0, 29.0)
        self.at = rng.uniform(26.0, 31.0)
        self.ah = rng.uniform(65.0, 85.0)

    def _sample(self):
        self.sequence += 1
        self.sm = min(100.0, max(0.0, self.sm + self.rng.uniform(-0.8, 0.8)))
        self.st = self.st + self.rng.uniform(-0.2, 0.2)
        self.at = self.at + self.rng.uniform(-0.3, 0.3)
        self.ah = min(100.0, max(0.0, self.ah + self.rng.uniform(-1.0, 1.0)))

        return {
            "h_id": HOUSE_ID,
            "n_id": self.node_id,
            "seq": self.sequence,
            "ts": int(time.time()),
            "sm": round(self.sm, 1),
            "st": round(self.st, 1),
            "ec": self.rng.randint(900, 1500),
            "ph": round(self.rng.uniform(6.0, 7.0), 1),
            "n": self.rng.randint(30, 60),
            "p": self.rng.randint(10, 30),
            "k": self.rng.randint(25, 50),
            "at": round(self.at, 1),
            "ah": round(self.ah, 1),
            "lux": self.rng.randint(2000, 9000),
            "rssi": self.rng.randint(-110, -60),
            "sink": self.sink_id,
        }

    def run(self):
        deadline = time.monotonic() + self.duration
        # Jeda awal acak supaya keempat sink node tidak selalu tepat bersamaan.
        self.stop_event.wait(self.rng.uniform(0.0, 1.0))

        while not self.stop_event.is_set() and time.monotonic() < deadline:
            self.actor.publish(TOPIC_TELEMETRY, self._sample())
            self.sent += 1
            self.stop_event.wait(self.interval)


class RobotAgent:
    """Collaboration Agent: 1 robot yang menjalankan misi INSPECT per tray."""

    def __init__(self, args, recorder, rng):
        self.args = args
        self.recorder = recorder
        self.rng = rng
        self.robot_id = "RB01"
        self.battery = 92
        self.status_seq = 0
        self.result_seq = 0
        self.mission_started = threading.Event()
        self.mission_done = threading.Event()
        self.active_mission = None
        self.lock = threading.Lock()
        self.actor = Actor("robot-" + self.robot_id, args, recorder,
                           subscriptions=[TOPIC_AGENT_COMMAND], on_message=self._on_command)

    def _on_command(self, actor, topic, payload):
        try:
            command = json.loads(payload)
        except ValueError:
            self.recorder.log("Robot menerima payload bukan JSON, diabaikan")
            return

        if command.get("cmd") != "INSPECT":
            return

        with self.lock:
            if self.active_mission is not None:
                self.recorder.log("Robot sedang menjalankan misi, perintah baru diabaikan")
                return

            self.active_mission = command.get("m_id")

        trays = command.get("trays") or []
        threading.Thread(target=self._run_mission, args=(command.get("m_id"), trays), daemon=True).start()

    def _publish_status(self, mission_id, state, tray, percent):
        self.status_seq += 1
        self.actor.publish(TOPIC_AGENT_STATUS, {
            "m_id": mission_id,
            "r_id": self.robot_id,
            "st": state,
            "cur_tray": tray,
            "pct": percent,
            "bat": self.battery,
            "seq": self.status_seq,
            "ts": int(time.time()),
        })

    def _run_mission(self, mission_id, trays):
        self.mission_started.set()
        self.recorder.log("Robot {} mulai misi {} | trays={}".format(self.robot_id, mission_id, trays))
        total = max(1, len(trays))

        for index, tray in enumerate(trays):
            tray_start = time.monotonic()

            while time.monotonic() - tray_start < self.args.tray_time:
                progress = (time.monotonic() - tray_start) / self.args.tray_time
                percent = int(100 * (index + progress) / total)
                self._publish_status(mission_id, "IN_PROGRESS", tray, percent)
                time.sleep(self.args.status_interval)

            self.battery = max(0, self.battery - 1)
            label = self.rng.choice(["BAGUS", "BAGUS", "BAGUS", "BURUK"])
            self.result_seq += 1
            self.actor.publish(TOPIC_AGENT_RESULT, {
                "m_id": mission_id,
                "h_id": HOUSE_ID,
                "tray_id": tray,
                "label": label,
                "conf": round(self.rng.uniform(0.80, 0.99), 2),
                "anom": 1 if label == "BURUK" else 0,
                "seq": self.result_seq,
                "ts": int(time.time()),
            })

        self._publish_status(mission_id, "DONE", trays[-1] if trays else 0, 100)
        self.recorder.log("Robot {} selesai misi {}".format(self.robot_id, mission_id))

        with self.lock:
            self.active_mission = None

        self.mission_done.set()


class UnitKontrolSim:
    """Unit Kontrol simulasi: menjawab control/<aktuator> dengan status/<aktuator>."""

    def __init__(self, args, recorder):
        self.actor = Actor("unit-kontrol", args, recorder,
                           subscriptions=[TOPIC_UNIT_CONTROL_FILTER], on_message=self._on_control)

    def _on_control(self, actor, topic, payload):
        status_topic = topic.replace("/control/", "/status/", 1)
        value = payload.strip()

        def reply():
            time.sleep(0.2)
            actor.publish(status_topic, value)

        threading.Thread(target=reply, daemon=True).start()


# --------------------------------------------------------------------------- #
# Skenario
# --------------------------------------------------------------------------- #

def run_pump_tests(cloud, args, recorder, markers):
    """Uji 1: ON dengan dur_s (auto-OFF). Uji 2: ON manual lalu OFF manual."""
    recorder.log("=== Uji pompa 1: ON dengan dur_s={} (Sink Node harus mematikan sendiri) ===".format(args.pump_duration))
    markers["pump_auto_tx"] = recorder.now()
    cloud.publish(TOPIC_ACTUATOR_CONTROL, {
        "act_id": "PUMP", "action": "ON", "dur_s": args.pump_duration,
        "trig": "SOIL_M_LOW", "seq": 1, "ts": int(time.time()),
    })
    time.sleep(args.pump_duration + 5)

    recorder.log("=== Uji pompa 2: ON manual, {} s kemudian OFF manual ===".format(args.pump_manual_on))
    markers["pump_manual_on_tx"] = recorder.now()
    cloud.publish(TOPIC_ACTUATOR_CONTROL, {
        "act_id": "PUMP", "action": "ON", "dur_s": 0,
        "trig": "MANUAL_TEST", "seq": 2, "ts": int(time.time()),
    })
    time.sleep(args.pump_manual_on)

    markers["pump_manual_off_tx"] = recorder.now()
    cloud.publish(TOPIC_ACTUATOR_CONTROL, {
        "act_id": "PUMP", "action": "OFF", "dur_s": 0,
        "trig": "MANUAL_TEST", "seq": 3, "ts": int(time.time()),
    })
    time.sleep(3)
    markers["pump_done"] = recorder.now()


def run_scenario(args, recorder):
    rng = random.Random(args.seed)
    stop_event = threading.Event()
    markers = {}

    monitor = Actor("monitor", args, recorder, subscriptions=["gh01/#"], record_rx=True)
    cloud = Actor("cloud", args, recorder)
    robot = RobotAgent(args, recorder, rng)
    unit = UnitKontrolSim(args, recorder) if not args.no_unit_sim else None
    sinks = [VirtualSinkNode(i + 1, args, recorder, stop_event, rng) for i in range(args.sinks)]

    actors = [monitor, cloud, robot.actor] + ([unit.actor] if unit else []) + [s.actor for s in sinks]

    recorder.log("Menghubungkan {} klien ke {}:{} ...".format(len(actors), args.broker, args.port))

    for actor in actors:
        actor.start()

    recorder.log("Semua klien terhubung.")
    time.sleep(1.0)

    try:
        recorder.log("=== Mulai {} Sink Node virtual bersamaan (interval {} s, durasi {} s) ===".format(
            args.sinks, args.telemetry_interval, args.duration))

        for sink in sinks:
            sink.start()

        time.sleep(2.0)

        recorder.log("=== Uji penolakan: mission/command tanpa m_id ===")
        markers["invalid_tx"] = recorder.now()
        markers["invalid_payload"] = cloud.publish(TOPIC_MISSION_COMMAND, {
            "h_id": HOUSE_ID, "cmd": "INSPECT", "trays": [1], "note": "invalid-{}".format(int(time.time())),
        })
        time.sleep(3.0)

        mission_id = "M{}-01".format(datetime.now().strftime("%Y%m%d"))
        trays = list(range(1, args.trays + 1))
        recorder.log("=== Misi inspeksi {} oleh 1 robot | trays={} ===".format(mission_id, trays))
        markers["mission_id"] = mission_id
        markers["trays"] = trays
        markers["mission_tx"] = recorder.now()
        cloud.publish(TOPIC_MISSION_COMMAND, {
            "m_id": mission_id, "h_id": HOUSE_ID, "cmd": "INSPECT",
            "trays": trays, "crit_lvl": 1, "ts": int(time.time()),
        })

        pump_thread = threading.Thread(target=run_pump_tests, args=(cloud, args, recorder, markers), daemon=True)
        time.sleep(2.0)
        pump_thread.start()

        if not robot.mission_started.wait(10.0):
            recorder.log("PERINGATAN: robot tidak menerima perintah misi (Sink Node tidak me-relay?)")
        else:
            mission_timeout = args.trays * args.tray_time + 30
            if not robot.mission_done.wait(mission_timeout):
                recorder.log("PERINGATAN: misi tidak selesai dalam {} s".format(mission_timeout))

        pump_thread.join(args.pump_duration + args.pump_manual_on + 30)

        for sink in sinks:
            sink.join(args.duration + args.telemetry_interval + 5)

        recorder.log("Menunggu pesan terakhir ...")
        time.sleep(3.0)
    finally:
        stop_event.set()
        for actor in actors:
            try:
                actor.stop()
            except Exception:
                pass

    return markers, sinks


# --------------------------------------------------------------------------- #
# Analisis
# --------------------------------------------------------------------------- #

def stats(values):
    if not values:
        return None
    return {
        "n": len(values),
        "avg_ms": round(1000 * sum(values) / len(values), 1),
        "min_ms": round(1000 * min(values), 1),
        "max_ms": round(1000 * max(values), 1),
    }


def fmt_stats(s):
    if not s:
        return "-"
    return "avg {avg_ms} ms, min {min_ms} ms, max {max_ms} ms (n={n})".format(**s)


def relay_latencies(rows, source_topic, target_topic):
    """Pasangkan TX di topik sumber dengan RX monitor di topik tujuan (payload identik)."""
    sent = {}
    for row in rows:
        if row["dir"] == "TX" and row["topic"] == source_topic and row["payload"] not in sent:
            sent[row["payload"]] = row

    relayed = {}
    for row in rows:
        if row["dir"] == "RX" and row["client"] == "monitor" and row["topic"] == target_topic:
            source = sent.get(row["payload"])
            if source is not None and row["payload"] not in relayed and row["t"] >= source["t"]:
                relayed[row["payload"]] = row["t"] - source["t"]

    return sent, relayed


def evaluate(rows, markers, sinks, args):
    checks = []
    metrics = {}

    def add(name, status, detail):
        checks.append({"check": name, "status": status, "detail": detail})

    monitor_rx = [r for r in rows if r["dir"] == "RX" and r["client"] == "monitor"]

    # 1. Telemetri dari Sink Node virtual
    total_sent = 0
    total_received = 0
    telemetry_latency = []
    per_sink = {}

    for sink in sinks:
        tx = [r for r in rows if r["dir"] == "TX" and r["client"] == sink.actor.name]
        tx_payloads = {r["payload"]: r for r in tx}
        rx = [r for r in monitor_rx if r["topic"] == TOPIC_TELEMETRY and r["payload"] in tx_payloads]
        for r in rx:
            telemetry_latency.append(r["t"] - tx_payloads[r["payload"]]["t"])
        oversize = [r for r in tx if r["bytes"] > MAX_BYTES[TOPIC_TELEMETRY]]
        per_sink[sink.sink_id] = {"sent": len(tx), "received": len(rx), "oversize": len(oversize),
                                  "max_bytes": max([r["bytes"] for r in tx] or [0])}
        total_sent += len(tx)
        total_received += len(rx)

    loss = 0.0 if total_sent == 0 else 100.0 * (total_sent - total_received) / total_sent
    metrics["telemetry_virtual"] = {"per_sink": per_sink, "sent": total_sent, "received": total_received,
                                    "loss_pct": round(loss, 2), "latency": stats(telemetry_latency)}
    oversize_total = sum(v["oversize"] for v in per_sink.values())
    add("telemetry/env {} Sink Node virtual bersamaan".format(len(sinks)),
        PASS if total_sent > 0 and total_received == total_sent and oversize_total == 0 else FAIL,
        "terkirim {}, diterima {}, loss {:.1f}%, payload >192 B: {}, latensi broker {}".format(
            total_sent, total_received, loss, oversize_total, fmt_stats(stats(telemetry_latency))))

    # 2. Telemetri dari Sink Node asli (LoRa)
    device_telemetry = [r for r in monitor_rx if r["topic"] == TOPIC_TELEMETRY and '"sink"' not in r["payload"]]
    device_oversize = [r for r in device_telemetry if r["bytes"] > MAX_BYTES[TOPIC_TELEMETRY]]
    metrics["telemetry_device"] = {"received": len(device_telemetry),
                                   "max_bytes": max([r["bytes"] for r in device_telemetry] or [0])}
    add("telemetry/env dari Sink Node asli (LoRa)",
        FAIL if device_oversize else INFO,
        "{} paket diterima selama pengujian (bergantung pada node LoRa yang aktif), payload >192 B: {}".format(
            len(device_telemetry), len(device_oversize)))

    # 3. Penolakan mission/command tanpa m_id
    invalid_payload = markers.get("invalid_payload")
    leaked = [r for r in monitor_rx if r["topic"] == TOPIC_AGENT_COMMAND and r["payload"] == invalid_payload]
    add("mission/command tanpa m_id ditolak Sink Node",
        PASS if invalid_payload and not leaked else FAIL,
        "tidak diteruskan ke {}".format(TOPIC_AGENT_COMMAND) if not leaked else "ikut diteruskan (seharusnya ditolak)")

    # 4. Relay mission/command
    sent, relayed = relay_latencies(rows, TOPIC_MISSION_COMMAND, TOPIC_AGENT_COMMAND)
    valid_sent = {p: r for p, r in sent.items() if p != invalid_payload}
    command_latency = [relayed[p] for p in valid_sent if p in relayed]
    metrics["mission_command"] = {"sent": len(valid_sent), "relayed": len(command_latency),
                                  "latency": stats(command_latency)}
    add("mission/command Cloud -> Sink Node -> Agent",
        PASS if valid_sent and len(command_latency) == len(valid_sent) else FAIL,
        "diteruskan {}/{}, latensi {}".format(len(command_latency), len(valid_sent), fmt_stats(stats(command_latency))))

    # 5. Relay mission/status
    sent, relayed = relay_latencies(rows, TOPIC_AGENT_STATUS, TOPIC_MISSION_STATUS)
    status_latency = list(relayed.values())
    status_oversize = [p for p in sent if len(p.encode("utf-8")) > MAX_BYTES[TOPIC_MISSION_STATUS]]
    status_tx_times = sorted(r["t"] for r in sent.values())
    status_gaps = [b - a for a, b in zip(status_tx_times, status_tx_times[1:])]
    metrics["mission_status"] = {"sent": len(sent), "relayed": len(relayed), "latency": stats(status_latency),
                                 "oversize": len(status_oversize),
                                 "interval_avg_s": round(sum(status_gaps) / len(status_gaps), 2) if status_gaps else None}
    add("mission/status Agent -> Sink Node -> Cloud",
        PASS if sent and len(relayed) == len(sent) and not status_oversize else FAIL,
        "diteruskan {}/{}, payload >128 B: {}, interval rata-rata {} s, latensi {}".format(
            len(relayed), len(sent), len(status_oversize),
            metrics["mission_status"]["interval_avg_s"] if status_gaps else "-",
            fmt_stats(stats(status_latency))))

    # 6. Relay mission/result
    sent, relayed = relay_latencies(rows, TOPIC_AGENT_RESULT, TOPIC_MISSION_RESULT)
    result_latency = list(relayed.values())
    result_oversize = [p for p in sent if len(p.encode("utf-8")) > MAX_BYTES[TOPIC_MISSION_RESULT]]
    expected_results = len(markers.get("trays", []))
    metrics["mission_result"] = {"expected": expected_results, "sent": len(sent), "relayed": len(relayed),
                                 "latency": stats(result_latency), "oversize": len(result_oversize)}
    add("mission/result Agent -> Sink Node -> Cloud",
        PASS if len(sent) == expected_results and len(relayed) == len(sent) and not result_oversize else FAIL,
        "hasil tray {}/{} terkirim, diteruskan {}/{}, payload >256 B: {}, latensi {}".format(
            len(sent), expected_results, len(relayed), len(sent), len(result_oversize),
            fmt_stats(stats(result_latency))))

    # 7 & 8. Pompa
    pump_rx = [r for r in monitor_rx if r["topic"] == TOPIC_PUMP_CONTROL]

    def first_after(value, t_from, t_to=None):
        for r in pump_rx:
            if r["payload"].strip() == value and r["t"] >= t_from and (t_to is None or r["t"] <= t_to):
                return r
        return None

    pump_metrics = {}
    t_auto = markers.get("pump_auto_tx")
    t_manual_on = markers.get("pump_manual_on_tx")
    t_manual_off = markers.get("pump_manual_off_tx")

    if t_auto is not None:
        on = first_after("1", t_auto, t_manual_on)
        off = first_after("0", on["t"], t_manual_on) if on else None
        on_ms = round(1000 * (on["t"] - t_auto), 1) if on else None
        duration = round(off["t"] - on["t"], 2) if (on and off) else None
        pump_metrics["auto"] = {"on_latency_ms": on_ms, "measured_duration_s": duration,
                                "expected_duration_s": args.pump_duration}
        ok = on is not None and off is not None and abs(duration - args.pump_duration) <= args.pump_tolerance
        add("actuator/control PUMP ON dur_s={} (auto-OFF)".format(args.pump_duration),
            PASS if ok else FAIL,
            "ON {} | OFF otomatis setelah {} s (toleransi ±{} s)".format(
                "diterima dalam {} ms".format(on_ms) if on else "TIDAK diterima",
                duration if duration is not None else "-", args.pump_tolerance))
    else:
        add("actuator/control PUMP ON dur_s (auto-OFF)", FAIL, "uji tidak berjalan")

    if t_manual_on is not None and t_manual_off is not None:
        on = first_after("1", t_manual_on, t_manual_off)
        off = first_after("0", t_manual_off)
        on_ms = round(1000 * (on["t"] - t_manual_on), 1) if on else None
        off_ms = round(1000 * (off["t"] - t_manual_off), 1) if off else None
        pump_metrics["manual"] = {"on_latency_ms": on_ms, "off_latency_ms": off_ms}
        add("actuator/control PUMP ON lalu OFF manual",
            PASS if on and off else FAIL,
            "ON {} | OFF {}".format(
                "diterima dalam {} ms".format(on_ms) if on else "TIDAK diterima",
                "diterima dalam {} ms".format(off_ms) if off else "TIDAK diterima"))
    else:
        add("actuator/control PUMP ON lalu OFF manual", FAIL, "uji tidak berjalan")

    pump_status = [r for r in monitor_rx if r["topic"] == TOPIC_PUMP_STATUS]
    pump_metrics["status_messages"] = len(pump_status)
    metrics["pump"] = pump_metrics
    add("Status pompa dari Unit Kontrol",
        INFO, "{} pesan status/pump{}".format(
            len(pump_status), " (Unit Kontrol simulasi aktif)" if not args.no_unit_sim else ""))

    relay_ok = metrics["mission_command"]["relayed"] > 0 or metrics["mission_status"]["relayed"] > 0
    if not relay_ok and not pump_rx:
        add("Diagnosis", INFO,
            "Tidak ada satu pun pesan yang diteruskan Sink Node. Pastikan board terhubung ke broker {}:{} "
            "(cek log serial: 'MQTT broker connected').".format(args.broker, args.port))

    return checks, metrics


def write_summary(output_dir, args, checks, metrics, markers):
    summary = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "config": vars(args),
        "mission_id": markers.get("mission_id"),
        "checks": checks,
        "metrics": metrics,
        "result": FAIL if any(c["status"] == FAIL for c in checks) else PASS,
    }

    with open(os.path.join(output_dir, "summary.json"), "w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)

    lines = [
        "Ringkasan uji kontrak data kolaborasi (Tabel 4.6)",
        "Waktu   : {}".format(summary["generated_at"]),
        "Broker  : {}:{}".format(args.broker, args.port),
        "Skenario: {} Sink Node virtual, 1 robot ({} tray), uji pompa ON/OFF".format(args.sinks, args.trays),
        "",
    ]
    for check in checks:
        lines.append("[{}] {}".format(check["status"], check["check"]))
        lines.append("       {}".format(check["detail"]))
    lines.append("")
    lines.append("HASIL AKHIR: {}".format(summary["result"]))

    text = "\n".join(lines)
    with open(os.path.join(output_dir, "summary.txt"), "w", encoding="utf-8") as handle:
        handle.write(text + "\n")

    return text, summary["result"]


# --------------------------------------------------------------------------- #

def parse_args():
    parser = argparse.ArgumentParser(description="Uji skenario MQTT Sink Node SmartGreenhouse (Tabel 4.6)")
    parser.add_argument("--broker", required=True, help="IP/hostname broker MQTT (sama dengan BROKER_URI di firmware)")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--username", default=None)
    parser.add_argument("--password", default=None)
    parser.add_argument("--client-prefix", default="gh01test", help="awalan client id (jangan 'clientku')")
    parser.add_argument("--sinks", type=int, default=4, help="jumlah Sink Node virtual (default 4)")
    parser.add_argument("--telemetry-interval", type=float, default=10.0, help="interval telemetri per sink (s)")
    parser.add_argument("--duration", type=float, default=60.0, help="lama sink virtual mengirim telemetri (s)")
    parser.add_argument("--trays", type=int, default=4, help="jumlah tray yang diinspeksi robot")
    parser.add_argument("--tray-time", type=float, default=8.0, help="lama inspeksi per tray (s)")
    parser.add_argument("--status-interval", type=float, default=2.0, help="interval mission/status (s)")
    parser.add_argument("--pump-duration", type=int, default=10, help="dur_s untuk uji auto-OFF pompa")
    parser.add_argument("--pump-manual-on", type=float, default=5.0, help="lama pompa ON sebelum OFF manual (s)")
    parser.add_argument("--pump-tolerance", type=float, default=2.0, help="toleransi durasi auto-OFF (s)")
    parser.add_argument("--no-unit-sim", action="store_true", help="jangan simulasikan Unit Kontrol")
    parser.add_argument("--seed", type=int, default=42, help="seed angka acak (hasil bisa diulang)")
    parser.add_argument("--output", default="results", help="folder induk hasil")
    return parser.parse_args()


def main():
    args = parse_args()
    output_dir = os.path.join(args.output, datetime.now().strftime("%Y%m%d_%H%M%S"))
    os.makedirs(output_dir, exist_ok=True)
    recorder = Recorder(output_dir)

    try:
        markers, sinks = run_scenario(args, recorder)
    except (OSError, RuntimeError) as error:
        recorder.log("Gagal terhubung ke broker {}:{} | {}".format(args.broker, args.port, error))
        recorder.close()
        sys.exit(2)
    except KeyboardInterrupt:
        recorder.log("Dihentikan oleh pengguna.")
        recorder.close()
        sys.exit(130)

    checks, metrics = evaluate(recorder.snapshot(), markers, sinks, args)
    text, result = write_summary(output_dir, args, checks, metrics, markers)
    recorder.log("\n" + text)
    recorder.log("Hasil tersimpan di: {}".format(os.path.abspath(output_dir)))
    recorder.close()
    sys.exit(0 if result == PASS else 1)


if __name__ == "__main__":
    main()
