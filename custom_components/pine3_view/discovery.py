"""Builds the Pine3 View home model automatically from Home Assistant's registries.

Areas -> devices -> entities. Each device becomes one tile, driven by its most
useful ("primary") entity; the device's other entities become extras (power,
battery, humidity...) shown on that tile instead of cluttering the room.
"""

from __future__ import annotations

import re
import unicodedata
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers import area_registry as ar
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers import entity_registry as er

try:  # floors exist from HA 2024.4
    from homeassistant.helpers import floor_registry as fr
except ImportError:  # pragma: no cover
    fr = None

# Lower number = better candidate for a device's primary entity.
PRIMARY_RANK = {
    "climate": 0, "media_player": 1, "vacuum": 2, "cover": 3, "lock": 4, "light": 5,
    "fan": 6, "camera": 7, "switch": 8, "input_boolean": 9, "button": 10,
    "binary_sensor": 20, "sensor": 21,
}
SUPPORTED = set(PRIMARY_RANK) | {"scene", "script"}
ROOM_CLIMATE = {"temperature", "humidity"}
EXTRA_SENSOR_CLASSES = {"power", "energy", "battery", "temperature", "humidity", "illuminance", "pm25", "co2"}
SENSOR_PRIORITY = ["temperature", "humidity", "co2", "pm25", "illuminance", "power", "energy", "battery"]
BINARY_PRIORITY = ["door", "window", "garage_door", "opening", "motion", "occupancy", "presence", "moisture", "smoke", "gas"]

# Room icon from the area name (pt + en keywords, accent-insensitive).
ROOM_ICONS = [
    (("casa de banho", "wc", "banho", "bathroom", "lavabo"), "mdi:shower"),
    (("cozinha", "kitchen"), "mdi:silverware-fork-knife"),
    (("sala de jantar", "jantar", "dining"), "mdi:table-chair"),
    (("sala", "living", "estar"), "mdi:sofa"),
    (("quarto", "suite", "bedroom", "dormitorio"), "mdi:bed"),
    (("crianca", "bebe", "kids", "nursery", "filho", "filha"), "mdi:teddy-bear"),
    (("escritorio", "office", "estudo", "study"), "mdi:desk"),
    (("garagem", "garage"), "mdi:garage"),
    (("jardim", "garden", "quintal", "exterior", "outdoor", "patio"), "mdi:flower"),
    (("varanda", "terraco", "balcony", "terrace"), "mdi:balcony"),
    (("piscina", "pool"), "mdi:pool"),
    (("lavandaria", "laundry", "arrumos", "despensa"), "mdi:washing-machine"),
    (("entrada", "hall", "corredor", "hallway", "entry"), "mdi:door"),
    (("ginasio", "gym"), "mdi:dumbbell"),
    (("cave", "basement", "sotao", "attic"), "mdi:stairs"),
]


def _fold(text: str) -> str:
    text = unicodedata.normalize("NFKD", text or "")
    return "".join(c for c in text if not unicodedata.combining(c)).lower()


def room_icon(name: str, explicit: str | None) -> str:
    if explicit:
        return explicit
    folded = _fold(name)
    for words, icon in ROOM_ICONS:
        if any(re.search(rf"\b{re.escape(w)}\b", folded) for w in words):
            return icon
    return "mdi:home-outline"


def _strip_prefix(name: str, *prefixes: str | None) -> str:
    """'Sala Candeeiro de pé' in area 'Sala' -> 'Candeeiro de pé'."""
    for prefix in prefixes:
        if not prefix:
            continue
        if _fold(name).startswith(_fold(prefix) + " ") and len(name) > len(prefix) + 1:
            rest = name[len(prefix):].lstrip(" -–:")
            return rest[:1].upper() + rest[1:]
    return name


def _sensor_class(hass: HomeAssistant, entity_id: str, entry: er.RegistryEntry | None) -> str | None:
    state = hass.states.get(entity_id)
    if state and state.attributes.get("device_class"):
        return state.attributes["device_class"]
    if entry:
        return entry.device_class or entry.original_device_class
    return None


def _rank(hass: HomeAssistant, entity_id: str, entry: er.RegistryEntry | None) -> tuple:
    domain = entity_id.split(".")[0]
    base = PRIMARY_RANK.get(domain, 99)
    sub = 50
    dc = _sensor_class(hass, entity_id, entry)
    if domain == "sensor" and dc in SENSOR_PRIORITY:
        sub = SENSOR_PRIORITY.index(dc)
    elif domain == "binary_sensor" and dc in BINARY_PRIORITY:
        sub = BINARY_PRIORITY.index(dc)
    return (base, sub, entity_id)


def kind_of(hass: HomeAssistant, entity_id: str, entry: er.RegistryEntry | None) -> str:
    domain = entity_id.split(".")[0]
    dc = _sensor_class(hass, entity_id, entry)
    if domain == "switch" and dc == "outlet":
        return "outlet"
    if domain == "input_boolean":
        return "switch"
    return domain


