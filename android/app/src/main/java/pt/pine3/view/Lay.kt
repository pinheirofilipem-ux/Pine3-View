package pt.pine3.view

import org.json.JSONArray
import org.json.JSONObject

/** Ordem, esconder e tamanho duplo de divisões e dispositivos (definições do painel), mais o modo de edição com cópia de trabalho. */
object Lay {
    class Edit(
        val roomOrder: MutableList<String>, val hiddenRooms: MutableList<String>, val bigRooms: MutableList<String>,
        val hidden: MutableList<String>, val bigDevices: MutableList<String>, val deviceOrder: MutableMap<String, MutableList<String>>,
    )

    var edit: Edit? = null
    var version = 0
    var onChange: (() -> Unit)? = null
    fun changed() { version++; onChange?.invoke() }

    private fun list(s: JSONObject, k: String): MutableList<String> = (0 until (s.optJSONArray(k)?.length() ?: 0)).map { s.optJSONArray(k)!!.getString(it) }.toMutableList()

    fun begin(home: Home) {
        val s = home.settings
        val order = list(s, "room_order").ifEmpty { home.rooms.map { it.id }.toMutableList() }
        val dord = HashMap<String, MutableList<String>>()
        s.optJSONObject("device_order")?.let { o -> o.keys().forEach { k -> dord[k] = list(o, k) } }
        edit = Edit(order, list(s, "hidden_rooms"), list(s, "big_rooms"), list(s, "hidden"), list(s, "big_devices"), dord)
        version++
    }

    fun finish(home: Home?, save: Boolean) {
        val e = edit ?: return
        edit = null
        if (save && home != null) {
            val dord = JSONObject(); e.deviceOrder.forEach { (k, v) -> dord.put(k, JSONArray(v)) }
            Ha.saveSettings(JSONObject().put("room_order", JSONArray(e.roomOrder)).put("hidden_rooms", JSONArray(e.hiddenRooms)).put("big_rooms", JSONArray(e.bigRooms))
                .put("hidden", JSONArray(e.hidden)).put("big_devices", JSONArray(e.bigDevices)).put("device_order", dord))
        }
        version++
    }

    private fun hiddenRooms(home: Home) = edit?.hiddenRooms ?: list(home.settings, "hidden_rooms")
    private fun hiddenDevs(home: Home) = edit?.hidden ?: list(home.settings, "hidden")
    fun roomOrder(home: Home): List<String> = edit?.roomOrder ?: list(home.settings, "room_order")

    /** Divisões visíveis, pela ordem escolhida. */
    fun rooms(home: Home): List<Room> {
        val hide = hiddenRooms(home); val order = roomOrder(home)
        return home.rooms.filter { it.id !in hide }.sortedBy { r -> order.indexOf(r.id).let { if (it < 0) 10_000 else it } }
    }

    fun hiddenRoomCount(home: Home) = home.rooms.count { it.id in hiddenRooms(home) }

    /** Dispositivos visíveis de uma divisão, pela ordem escolhida. */
    fun devs(home: Home, room: Room): List<Dev> {
        val hide = hiddenDevs(home)
        val ord = edit?.deviceOrder?.get(room.id) ?: home.settings.optJSONObject("device_order")?.let { list(it, room.id) } ?: emptyList()
        return room.devices.filter { it.id !in hide }.sortedBy { d -> ord.indexOf(d.id).let { if (it < 0) 10_000 else it } }
    }

    fun hiddenDevCount(home: Home, room: Room) = room.devices.count { it.id in hiddenDevs(home) }

    fun isBigRoom(home: Home, id: String) = id in (edit?.bigRooms ?: list(home.settings, "big_rooms"))
    fun isBigDev(home: Home, id: String) = id in (edit?.bigDevices ?: list(home.settings, "big_devices"))

    fun hide(kind: String, key: String) { val e = edit ?: return; if (kind == "room") { if (key !in e.hiddenRooms) e.hiddenRooms += key } else if (key !in e.hidden) e.hidden += key; changed() }
    fun toggleBig(kind: String, key: String) { val e = edit ?: return; val l = if (kind == "room") e.bigRooms else e.bigDevices; if (!l.remove(key)) l += key; changed() }
    fun restoreRooms() { edit?.hiddenRooms?.clear(); changed() }
    fun restoreDevs(room: Room) { val e = edit ?: return; e.hidden.removeAll(room.devices.map { it.id }.toSet()); changed() }

    /** A grelha de divisões foi reordenada: as chaves visíveis ficam à frente, o resto mantém a ordem. */
    fun reorderRooms(keys: List<String>) { val e = edit ?: return; val rest = e.roomOrder.filter { it !in keys }; e.roomOrder.clear(); e.roomOrder.addAll(keys + rest); changed() }

    fun reorderDevs(room: Room, keys: List<String>) {
        val e = edit ?: return
        val full = room.devices.map { it.id }
        val rest = full.filter { it !in keys }
        e.deviceOrder[room.id] = (keys + rest).toMutableList()
        changed()
    }
}
