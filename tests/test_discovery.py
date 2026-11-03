"""Tests for the automatic home model."""

from homeassistant.core import HomeAssistant
from homeassistant.helpers import area_registry as ar
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.entity import EntityCategory
from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.pine3_view.websocket import async_setup_api
from custom_components.pine3_view.discovery import build_home, room_icon


async def _setup_home(hass: HomeAssistant):
    entry = MockConfigEntry(domain="test")
    entry.add_to_hass(hass)
    areas, devs, ents = ar.async_get(hass), dr.async_get(hass), er.async_get(hass)
    sala = areas.async_create("Sala")
    cozinha = areas.async_create("Cozinha")
    areas.async_create("Garagem")  # empty: must not appear

    def device(uid, name, area):
        d = devs.async_get_or_create(config_entry_id=entry.entry_id, identifiers={("test", uid)}, name=name)
        return devs.async_update_device(d.id, area_id=area.id)

    def entity(domain, uid, device, state, attrs=None, **kw):
        e = ents.async_get_or_create(domain, "test", uid, device_id=device.id if device else None, config_entry=entry, **kw)
        hass.states.async_set(e.entity_id, state, attrs or {})
        return e.entity_id

    lamp = device("lamp", "Sala Candeeiro de pé", sala)
    lamp_id = entity("light", "lamp", lamp, "on", {"friendly_name": "Sala Candeeiro de pé"})
    entity("sensor", "lamp_sig", lamp, "-60", {"device_class": "signal_strength"}, entity_category=EntityCategory.DIAGNOSTIC)

    plug = device("plug", "Máquina de café", cozinha)
    plug_id = entity("switch", "plug", plug, "on", {"device_class": "outlet"})
    power_id = entity("sensor", "plug_w", plug, "1200", {"device_class": "power", "unit_of_measurement": "W"})

    th = device("th", "Sensor Sala", sala)
    temp_id = entity("sensor", "th_t", th, "21.3", {"device_class": "temperature", "unit_of_measurement": "°C"})
    hum_id = entity("sensor", "th_h", th, "54", {"device_class": "humidity", "unit_of_measurement": "%"})

    relay = device("relay", "Relé 2 canais", sala)
    r1 = entity("light", "relay1", relay, "off", {"friendly_name": "Teto"})
    r2 = entity("light", "relay2", relay, "off", {"friendly_name": "Parede"})

    tv = device("tv", "Televisão", sala)
    tv_id = entity("media_player", "tv", tv, "playing")
    entity("switch", "tv_power", tv, "on")

    hidden_id = entity("light", "hid", device("hid", "Escondida", sala), "on", hidden_by=er.RegistryEntryHider.USER)
    loose = entity("switch", "loose", None, "off", {"friendly_name": "Bomba"})
    scene = entity("scene", "movie", None, "scening")
    ents.async_update_entity(scene, area_id=sala.id)
    hass.states.async_set("weather.casa", "sunny", {})
    return locals()


async def test_build_home(hass: HomeAssistant) -> None:
    h = await _setup_home(hass)
    home = build_home(hass, {"hidden": [], "hidden_rooms": [], "room_order": []})
    rooms = {r["name"]: r for r in home["rooms"]}

    assert list(rooms) == ["Cozinha", "Sala", "Outros"]  # alphabetical, "Outros" last, empty Garagem dropped
    assert rooms["Sala"]["icon"] == "mdi:sofa"
    assert rooms["Cozinha"]["icon"] == "mdi:silverware-fork-knife"

    sala = {d["id"]: d for d in rooms["Sala"]["devices"]}
    # area prefix stripped from the device name
    assert sala[h["lamp_id"]]["name"] == "Candeeiro de pé"
    # a 2-gang relay produces one tile per light, named by entity
    assert sala[h["r1"]]["name"] == "Teto" and sala[h["r2"]]["name"] == "Parede"
    # media player wins over its own power switch; one tile for the TV
    assert sala[h["tv_id"]]["name"] == "Televisão"
    assert not any(d["id"].startswith("switch.") for d in rooms["Sala"]["devices"])
    # temperature/humidity device becomes a single sensor tile and feeds the room climate
    assert sala[h["temp_id"]]["extras"] == [h["hum_id"]]
    assert rooms["Sala"]["temperature"] == [h["temp_id"]]
    assert rooms["Sala"]["humidity"] == [h["hum_id"]]
    # hidden and diagnostic entities never show up
    assert h["hidden_id"] not in sala
    assert all("sig" not in d for d in sala)
    # scenes assigned to a room are listed with it
    assert rooms["Sala"]["scenes"] == [h["scene"]]

    coz = rooms["Cozinha"]["devices"]
    assert coz == [{"id": h["plug_id"], "device_id": coz[0]["device_id"], "manufacturer": None, "model": None, "name": "Máquina de café",
                    "kind": "outlet", "extras": [h["power_id"]], "hidden": False}]
    assert [d["id"] for d in rooms["Outros"]["devices"]] == [h["loose"]]
    assert home["weather"] == "weather.casa"
    assert h["scene"] in home["scenes"]


