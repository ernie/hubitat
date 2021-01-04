/**
 * Copyright 2020 Ernie Miller
 *
 * Modified from "Leviton Decora Z-Wave Plus Dimmer" driver by Jason Xia
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
metadata {
  definition (name: "Leviton Decora Smart Z-Wave Dimmer", namespace: "ernie", author: "Ernie Miller", ocfDeviceType: "oic.d.light") {
    capability "Actuator"
    capability "Configuration"
    capability "Indicator"
    capability "Light"
    capability "Refresh"
    capability "Switch"
    capability "Switch Level"

    attribute "loadType", "enum", ["Incandescent", "LED", "CFL"]
    attribute "presetLevel", "number"
    attribute "minLevel", "number"
    attribute "maxLevel", "number"
    attribute "fadeOnTime", "number"
    attribute "fadeOffTime", "number"
    attribute "levelIndicatorTimeout", "number"
    attribute "firmwareVersion", "string"

    fingerprint mfr:"001D", prod:"3201", deviceId:"0001", inClusters:"0x5E,0x85,0x59,0x86,0x72,0x70,0x5A,0x73,0x26,0x20,0x27,0x2C,0x2B,0x7A", outClusters:"0x82", deviceJoinName: "Leviton DZ6HD Z-Wave Dimmer"
    fingerprint mfr:"001D", prod:"3301", deviceId:"0001", inClusters:"0x5E,0x85,0x59,0x86,0x72,0x70,0x5A,0x73,0x26,0x20,0x27,0x2C,0x2B,0x7A", outClusters:"0x82", deviceJoinName: "Leviton DZ1KD Z-Wave Dimmer"
  }

  preferences {
    input name: "loadType", type: "enum", title: "Load type",
        options: ["Incandescent (default)", "LED", "CFL"],
        displayDuringSetup: false, required: false
    input name: "indicatorStatus", type: "enum", title: "Indicator LED is lit",
        options: ["When switch is off (default)", "When switch is on", "Never"],
        displayDuringSetup: false, required: false
    input name: "presetLevel", type: "number", title: "Light turns on to level",
        description: "0 to 100 (default 0 = last level)", range: "0..100",
        displayDuringSetup: false, required: false
    input name: "minLevel", type: "number", title: "Minimum light level",
        description: "0 to 100 (default 10)", range: "0..100",
        displayDuringSetup: false, required: false
    input name: "maxLevel", type: "number", title: "Maximum light level",
        description: "0 to 100 (default 100)", range: "0..100",
        displayDuringSetup: false, required: false
    input name: "fadeOnTime", type: "number", title: "Fade-on time",
        description: "0 to 253 (default 2)<br>0 = instant on<br>1 - 127 = 1 - 127 seconds<br>128 - 253 = 1 - 126 minutes", range: "0..253",
        displayDuringSetup: false, required: false
    input name: "fadeOffTime", type: "number", title: "Fade-off time",
        description: "0 to 253 (default 2)<br>0 = instant off<br>1 - 127 = 1 - 127 seconds<br>128 - 253 = 1 - 126 minutes", range: "0..253",
        displayDuringSetup: false, required: false
    input name: "levelIndicatorTimeout", type: "number", title: "Dim level indicator timeout",
        description: "0 to 255 (default 3)", range: "0..255",
        displayDuringSetup: false, required: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
  }
}

def installed() {
  log.debug "installed..."
  refresh()
}

def updated() {
  if (state.lastUpdatedAt != null && state.lastUpdatedAt >= now() - 1000) {
    log.debug "ignoring double updated"
    return
  }
  log.debug "updated..."
  state.lastUpdatedAt = now()
  configure()
}

def configure() {
  def commands = []
  if (loadType != null) {
    commands.addAll(setLoadType(loadType))
  }
  if (indicatorStatus != null) {
    commands.addAll(setIndicatorStatus(indicatorStatus))
  }
  if (presetLevel != null) {
    commands.addAll(setPresetLevel(presetLevel as short))
  }
  if (minLevel != null) {
    commands.addAll(setMinLevel(minLevel as short))
  }
  if (maxLevel != null) {
    commands.addAll(setMaxLevel(maxLevel as short))
  }
  if (fadeOnTime != null) {
    commands.addAll(setFadeOnTime(fadeOnTime as short))
  }
  if (fadeOffTime != null) {
    commands.addAll(setFadeOffTime(fadeOffTime as short))
  }
  if (levelIndicatorTimeout != null) {
    commands.addAll(setLevelIndicatorTimeout(levelIndicatorTimeout as short))
  }
  if (logEnable) log.debug "Configuring with commands $commands"
  commands
}

def parse(String description) {
  def result = null
  def cmd = zwave.parse(description, [0x20: 1, 0x25:1, 0x26: 1, 0x70: 1, 0x72: 2])
  if (cmd) {
    result = zwaveEvent(cmd)
    if (logEnable) log.debug "Parsed $cmd to $result"
  } else {
    log.debug "Non-parsed event: $description"
  }
  result
}

def on() {
  state.lastDigital = now()
  def fadeOnTime = device.currentValue("fadeOnTime")
  def presetLevel = device.currentValue("presetLevel")
  short duration = fadeOnTime == null ? 2 : durationToSeconds(fadeOnTime.shortValue())
  short level = presetLevel == null || presetLevel == 0 ? 0xFF : toZwaveLevel(presetLevel as short)
  if (level != 0xFF) {
    def displayLevel = toDisplayLevel(level)
    if (device.currentValue("level") != displayLevel) {
      sendEvent(name: "level", value: displayLevel, unit: "%", descriptionText: "Level set to $displayLevel% [$eventType]", type: eventType)
    }
  }
  if (device.currentValue("switch") != "on") {
    sendEvent(name: "switch", value: "on", descriptionText: "Switch turned on [$eventType]", type: eventType)
  }
  delayBetween([
      zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: duration).format(),
      zwave.switchMultilevelV1.switchMultilevelGet().format()
  ], commandDelayMs)
}

def off() {
  state.lastDigital = now()
  def fadeOffTime = device.currentValue("fadeOffTime")
  short duration = fadeOffTime == null ? 2 : durationToSeconds(fadeOffTime.shortValue())
  if (device.currentValue("level") != 0) {
    sendEvent(name: "level", value: 0, unit: "%", descriptionText: "Level set to 0% [$eventType]", type: eventType)
  }
  if (device.currentValue("switch") != "off") {
    sendEvent(name: "switch", value: "off", descriptionText: "Switch turned off [$eventType]", type: eventType)
  }
  delayBetween([
      zwave.switchMultilevelV2.switchMultilevelSet(value: 0x00, dimmingDuration: duration).format(),
      zwave.switchMultilevelV1.switchMultilevelGet().format()
  ], commandDelayMs)
}

def setLevel(value, duration = null) {
  state.lastDigital = now()
  if (logEnable) log.debug "setLevel: $value"

  short level = toDisplayLevel(value as short)
  short dimmingDuration = duration == null ? 2 : duration
  String switchState = level == 0 ? "off" : "on"

  if (level != device.currentValue("level")) {
    sendEvent(name: "level", value: level, unit: "%", descriptionText: "Level set to $level% [$eventType]", type: eventType)
  }
  if (switchState != device.currentValue("switch")) {
    sendEvent(name: "switch", value: switchState, descriptionText: "Switch turned $switchState [$eventType]", type: eventType)
  }
  delayBetween([
      zwave.switchMultilevelV2.switchMultilevelSet(value: toZwaveLevel(level), dimmingDuration: dimmingDuration).format(),
      zwave.switchMultilevelV1.switchMultilevelGet().format()
  ], commandDelayMs)
}

def refresh() {
  def commands = statusCommands
  commands << zwave.versionV1.versionGet().format()
  for (i in 1..8) {
    commands << zwave.configurationV1.configurationGet(parameterNumber: i).format()
  }
  log.debug "Refreshing with commands $commands"
  delayBetween(commands, commandDelayMs)
}

def indicatorNever() {
  sendEvent(name: "indicatorStatus", value: "never", descriptionText: "indicatorStatus set to \"never\"")
  configurationCommand(7, 0)
}

def indicatorWhenOff() {
  sendEvent(name: "indicatorStatus", value: "when off", descriptionText: "indicatorStatus set to \"when off\"")
  configurationCommand(7, 255)
}

def indicatorWhenOn() {
  sendEvent(name: "indicatorStatus", value: "when on", descriptionText: "indicatorStatus set to \"when on\"")
  configurationCommand(7, 254)
}

private static int getCommandDelayMs() { 500 }

private zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  levelEvent(cmd.value)
}

private zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
  levelEvent(cmd.value)
}

private zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
  response(zwave.switchMultilevelV1.switchMultilevelGet())
}

private zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  if (cmd.value == 0) {
    switchEvent(false)
  } else if (cmd.value == 255) {
    switchEvent(true)
  } else {
    log.debug "Bad switch value $cmd.value"
  }
}

private zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  def result = null
  switch (cmd.parameterNumber) {
    case 1:
      result = createEvent(name: "fadeOnTime", value: cmd.configurationValue[0])
      break
    case 2:
      result = createEvent(name: "fadeOffTime", value: cmd.configurationValue[0])
      break
    case 3:
      result = createEvent(name: "minLevel", value: cmd.configurationValue[0])
      break
    case 4:
      result = createEvent(name: "maxLevel", value: cmd.configurationValue[0])
      break
    case 5:
      result = createEvent(name: "presetLevel", value: cmd.configurationValue[0])
      break
    case 6:
      result = createEvent(name: "levelIndicatorTimeout", value: cmd.configurationValue[0])
      break
    case 7:
      def value = null
      switch (cmd.configurationValue[0]) {
        case 0: value = "never"; break
        case 254: value = "when on"; break
        case 255: value = "when off"; break
      }
      result = createEvent(name: "indicatorStatus", value: value)
      break
    case 8:
      def value = null
      switch (cmd.configurationValue[0]) {
        case 0: value = "Incandescent"; break
        case 1: value = "LED"; break
        case 2: value = "CFL"; break
      }
      result = createEvent(name: "loadType", value: value)
      break
  }
  result
}

private zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
  if (logEnable) log.debug "Hail received. Getting updated level."
  response(zwave.switchMultilevelV1.switchMultilevelGet())
}

private zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  createEvent(name: "firmwareVersion", value: "${cmd.firmware0Version}.${cmd.firmware0SubVersion}", displayed: false)
}

private zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "Unhandled zwave command $cmd"
}

private levelEvent(short level) {
  def result = []
  def displayLevel = toDisplayLevel(level)
  def switchState = level == 0 ? "off" : "on"
  if (level >= 0 && level <= 100) {
    if (displayLevel != device.currentValue("level")) {
      result << createEvent(name: "level", value: displayLevel, unit: "%", descriptionText: "Level set to $displayLevel% [$eventType]", type: eventType)
    }
    if (switchState != device.currentValue("switch")) {
      result << switchEvent(switchState == "on")
    }
  } else {
    if (logEnable) log.debug "Bad level $level"
  }
  result
}

private switchEvent(boolean on) {
  def switchState = on ? "on" : "off"
  createEvent(name: "switch", value: switchState, descriptionText: "Switch turned $switchState [$eventType]", type: eventType)
}

private getStatusCommands() {
  [zwave.switchMultilevelV1.switchMultilevelGet().format()]
}

private short toDisplayLevel(short level) {
  level = Math.max(0, Math.min(100, level))
  (level == (short) 99) ? 100 : level
}

private short toZwaveLevel(short level) {
  Math.max(0, Math.min(99, level))
}

private int durationToSeconds(short duration) {
  if (duration >= 0 && duration <= 127) {
    duration
  } else if (duration >= 128 && duration <= 254) {
    (duration - 127) * 60
  } else if (duration == 255) {
    2   // factory default
  } else {
    log.error "Bad duration $duration"
    0
  }
}

private short secondsToDuration(int seconds) {
  if (seconds >= 0 && seconds <= 127) {
    seconds
  } else if (seconds >= 128 && seconds <= 127 * 60) {
    127 + Math.round(seconds / 60)
  } else {
    log.error "Bad seconds $seconds"
    255
  }
}

private getEventType() {
  (state.lastDigital && state.lastDigital > now() - 1000) ?
    "digital" :
    "physical"
}

private configurationCommand(param, value) {
  param = param as short
  value = value as short
  delayBetween([
      zwave.configurationV1.configurationSet(parameterNumber: param, configurationValue: [value]).format(),
      zwave.configurationV1.configurationGet(parameterNumber: param).format()
  ], commandDelayMs)
}

private setFadeOnTime(short time) {
  sendEvent(name: "fadeOnTime", value: time, descriptionText: "fadeOnTime set to $time")
  configurationCommand(1, time)
}

private setFadeOffTime(short time) {
  sendEvent(name: "fadeOffTime", value: time, descriptionText: "fadeOffTime set to $time")
  configurationCommand(2, time)
}

private setMinLevel(short level) {
  sendEvent(name: "minLevel", value: level, descriptionText: "minLevel set to $level")
  configurationCommand(3, level)
}

private setMaxLevel(short level) {
  sendEvent(name: "maxLevel", value: level, descriptionText: "maxLevel set to $level")
  configurationCommand(4, level)
}

private setPresetLevel(short level) {
  sendEvent(name: "presetLevel", value: level, descriptionText: "presetLevel set to $level")
  configurationCommand(5, level)
}

private setLevelIndicatorTimeout(short timeout) {
  sendEvent(name: "levelIndicatorTimeout", value: timeout, descriptionText: "levelIndicatorTimeout set to $timeout")
  configurationCommand(6, timeout)
}

private setLoadType(String loadType) {
  switch (loadType) {
    case "Incandescent (default)":
      sendEvent(name: "loadType", value: "Incandescent", descriptionText: "loadType set to Incandescent")
      return configurationCommand(8, 0)
    case "LED":
      sendEvent(name: "loadType", value: "LED", descriptionText: "loadType set to LED")
      return configurationCommand(8, 1)
    case "CFL":
      sendEvent(name: "loadType", value: "CFL", descriptionText: "loadType set to CFL")
      return configurationCommand(8, 2)
  }
}

private setIndicatorStatus(String status) {
  switch (indicatorStatus) {
    case "When switch is off (default)":  return indicatorWhenOff()
    case "When switch is on":             return indicatorWhenOn()
    case "Never":                         return indicatorNever()
  }
}
