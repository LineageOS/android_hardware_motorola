# MbnOverride

Selects a modem carrier config (MBN) per SIM, for networks where the
config picked by Motorola's firmware does not enable IMS/VoLTE.

## Why this exists

The modem chooses a carrier config on its own, from the configs
shipped in `/vendor/fsg`, based on the SIM. A SIM that matches none of
the bundled operators falls back to a generic config
(`EMEA_APAC_SUPER` on rtwo). On some networks that generic config does
not bring IMS up: IMS requests sent to the modem fail, no IMS service
is enabled and VoLTE is unavailable, even though the network advertises
VoPS. Another config already present in the modem often works.

Example (the reason this was written): Irancell (MCC/MNC 432/35) on
rtwo. With `EMEA_APAC_SUPER` IMS never registers; with `DU_ARE_VOLTE`
it registers and VoLTE calls stay on LTE.

## What it does (and does not do)

* It maps a SIM's MCC/MNC to the **name** of a config that already
  exists in the modem and applies it, one slot at a time.
* SIMs with the same MCC/MNC receive the same configured MBN.
* With different operators, only SIMs listed in the allowlist are
  overridden; others retain the modem's stock-selected config.
* Both SIMs can use the same or different MBNs simultaneously,
  without interfering with each other.
* It does not add, modify or write any MBN, NV or EFS data, and it
  does not touch files in `/vendor`.
* It does nothing for SIMs without an entry in the allowlist. Those
  keep the stock behaviour.
* It does not use `qcRilDisableMbnAutoSel`. That call cannot be undone
  and would stop the modem from choosing configs for other SIMs.

## How it works

1. The service starts at boot and binds to `QcrilMsgTunnel` through
   `QcRilHook` (`qcrilhook.jar`, permission
   `com.qualcomm.permission.USE_QCRIL_MSG_TUNNEL`, granted through
   privapp-permissions).
2. When `QcRilHook` is ready and whenever the subscriptions change, it
   reads the SIM operator (MCC+MNC) of every active slot and looks it
   up in `res/xml/mbn_overrides.xml`.
3. If at least one slot has an entry, it waits until PDC answers
   `qcRilGetConfig` (up to 3 minutes), asks the modem for its configs
   (`qcRilGetAllConfigs`, answered by the broadcast
   `ACTION_PDC_CONFIG_LIST_RECEIVED`) and resolves each config's name
   with `qcRilGetMetaInfoForConfig`. Config IDs are the SHA-1 of the
   MBN file, so they are never hardcoded and survive firmware updates.
4. For each slot whose active config differs from the wanted one:
   * `qcRilSelectConfig(id, 1 << slot)`
   * wait for `ACTION_PDC_DATA_RECEIVED` with `error=0`
   * `qcRilActivateConfig(1 << slot)`
   * poll `qcRilGetConfig(slot)` until it reports the wanted config
   Select/activate are done per slot; a combined mask `3` only
   affected the first slot in testing.
5. Nothing is done during a call. Attempts are limited to three per
   slot per ten minutes, so a modem that keeps reverting cannot cause a
   loop.
6. The selection does not survive a reboot (the modem selects its own
   default again), so the service reapplies it every time `QcRilHook`
   becomes ready. Applying it when the slot is already on the wanted
   config is a no-op.

## Configuration

`res/values/config.xml`:

```xml
<bool name="config_mbnOverrideEnabled">true</bool>
```

`true` runs the service. It only acts on SIMs listed in the allowlist.
`false` stops the service completely (kill switch).

`res/xml/mbn_overrides.xml` is the allowlist:

```xml
<mbn-overrides>
    <override mcc="432" mnc="35" name="DU_ARE_VOLTE"/>  <!-- Irancell -->
    <override mcc="432" mnc="11" name="DU_ARE_VOLTE"/>  <!-- Hamrah-e-Aval -->
</mbn-overrides>
```

* `name` is the config name reported by the modem (see below).
* `mnc` is optional. Without it the entry matches the whole MCC.
* Write `mnc` exactly as it appears in the SIM operator string,
  including leading zeros (`01`, not `1`).
* Only add entries that were tested with a real SIM.

## Listing the configs available in your modem

The list depends on the firmware, so read it from your own device:

```
adb root
adb shell setprop persist.log.tag.MbnOverride D
adb reboot
# wait about a minute after boot
adb logcat -d -s MbnOverride | sed -n 's/.*config: //p' | sort -u
```

`persist.log.tag.MbnOverride` survives reboots; remove it when done:

```
adb shell setprop persist.log.tag.MbnOverride ""
```

Names usually match the files in `/vendor/fsg` (upper case, without
`.mbn`), but the log is authoritative. A list taken from rtwo
(XT2301-5, firmware W1TRS36H.56-13-1-3) is in
`modem-configs-rtwo-W1TRS36H.56-13-1-3.txt`. It is only a reference: a
config being present does not mean it suits your SIM.

## Finding a config for your SIM

1. Look at your own operator code: `adb shell getprop gsm.sim.operator.numeric`
   (one value per slot).
2. Start with configs of operators in the same country or region and
   with `VOLTE` or `IMS` in the name.
3. A config also carries that operator's band and IMS settings, so
   results vary. Test each candidate, one at a time.

## Testing an entry

1. Add the entry, build, install, reboot.
2. Watch the service:

       adb logcat -s MbnOverride

   Expected, per slot: `slot=N sim=<mccmnc> -> <NAME>`, `select ok`,
   `activate=true`, `slot N now active: <NAME>`. On the next run:
   `already on <NAME>`.
3. Check IMS: Settings > About phone > SIM status > IMS registration,
   for each slot.
4. Place a call from each SIM. It must stay on LTE (no fallback to
   3G/2G) and audio must work both ways.
5. Check that mobile data and SMS still work.
6. Reboot at least three times; the same lines must appear each time.
7. Toggle airplane mode; remove and reinsert a SIM.
8. Negative test: with a SIM that has no entry, the log must show the
   SIM and no select/activate lines.
9. Mixed setup, if you can: one SIM with an entry and one without. The
   slot without an entry must keep behaving as stock (the log shows it
   as `(stock)` and no select/activate lines for it).

## Submitting an entry

Open a change that adds one line to `mbn_overrides.xml`. In the commit
message give the device, firmware build, operator name, MCC/MNC and
what you checked (steps 3 to 5 above). Do not include IMSI, ICCID,
phone numbers or full logs.

## Known limitations

* Applying a config can interrupt data/IMS for a few seconds.
* Whether a given config works with a given SIM can only be found by
  testing it.
* `sub_id` in `ACTION_PDC_DATA_RECEIVED` was `0` for selects on both
  slots (requests are logged on OEM hook instance 0 for both slots),
  so it does not identify the slot. The service only uses `error` from
  that broadcast and confirms the result by reading the active config
  of each slot with `qcRilGetConfig`.

## Turning it off

Set `config_mbnOverrideEnabled` to `false`, or on a running device:

```
adb shell pm disable-user --user 0 org.lineageos.motorola.mbnoverride
adb reboot
```

After the reboot the modem selects its default config again.
