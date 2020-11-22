/**
* Zooz Power Switch w/State
*
* Copyright 2020 Ernie Miller
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

import groovy.transform.Field

@Field static Map meters = [
  energy: [unit: "kWh", scale: 0],
  power: [unit: "W", scale: 2],
  voltage: [unit: "V", scale: 4],
  amperage: [unit: "A", scale: 5],
].collectEntries { name, opts ->
  [name, opts + [name: name]]
}


@Field static Map configParams = [
  overloadProtection: [
		num: 20,
		title: "Overload Protection",
    description: "Disable at your own peril!",
		size: 1,
    type: "enum",
		options: ["Disabled", "Enabled (default)"],
    defaultValue: "Enabled (default)"
  ],
	powerFailureRecovery: [
		num: 21,
		title: "On/Off Status Recovery After Power Failure",
    description: "Switch status upon regaining lost power",
		size: 1,
    type: "enum",
		options: ["Remember last status (default)", "On", "Off"],
    defaultValue: "Remember last status (default)"
  ],
	ledIndicator: [
		num: 27,
		title: "LED Indicator Control",
    description: "When should the switch light its color-coded power LED?",
		size: 1,
    type: "enum",
		options: [
      "Always show (default)",
      "Show for 5 seconds when turned on/off"
    ],
    defaultValue: "Always show (default)"
  ],
	powerValueChange: [
		num: 151,
		title: "Power Report Value Threshold",
    description: "This change, in watts, will trigger a report (1-65535, 0 = disable)",
		size: 2,
    type: "number",
    defaultValue: 50
  ],
	powerPercentageChange: [
		num: 152,
		title: "Power Report Pecentage Threshold",
    description: "This change, in %, will trigger a report (1-255, 0 = disable, low numbers here will make your switch <em>very</em> chatty on the network)",
		size: 1,
    type: "number",
    defaultValue: 10
  ],
	powerReportInterval: [
		num: 171,
		title: "Power Report Frequency",
    description: "Report power every X seconds (5-2678400, 0 = disable)",
		size: 4,
    type: "number",
    defaultValue: 0
  ],
	energyReportInterval: [
		num: 172,
		title: "Energy Report Frequency",
    description: "Report energy every X seconds (5-2678400, 0 = disable)",
		size: 4,
    type: "number",
    defaultValue: 0
  ],
	voltageReportInterval: [
		num: 173,
		title: "Voltage Report Frequency",
    description: "Report voltage every X seconds (5-2678400, 0 = disable)",
		size: 4,
    type: "number",
    defaultValue: 0
  ],
	amperageReportInterval: [
		num: 174,
		title: "Electricity (Amperage) Report Interval",
    description: "Report amperage every X seconds (5-2678400, 0 = disable)",
		size: 4,
    type: "number",
    defaultValue: 0
  ]
].collectEntries { name, opts ->
  [name, opts + [name: name]]
}

metadata {
  definition (
    name: "Zooz Power Switch w/State", namespace: "ernie",
    author: "Ernie Miller", ocfDeviceType: "oic.d.switch"
  ) {
    capability "Actuator"
    capability "Configuration"
    capability "Energy Meter"
    capability "Initialize"
    capability "Outlet"
    capability "Power Meter"
    capability "Pushable Button"
    capability "Refresh"
    capability "Sensor"
    capability "Switch"
    capability "Voltage Measurement"

    attribute "powerLow", "number"
    attribute "powerHigh", "number"
    attribute "energyLow", "number"
    attribute "energyHigh", "number"
    attribute "voltageLow", "number"
    attribute "voltageHigh", "number"
    attribute "amperage", "number"
    attribute "amperageLow", "number"
    attribute "amperageHigh", "number"
		attribute "energyTime", "number"
    attribute "energyDuration", "string"
    attribute "status", "string"

    command "push", ["number"]
    command "reset"

    fingerprint mfr: "027A", prod: "0101", deviceId: "000D", inClusters: "0x5E,0x25,0x32,0x27,0x2C,0x2B,0x70,0x85,0x59,0x72,0x86,0x7A,0x73,0x5A", deviceJoinName: "Zooz Power Switch"
  }

  preferences {
    input "powerLevels", "string", title: "Power Levels",
      description: "List of increasing power levels in watts, separated by / (Reports below the lowest number here are considered \"idle\", and will be used to determine \"finished\" state after the attached device is seen to be active)",
      required: true, defaultValue: "1"
    input "stateNames", "string", title: "State Names",
      description: "List of state names for power levels, separated by /",
      required: true, defaultValue: "active"
    input "idleName", "string", title: "Idle Name",
      description: "Rename the idle state (ex. \"dirty\")",
      required: true, defaultValue: "idle"
    input "finishedName", "string", title: "Finished Name",
      description: "Rename the finished state (ex. \"clean\")",
      required: true, defaultValue: "finished"
    input "delayFinish", "number", title: "Delay Finish",
      description: "Require below-threshold power reports for this many seconds before transitioning to finished state (0 = immediate)",
      required: true, defaultValue: 0
    configParams.each { paramName, options ->
      input options
    }
    input "logEnable", "bool", title: "Enable logging", defaultValue: false
    input "debugEnable", "bool", title: "Enable debug logging",
      defaultValue: false
  }
}

def installed() {
  initialize()
}

def updated() {
  if (powerLevels && stateNames) {
    newLevels = powerLevels.split("\\s*[,/]\\s*").collect { it.toBigDecimal() }.sort()
    newNames = stateNames.split("\\s*[,/]\\s*")
    if (newLevels.size() == newNames.size()) {
      state.levels = newLevels
      state.names = newNames
    } else {
      log.error "$device.displayName: Unmatched level/name pairs!"
    }
    device.updateSetting(
      "powerLevels", [value: state.levels.join(" / "), type: "string"]
    )
    device.updateSetting(
      "stateNames", [value: state.names.join(" / "), type: "string"]
    )
    newStatus = powerToStatus(device.currentValue("power"))
    if (newStatus != device.currentValue("status")) {
      sendEvent(
        name: "status", value: newStatus,
        descriptionText: "Updating status to $newStatus"
      )
    }
  } else {
    state.levels = []
    state.names = []
  }
  configure()
}

def initialize() {
  refresh()
  def status = powerToStatus(device.currentValue("power"))
  sendEvent(
    name: "status", value: status,
    descriptionText: "Initialized status to $status"
  )
  def energyTime = new Date().time
  sendEvent(
    name: "energyTime", value: energyTime,
    descriptionText: "Initialized energyTime to $energyTime"
  )
}

def configure() {
  logDebug "Configuring."
  def result = []
	def commands = []
	configParams.each { paramName, param ->
		commands += updateConfigVal(param)
	}
	result += commandSequence(commands)

	if (!device.currentValue("energyTime")) {
		result += reset()
	} else {
		result += refresh()
	}
	result
}

def push(button = 1) {
  sendEvent(
    name: "pushed", value: button, descriptionText: "Button $button pushed"
  )
  if (device.currentValue("status") != idle) {
    sendEvent(
      name: "status", value: idle,
      descriptionText: "Resetting status to $idle"
    )
    state.firstIdleAt = null
  }
}

def on() {
	logInfo "Turning On"
	commandSequence([
		switchBinarySetCommand(0xFF),
		switchBinaryGetCommand()
	])
}

def off() {
	logInfo "Turning Off"
	commandSequence([
		switchBinarySetCommand(0x00),
		switchBinaryGetCommand()
	])
}

def refresh() {
  sendEvent(
    name: "numberOfButtons", value: 1,
    descriptionText: "Refreshing numberOfButtons to 1"
  )
  return commandSequence([
    switchBinaryGetCommand(),
    meterGetCommand(meters["energy"]),
    meterGetCommand(meters["power"]),
    meterGetCommand(meters["voltage"]),
    meterGetCommand(meters["amperage"])
  ])
}

def reset() {
	meters.each { name, meter ->
		sendEvent(
      name: "${name}Low", value: 0, unit: meter.unit,
      descriptionText: "Resetting ${name}Low to 0$meter.unit"
    )
		sendEvent(
      name: "${name}High", value: 0, unit: meter.unit,
      descriptionText: "Resetting ${name}High to 0$meter.unit"
    )
	}
  def energyTime = new Date().time
	sendEvent(
    name: "energyTime", value: energyTime,
    descriptionText: "Resetting energyTime to $energyTime"
  )

	def result = [
		meterResetCommand(),
		"delay 1000"
	]
	result += refresh()
	result
}

def parse(String description) {
	def result = []
	def command = zwave.parse(description, commandClassVersions)
	if (command) {
    logDebug "Z-Wave parse: ${command.inspect()}"
		result = zwaveEvent(command)
	}
	else {
		log.warn "Z-Wave unable to parse: ${description.inspect()}"
	}

	result
}

def zwaveEvent(
  hubitat.zwave.commands.configurationv2.ConfigurationReport command
) {
	def val = command.scaledConfigurationValue

	def configParam = configParams.find { name, param ->
		param.num == command.parameterNumber
	}?.value

	if (configParam) {
		def name = configParam.options?.get(val)
		logDebug "$configParam.title (#$configParam.num) = ${name != null ? name : val} ($val)"
		state."config${configParam.name.capitalize()}" = val
	} else {
		logDebug "Parameter $command.parameterNumber = $val"
	}
	return []
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport command) {
  def value = (command.value == 0xFF) ? "on" : "off"
	[createEvent(
    name: "switch", value: value, type: "digital",
    descriptionText: "Switch is $value"
  )]
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport command) {
	def result = []
	result << createSwitchEvent(command.value, "physical")
	return result
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport command) {
	def result = []
	def val = command.scaledMeterValue
  def meter
  meter = meters.find { it.value.scale == command.scale }?.value
  if (!meter) {
    logDebug "Unknown Meter Scale: $command"
  }

  switch (meter?.name) {
    case "power":
      String newStatus = powerToStatus(val)
      if (newStatus != device.currentValue("status")) {
        result << createEvent(
          name: "status", value: newStatus,
          descriptionText: "Updating status to $newStatus"
        )
      }
      break
    case "energy":
      if (val != device.currentValue("energy")) {
        def energyDuration = calculateEnergyDuration()
        result << createEvent(
          name: "energyDuration", value: energyDuration,
          descriptionText: "Updating energyDuration to $energyDuration"
        )
      }
      break
    default:
      null // No special handling for other meters at this time
  }

  if (meter && val != device.currentValue(meter.name)) {
    low = device.currentValue("${meter.name}Low")
    high = device.currentValue("${meter.name}High")
    result << createEvent(
      name: meter.name, value: val, unit: meter.unit,
      descriptionText: "Updating $meter.name to $val$meter.unit"
    )
    if (!low || val < low) {
      result << createEvent(
        name: "${meter.name}Low", value: val, unit: meter.unit,
        descriptionText: "Updating ${meter.name}Low to $val$meter.unit"
      )
    }
    if (!high || val > high) {
      result << createEvent(
        name: "${meter.name}High", value: val, unit: meter.unit,
        descriptionText: "Updating ${meter.name}High to $val$meter.unit"
      )
    }
  }

  result
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)

	def result = []
	if (encapsulatedCmd) {
		result += zwaveEvent(encapsulatedCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
	result
}

private powerToStatus(powerLevel) {
  statusIndex = state.levels.reverse().findIndexOf { it < powerLevel }
  def newStatus
  def currentTime = new Date().time

  if (statusIndex < 0) {
    if (
      delayFinish == 0 ||
      (
        state.firstIdleAt &&
        state.firstIdleAt < currentTime - (delayFinish * 1000)
      )
    ) {
        newStatus = device.currentValue("status") == idle ? idle : finished
        state.firstIdleAt = null
    } else {
      newStatus = device.currentValue("status")
      if (!state.firstIdleAt) {
        state.firstIdleAt = currentTime
      }
    }
  } else {
    state.firstIdleAt = null
    newStatus = state.names[-(1 + statusIndex)]
  }
  newStatus
}

private getIdle() {
  idleName
}

private getFinished() {
  finishedName
}

private logInfo(message) {
  if (logEnable) {
    log.info "$device.displayName: $message"
  }
}

private logDebug(message) {
  if (logEnable && debugEnable) {
    log.debug "$device.displayName: $message"
  }
}

private calculateEnergyDuration() {
	def energyTimeMS = device.currentValue("energyTime")
	if (!energyTimeMS) {
		return "Unknown"
	}
	else {
		def duration = roundToHundredths((new Date().time - energyTimeMS) / 60000)

		if (duration >= (24 * 60)) {
			return getFormattedDuration(duration, (24 * 60), "Day")
		}
		else if (duration >= 60) {
			return getFormattedDuration(duration, 60, "Hour")
		}
		else {
			return getFormattedDuration(duration, 0, "Minute")
		}
	}
}

private getFormattedDuration(duration, divisor, name) {
	if (divisor) {
		duration = roundToHundredths(duration / divisor)
	}
	return "${duration} ${name}${duration == 1 ? '' : 's'}"
}

private roundToHundredths(number) {
  Math.round(number * 100) / 100
}

private meterGetCommand(meter) {
	secureCommand(zwave.meterV3.meterGet(scale: meter.scale))
}

private meterResetCommand() {
	secureCommand(zwave.meterV3.meterReset())
}

private switchBinaryGetCommand() {
	secureCommand(zwave.switchBinaryV1.switchBinaryGet())
}

private switchBinarySetCommand(val) {
	secureCommand(zwave.switchBinaryV1.switchBinarySet(switchValue: val))
}

private configSetCommand(param, val) {
	secureCommand(
    zwave.configurationV2.configurationSet(
      parameterNumber: param.num, size: param.size,
      scaledConfigurationValue: val
    )
  )
}

private configGetCommand(param) {
	secureCommand(
    zwave.configurationV2.configurationGet(parameterNumber: param.num)
  )
}

private secureCommand(command) {
	if (
    zwaveInfo?.zw?.contains("s") ||
   ("0x98" in device.rawDescription?.split(" "))
  ) {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(command).format()
	}
	else {
		return command.format()
	}
}

private commandSequence(commands, delay = 500) {
	commands ? delayBetween(commands, delay) : []
}

private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x25: 1,	// Switch Binary
		0x27: 1,	// All Switch
		0x2B: 1,	// Scene Activation
		0x2C: 1,	// Scene Actuator Configuration
		0x32: 3,	// Meter v4
		0x59: 1,	// AssociationGrpInfo
		0x5A: 1,	// DeviceResetLocally
		0x5E: 2,	// ZwaveplusInfo
		0x70: 2,	// Configuration
		0x72: 2,	// ManufacturerSpecific
		0x73: 1,	// Powerlevel
		0x7A: 2,	// Firmware Update Md (3)
		0x85: 2,	// Association
		0x86: 1,	// Version (2)
		0x98: 1		// Security
	]
}

private updateConfigVal(param) {
	def commands = []
	if (hasPendingChange(param)) {
		def newVal = getParamIntVal(param)
		logInfo "$param.name (#$param.num): changing ${state["config${param.name.capitalize()}"]} to $newVal"
		commands << configSetCommand(param, newVal)
		commands << configGetCommand(param)
	}
	commands
}

private hasPendingChange(param) {
	getParamIntVal(param) != state["config${param.name.capitalize()}"]
}

private getParamIntVal(param) {
  def value = settings?."$param.name" ?: param.defaultValue
  param.options ? param.options.indexOf(value) : value
}