async def test_settings_apply(hass: HomeAssistant) -> None:
    h = await _setup_home(hass)
    sala_id = ar.async_get(hass).async_get_area_by_name("Sala").id
    home = build_home(hass, {"hidden": [h["lamp_id"]], "hidden_rooms": [sala_id], "room_order": [sala_id]})
    assert home["rooms"][0]["name"] == "Sala" and home["rooms"][0]["hidden"] is True
    lamp = next(d for d in home["rooms"][0]["devices"] if d["id"] == h["lamp_id"])
    assert lamp["hidden"] is True


def test_room_icon() -> None:
    assert room_icon("Casa de Banho Suite", None) == "mdi:shower"
    assert room_icon("Quarto da Ana", None) == "mdi:bed"
    assert room_icon("Escritório", None) == "mdi:desk"
    assert room_icon("Sótão", None) == "mdi:stairs"
    assert room_icon("Sala", "mdi:star") == "mdi:star"
    assert room_icon("Xpto", None) == "mdi:home-outline"


async def test_websocket(hass: HomeAssistant, hass_ws_client) -> None:
    h = await _setup_home(hass)
    # The full entry also registers the sidebar panel (needs hass_frontend); test the API alone.
    await async_setup_api(hass)

    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_view/home"})
    msg = await ws.receive_json()
    assert msg["success"] and any(r["name"] == "Sala" for r in msg["result"]["rooms"])

    await ws.send_json({"id": 2, "type": "pine3_view/subscribe"})
    assert (await ws.receive_json())["success"]

    await ws.send_json({"id": 3, "type": "pine3_view/settings", "hidden": [h["lamp_id"]], "theme": "noite"})
    # the subscription pushes a fresh model, and the settings call returns
    got = {}
    for _ in range(2):
        m = await ws.receive_json()
        got[m["id"]] = m
    assert got[3]["result"]["theme"] == "noite"
    pushed = got[2]["event"]
    assert any(d["id"] == h["lamp_id"] and d["hidden"] for r in pushed["rooms"] for d in r["devices"])


async def test_settings_validation(hass: HomeAssistant, hass_ws_client) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_view/settings", "card_color": "#e6dcc2", "accent_color": "#4aa8ff",
                        "dock": ["home", "security"], "clock_24h": False})
    msg = await ws.receive_json()
    assert msg["success"]
    assert msg["result"]["accent_color"] == "#4aa8ff" and msg["result"]["dock"] == ["home", "security"]
    assert msg["result"]["show_scenes"] is True  # untouched defaults survive

    await ws.send_json({"id": 2, "type": "pine3_view/settings", "card_color": "red"})
    assert not (await ws.receive_json())["success"]
    await ws.send_json({"id": 3, "type": "pine3_view/settings", "dock": ["home", "nope"]})
    assert not (await ws.receive_json())["success"]

    await ws.send_json({"id": 4, "type": "pine3_view/home"})
    home = (await ws.receive_json())["result"]
    assert home["version"] and home["settings"]["card_color"] == "#e6dcc2"


