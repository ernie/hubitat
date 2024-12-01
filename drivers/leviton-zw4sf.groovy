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
 */

import groovy.transform.Field

metadata {
  definition (name: "Leviton ZW4SF Fan Controller", namespace: "ernie", author: "Ernie Miller", ocfDeviceType: "oic.d.fan") {
    capability "Actuator"
    capability "Configuration"
    capability "FanControl"
    capability "Indicator"
    capability "Refresh"
    capability "Switch"
    capability "SwitchLevel"

    attribute "presetLevel", "number"
    attribute "minLevel", "number"
    attribute "maxLevel", "number"
    attribute "levelIndicatorTimeout", "number"
    attribute "firmwareVersion", "string"

    fingerprint mfr:"001D", prod:"0038", deviceId:"0002", inClusters:"0x5E,0x26,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x87,0x73,0x98,0x9F,0x6C,0x7A,0x70,0x2B,0x2C", deviceJoinName: "Leviton ZW4SF Fan Controller"
  }

  preferences {
    input name: "indicatorStatus", type: "enum", title: "Indicator LED is lit",
      options: ["When switch is off (default)", "When switch is on", "Never"],
      displayDuringSetup: false, required: false
    input name: "presetLevel", type: "number", title: "Fan turns on to level",
      description: "0 = last level (default), 1 - 100 = fixed level", range: "0..100",
      displayDuringSetup: false, required: false
    input name: "minLevel", type: "number", title: "Minimum speed",
      description: "0 to 100 (default 10)", range: "0..100",
      displayDuringSetup: false, required: false
    input name: "maxLevel", type: "number", title: "Maximum speed",
      description: "0 to 100 (default 100)", range: "0..100",
      displayDuringSetup: false, required: false
    input name: "levelIndicatorTimeout", type: "number", title: "Level indicator timeout in seconds",
      description: "0 to 255 (default 3), 0 = level indicator off, 255 = level indicator always on", range: "0..255",
      displayDuringSetup: false, required: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
  }
}

def installed() {
  if (logEnable) log.debug "installed..."
}

def updated() {
  if (state.lastUpdatedAt != null && state.lastUpdatedAt >= now() - 1000) {
    if (logEnable) log.debug "ignoring double updated"
    return
  }
  if (logEnable) log.debug "updated..."
  state.lastUpdatedAt = now()
  configure()
}

def configure() {
  def commands = []
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
    if (logEnable) log.debug "Non-parsed event: $description"
  }
  result
}

def on() {
  state.lastDigital = now()
  def presetLevel = device.currentValue("presetLevel")
  short level = presetLevel == null || presetLevel == 0 ? 0xFF : toZwaveLevel(presetLevel as short)
  if (level != 0xFF) {
    def speed = toSpeed(level)
    def displayLevel = toDisplayLevel(level)
    if (device.currentValue("speed") != speed) {
      sendEvent(name: "speed", value: speed, descriptionText: "Speed set to $speed [$eventType]", type: eventType)
    }
    if (device.currentValue("level") != displayLevel) {
      sendEvent(name: "level", value: displayLevel, unit: "%", descriptionText: "Level set to $displayLevel% [$eventType]", type: eventType)
    }
  }
  if (device.currentValue("switch") != "on") {
    sendEvent(name: "switch", value: "on", descriptionText: "Switch turned on [$eventType]", type: eventType)
  }
  zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: 0).format()
}

def off() {
  state.lastDigital = now()
  if (device.currentValue("speed") != "off") {
    sendEvent(name: "speed", value: "off", descriptionText: "Speed set to off [$eventType]", type: eventType)
  }
  if (device.currentValue("level") != 0) {
    sendEvent(name: "level", value: 0, unit: "%", descriptionText: "Level set to 0% [$eventType]", type: eventType)
  }
  if (device.currentValue("switch") != "off") {
    sendEvent(name: "switch", value: "off", descriptionText: "Switch turned off [$eventType]", type: eventType)
  }
  zwave.switchMultilevelV2.switchMultilevelSet(value: 0x00, dimmingDuration: 0).format()
}