def build_home(hass: HomeAssistant, settings: dict[str, Any]) -> dict[str, Any]:
    areas = ar.async_get(hass)
    devices = dr.async_get(hass)
    entities = er.async_get(hass)
    floors = fr.async_get(hass) if fr else None
    hidden = set(settings.get("hidden", []))

    # 1. Collect visible entities, grouped by (area, device).
    groups: dict[tuple[str, str], list[str]] = {}
    for entry in entities.entities.values():
        eid = entry.entity_id
        domain = eid.split(".")[0]
        if domain not in SUPPORTED or entry.disabled_by or entry.hidden_by or entry.entity_category:
            continue
        if hass.states.get(eid) is None:
            continue
        device = devices.async_get(entry.device_id) if entry.device_id else None
        if device and device.disabled_by:
            continue
        area_id = entry.area_id or (device.area_id if device else None) or "_none"
        groups.setdefault((area_id, entry.device_id or f"entity:{eid}"), []).append(eid)

    # Entities defined in YAML (no registry entry) only matter as scenes/scripts.
    scenes = sorted(
        s.entity_id for s in hass.states.async_all("scene")
        if not (e := entities.async_get(s.entity_id)) or not (e.hidden_by or e.disabled_by)
    )
    scripts = sorted(
        s.entity_id for s in hass.states.async_all("script")
        if not (e := entities.async_get(s.entity_id)) or not (e.hidden_by or e.disabled_by)
    )

    # 2. Turn each group into tiles.
    rooms: dict[str, dict[str, Any]] = {}
    for (area_id, device_key), eids in groups.items():
        room = rooms.setdefault(area_id, {"devices": [], "temperature": [], "humidity": [], "scenes": []})
        device = devices.async_get(device_key) if not device_key.startswith("entity:") else None
        area = areas.async_get_area(area_id) if area_id != "_none" else None

        scene_like = [e for e in eids if e.split(".")[0] in ("scene", "script")]
        room["scenes"].extend(e for e in scene_like if e.startswith("scene."))
        eids = [e for e in eids if e not in scene_like]
        if not eids:
            continue

        ordered = sorted(eids, key=lambda e: _rank(hass, e, entities.async_get(e)))
        # Room climate readings come from any temperature/humidity sensor.
        for e in ordered:
            if e.startswith("sensor.") and _sensor_class(hass, e, entities.async_get(e)) in ROOM_CLIMATE:
                room[_sensor_class(hass, e, entities.async_get(e))].append(e)

        controls = [e for e in ordered if PRIMARY_RANK.get(e.split(".")[0], 99) < 20]
        # A device with several independent controls (e.g. 2-gang switch) -> one tile each.
        primaries = controls if len({e.split(".")[0] for e in controls}) == 1 and len(controls) > 1 and controls[0].split(".")[0] in ("light", "switch", "cover") else ordered[:1]
        extras = [
            e for e in ordered if e not in primaries
            and e.split(".")[0] in ("sensor", "binary_sensor")
            and (_sensor_class(hass, e, entities.async_get(e)) in EXTRA_SENSOR_CLASSES or e.startswith("binary_sensor."))
        ][:3]

        dev_name = (device.name_by_user or device.name) if device else None
        for primary in primaries:
            state = hass.states.get(primary)
            name = state.attributes.get("friendly_name") or primary
            if len(primaries) == 1 and dev_name:
                name = dev_name
            name = _strip_prefix(name, area.name if area else None)
            room["devices"].append({
                "id": primary,
                "device_id": device.id if device else None,
                "name": name,
                "kind": kind_of(hass, primary, entities.async_get(primary)),
                "extras": extras,
                "hidden": primary in hidden,
            })

    # 3. Room metadata and ordering.
    floor_level = {}
    if floors:
        for f in floors.async_list_floors():
            floor_level[f.floor_id] = (f.level if f.level is not None else 0, f.name)
    order = {rid: i for i, rid in enumerate(settings.get("room_order", []))}
    hidden_rooms = set(settings.get("hidden_rooms", []))

    out = []
    for area_id, room in rooms.items():
        if not room["devices"] and not room["scenes"]:
            continue
        area = areas.async_get_area(area_id) if area_id != "_none" else None
        name = area.name if area else "Outros"
        floor_id = getattr(area, "floor_id", None) if area else None
        room["devices"].sort(key=lambda d: (PRIMARY_RANK.get(d["id"].split(".")[0], 99), _fold(d["name"])))
        out.append({
            "id": area_id,
            "name": name,
            "icon": room_icon(name, getattr(area, "icon", None) if area else None),
            "picture": getattr(area, "picture", None) if area else None,
            "floor": floor_level.get(floor_id, (None, None))[1],
            "floor_level": floor_level.get(floor_id, (0, None))[0] if floor_id else 0,
            "devices": room["devices"],
            "temperature": room["temperature"],
            "humidity": room["humidity"],
            "scenes": room["scenes"],
            "hidden": area_id in hidden_rooms,
            "_sort": (
                order.get(area_id, 10_000),
                1 if area_id == "_none" else 0,
                floor_level.get(floor_id, (0, ""))[0],
                _fold(name),
            ),
        })
    out.sort(key=lambda r: r.pop("_sort"))

    return {
        "rooms": out,
        "scenes": [s for s in scenes if s not in hidden],
        "scripts": [s for s in scripts if s not in hidden],
        "weather": next((s.entity_id for s in hass.states.async_all("weather")), None),
        "persons": [s.entity_id for s in hass.states.async_all("person")],
        "settings": settings,
    }
