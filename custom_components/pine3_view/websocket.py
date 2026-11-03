"""Websocket API and registry listeners for Pine3 View."""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers.event import async_call_later
from homeassistant.helpers.storage import Store

from .const import DOMAIN, EVENT_UPDATED
from .discovery import build_home

STORAGE_KEY = f"{DOMAIN}.settings"
DEFAULT_SETTINGS: dict[str, Any] = {"hidden": [], "hidden_rooms": [], "room_order": [], "theme": "ameixa"}
REGISTRY_EVENTS = (
    "area_registry_updated",
    "device_registry_updated",
    "entity_registry_updated",
    "floor_registry_updated",
)


async def async_setup_api(hass: HomeAssistant) -> None:
    store: Store = Store(hass, 1, STORAGE_KEY)
    settings = {**DEFAULT_SETTINGS, **(await store.async_load() or {})}
    hass.data[DOMAIN] = {"store": store, "settings": settings, "unsubs": []}

    websocket_api.async_register_command(hass, ws_home)
    websocket_api.async_register_command(hass, ws_settings)
    websocket_api.async_register_command(hass, ws_subscribe)

    pending: list = []

    @callback
    def _fire(_now=None) -> None:
        pending.clear()
        hass.bus.async_fire(EVENT_UPDATED)

    @callback
    def _changed(_event: Event) -> None:
        # Registries change in bursts (e.g. a new integration): debounce.
        if not pending:
            pending.append(async_call_later(hass, 2, _fire))

    for ev in REGISTRY_EVENTS:
        hass.data[DOMAIN]["unsubs"].append(hass.bus.async_listen(ev, _changed))


@callback
def async_unload_api(hass: HomeAssistant) -> None:
    for unsub in hass.data.get(DOMAIN, {}).get("unsubs", []):
        unsub()
    hass.data.pop(DOMAIN, None)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/home"})
@callback
def ws_home(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    connection.send_result(msg["id"], build_home(hass, hass.data[DOMAIN]["settings"]))


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/subscribe"})
@callback
def ws_subscribe(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Push a fresh home model whenever the registries or settings change."""

    @callback
    def _forward(_event: Event) -> None:
        if DOMAIN in hass.data:
            connection.send_message(
                websocket_api.event_message(msg["id"], build_home(hass, hass.data[DOMAIN]["settings"]))
            )

    connection.subscriptions[msg["id"]] = hass.bus.async_listen(EVENT_UPDATED, _forward)
    connection.send_result(msg["id"])


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/settings",
        vol.Optional("hidden"): [str],
        vol.Optional("hidden_rooms"): [str],
        vol.Optional("room_order"): [str],
        vol.Optional("theme"): str,
    }
)
@websocket_api.async_response
async def ws_settings(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    data = hass.data[DOMAIN]
    for key in DEFAULT_SETTINGS:
        if key in msg:
            data["settings"][key] = msg[key]
    await data["store"].async_save(data["settings"])
    hass.bus.async_fire(EVENT_UPDATED)
    connection.send_result(msg["id"], data["settings"])