def setSpeed(speed) {
  if (logEnable) log.debug "setSpeed: $speed"

  def value = null

  switch (speed) {
    case ["low", "medium-low"]:
      setLevel(25)
      break
    case "medium":
      setLevel(50)
      break
    case "medium-high":
      setLevel(75)
      break
    case "high":
      setLevel(100)
      break
    case ["on", "auto"]:
      return on()
    case "off":
      return off()
    default:
      if (logEnable) log.debug "Invalid speed: $speed"
  }
}

def setLevel(value, duration = 0) {
  state.lastDigital = now()
  if (logEnable) log.debug "setLevel: $value"

  short level = toDisplayLevel(value as short)
  String speed = toSpeed(level)
  String switchState = speed != "off" ? "on" : "off"

  if (speed != device.currentValue("speed")) {
    sendEvent(name: "speed", value: speed, descriptionText: "Speed set to $speed [$eventType]", type: eventType)
  }
  if (level != device.currentValue("level")) {
    sendEvent(name: "level", value: level, unit: "%", descriptionText: "Level set to $level% [$eventType]", type: eventType)
  }
  if (switchState != device.currentValue("switch")) {
    sendEvent(name: "switch", value: switchState, descriptionText: "Switch turned $switchState [$eventType]", type: eventType)
  }
  zwave.switchMultilevelV2.switchMultilevelSet(value: toZwaveLevel(level), dimmingDuration: 0).format()
}

def refresh() {
  def commands = statusCommands
  commands << zwave.versionV1.versionGet().format()
  for (i in 3..7) {
    commands << zwave.configurationV1.configurationGet(
      parameterNumber: i
    ).format()
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

private String toSpeed(level) {
  switch (level as int) {
    case { it <= 0 }: "off"; break
    case 1..25: "low"; break
    case 26..50: "medium"; break
    case 51..75: "medium-high"; break
    default: "high"
  }
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
    if (logEnable) log.debug "Bad switch value $cmd.value"
  }
}

private zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
  def result = []
  switch (cmd.parameterNumber) {
    case 3:
      result << createEvent(name: "minLevel", value: cmd.configurationValue[0])
      break
    case 4:
      result << createEvent(name: "maxLevel", value: cmd.configurationValue[0])
      break
    case 5:
      result << createEvent(name: "presetLevel", value: cmd.configurationValue[0])
      break
    case 6:
      result << createEvent(name: "levelIndicatorTimeout", value: cmd.configurationValue[0])
      break
    case 7:
      def value = null
      switch (cmd.configurationValue[0]) {
        case 0: value = "never"; break
        case 254: value = "when on"; break
        case 255: value = "when off"; break
      }
      result << createEvent(name: "indicatorStatus", value: value)
      break
  }
  result
}

private zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
  createEvent(name: "firmwareVersion", value: "${cmd.firmware0Version}.${cmd.firmware0SubVersion}", displayed: false)
}

private zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn "Unhandled zwave command $cmd"
}

private levelEvent(short level) {
  def result = []
  def speed = toSpeed(level)
  def displayLevel = toDisplayLevel(level)
  def switchState = level == 0 ? "off" : "on"
  if (level >= 0 && level <= 100) {
    if (speed != device.currentValue("speed")) {
      result << createEvent(name: "speed", value: speed, descriptionText: "Speed set to $speed [$eventType]", type: eventType)
    }
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

private configurationCommand(param, value) {
  param = param as short
  value = value as short
  delayBetween([
      zwave.configurationV1.configurationSet(parameterNumber: param, size: 1, configurationValue: [value]).format(),
      zwave.configurationV1.configurationGet(parameterNumber: param).format()
  ], commandDelayMs)
}

private getEventType() {
  (state.lastDigital && state.lastDigital > now() - 1000) ?
    "digital" :
    "physical"
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

private setIndicatorStatus(String status) {
  switch (indicatorStatus) {
    case "When switch is off (default)": return indicatorWhenOff()
    case "When switch is on": return indicatorWhenOn()
    case "Never": return indicatorNever()
  }
}
