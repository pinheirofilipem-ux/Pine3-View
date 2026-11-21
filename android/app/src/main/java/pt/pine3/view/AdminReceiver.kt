package pt.pine3.view

import android.app.admin.DeviceAdminReceiver

/** Necessário apenas para poder chamar DevicePolicyManager.lockNow() (desliga o ecrã a sério). */
class AdminReceiver : DeviceAdminReceiver()