async def test_v05_settings(hass: HomeAssistant, hass_ws_client) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_view/settings", "language": "en", "layout": "list", "screensaver": 5,
                        "weather_entity": "open-meteo", "big_rooms": ["sala"], "device_order": {"sala": ["light.a", "light.b"]},
                        "house_size": {"sala": 3}})
    msg = await ws.receive_json()
    assert msg["success"], msg
    res = msg["result"]
    assert res["language"] == "en" and res["layout"] == "list" and res["house_size"] == {"sala": 3}
    assert "house" in res["dock"]

    for bad_id, bad in enumerate([{"language": "fr"}, {"layout": "masonry"}, {"house_size": {"sala": 9}},
                                  {"weather_entity": "sensor.x"}, {"screensaver": -1}], start=2):
        await ws.send_json({"id": bad_id, "type": "pine3_view/settings", **bad})
        assert not (await ws.receive_json())["success"], bad


async def test_dock_migration(hass: HomeAssistant, hass_storage) -> None:
    hass_storage["pine3_view.settings"] = {"version": 1, "key": "pine3_view.settings",
                                           "data": {"dock": ["home", "scenes"], "theme": "noite"}}
    await async_setup_api(hass)
    s = hass.data["pine3_view"]["settings"]
    assert s["dock"] == ["home", "house", "scenes"]
    assert s["theme"] == "noite" and s["settings_version"] == 2


async def test_floor_level(hass: HomeAssistant) -> None:
    from homeassistant.helpers import floor_registry as fr

    h = await _setup_home(hass)
    floor = fr.async_get(hass).async_create("Primeiro andar", level=1)
    areas = ar.async_get(hass)
    sala = areas.async_get_area_by_name("Sala")
    areas.async_update(sala.id, floor_id=floor.floor_id)
    home = build_home(hass, {"hidden": [], "hidden_rooms": [], "room_order": []})
    rooms = {r["name"]: r for r in home["rooms"]}
    assert rooms["Sala"]["floor_level"] == 1 and rooms["Sala"]["floor"] == "Primeiro andar"
    assert rooms["Cozinha"]["floor_level"] == 0
    assert h["lamp_id"]


async def test_room_entities(hass: HomeAssistant) -> None:
    h = await _setup_home(hass)
    home = build_home(hass, {"hidden": [], "hidden_rooms": [], "room_order": []})
    sala = next(r for r in home["rooms"] if r["name"] == "Sala")
    ents = {e["id"]: e for e in sala["entities"]}
    # primaries, merged secondaries and diagnostic entities are all listed
    assert ents[h["lamp_id"]]["primary"] is True
    tv_switch = next(i for i in ents if i.startswith("switch."))
    assert ents[tv_switch]["primary"] is False
    sig = next(i for i in ents if "signal" in i or "sig" in i)
    assert ents[sig]["category"] is True
    assert ents[h["hidden_id"]]["ha_hidden"] is True
    # primaries first
    assert sala["entities"][0]["primary"] is True


async def test_layout_settings(hass: HomeAssistant, hass_ws_client) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_view/settings", "shown": ["switch.tv"],
                        "house_layout": {"sala": {"f": 0, "x": 0, "y": 0, "w": 5, "d": 4}}})
    msg = await ws.receive_json()
    assert msg["success"] and msg["result"]["house_layout"]["sala"]["w"] == 5
    await ws.send_json({"id": 2, "type": "pine3_view/settings", "house_layout": {"sala": {"f": 0, "x": 0, "y": 0, "w": 0, "d": 4}}})
    assert not (await ws.receive_json())["success"]


async def test_panel_setup_and_reload(hass: HomeAssistant) -> None:
    """The panel is served from a versioned path and the entry can be reloaded."""
    from unittest.mock import AsyncMock, patch

    from custom_components.pine3_view import async_setup_entry, async_unload_entry
    from custom_components.pine3_view.const import DOMAIN, VERSION

    entry = MockConfigEntry(domain=DOMAIN)
    entry.add_to_hass(hass)
    hass.http = AsyncMock()
    with patch("homeassistant.components.panel_custom.async_register_panel", new=AsyncMock()) as reg, \
         patch("homeassistant.components.frontend.async_remove_panel"):
        assert await async_setup_entry(hass, entry)
        assert await async_unload_entry(hass, entry)
        assert await async_setup_entry(hass, entry)
    assert reg.call_args.kwargs["module_url"] == f"/pine3_view_static/{VERSION}/pine3-view-panel.js"
    assert hass.http.async_register_static_paths.await_count == 1
    path = hass.http.async_register_static_paths.await_args.args[0][0]
    assert path.url_path == f"/pine3_view_static/{VERSION}"
