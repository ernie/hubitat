/**
* Smarter Humidity Fan
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

definition(
  name: "Smarter Humidity Fan",
  namespace: "ernie",
  author: "Ernie Miller",
  description: "Control a fan switch based on relative humidity.",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: ""
)

preferences {
  page(name: "mainPage", title: "Smarter Humidity Fan")
}

def mainPage() {
  dynamicPage(
    name: "mainPage", title: "<h1>Smarter Humidity Fan</h1>",
    install: true, uninstall: true, refreshInterval: 0
  ) {
    section("Device Instructions", hideable: true, hidden: true) {
      paragraph "The humidity sensor you select will control the fan switch " +
        "you select. In addition, you can select additional switches to turn " +
        "on when the sensor triggers \"smart mode\", and off when the " +
        "humidity drops again."
      paragraph "Lastly, the app automatically creates a child virtual " +
        "switch labeled with the name of the app and suffixed \"Smart Mode\" " +
        "which reflects the current status of the fan's smart mode. You can " +
        "use this switch to prevent things like motion lights turning off " +
        "when you're in the shower, or trigger other, more complex rules in " +
        "Rule Machine based on it."
    }
    section("<h2>Devices</h2>") {
      input "humiditySensor", "capability.relativeHumidityMeasurement",
        title: "Humidity Sensor:", required: true
      input "fanSwitch", "capability.switch", title: "Fan Switch:",
        required: true
      input "extraSwitches", "capability.switch",
        title: "Additional Switches:", multiple: true
    }
    section("Fan Behavior Instructions", hideable: true, hidden: true) {
      paragraph "<b>Fan behavior is controlled based on how quickly humidity " +
        "is changing within a range, called the <em>smart range</em>.</b> " +
        "When humidity increases at a rate exceeding your (percent/min) " +
        "sensitivity setting within the smart range, the fan will turn on, " +
        "and will remain on until the humidity drops below the smart range " +
        "again, or until the auto-off timer expires."
      paragraph "<b>The top end of the smart range defines a humidity level " +
        "that is unacceptable no matter how slowly it was reached, and the " +
        "fan will turn on (and remain on) while this value is exceeded.</b> " +
        "If the auto-off timer expires and the humidity is still above the " +
        "smart range, the timer will be reset."
      paragraph "<b>Similarly, since some devices require a significant " +
        "change before they report a humidity increase, an \"Excessive " +
        "Change Threshold\" can be configured.</b> This prevents requiring " +
        "a second report if, for instance, the device has not reported all " +
        "night, but its first report of the morning increases by several %."
      paragraph "If the fan was manually turned on, but sensitivity/range " +
        "conditions are met, smart mode will engage, and turn your fan off " +
        "at the appropriate time, as if it had been turned on automatically."
    }
    section("<h2>Fan Behavior</h2>") {
      input "sensitivity",
        "decimal", title: "<b>Sensitivity</b> (% / minute, 0.1 - 2.0)",
        required: true, defaultValue: 1.0, range: "0.1..2.0"
      input "minHumidity",
        "number", title: "<b>Smart Range Minimum %</b>", required: true,
        defaultValue: 55
      input "maxHumidity",
        "number", title: "<b>Smart Range Maximum %</b>", required: true,
        defaultValue: 65
      input "excessiveChange",
        "number", title: "<b>Excessive Change Threshold</b> (%, 0 to disable)",
        required: true, defaultValue: 5
      input "maxRuntime",
        "number", title: "<b>Auto-off check</b> (minutes, 0 to disable)",
        required: true, defaultValue: 60
      input "disableModes",
        "mode", title: "<b>Disable fan activation in modes</b>", multiple: true
    }
    section("Logging") {
      input "logEnabled",
        "bool", title: "Enable Logging", required: false, defaultValue: false
      input "debugLogEnabled",
        "bool", title: "Enable Additional Debug Logging", required: false,
        defaultValue: false
    }

    section() {
      label title: "Enter a name for this app instance", required: false
    }
  }
}

def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  initialize()
}

def initialize() {
  if (state.smart == null) {
    state.smart = false
  }
  state.fanOnSince = state.fanOnSince ?: 0
  state.humidityChange = state.humidityChange ?: 0.0
  state.lastHumidity = state.lastHumidity ?:
    humiditySensor.currentValue("humidity")
  state.lastHumidityTimestamp = state.lastHumidityTimestamp ?:
    (new Date()).getTime()
  subscribe(humiditySensor, "humidity", humidityEvent)
  subscribe(fanSwitch, "switch", switchEvent)
  smartSwitch
}

def humidityEvent(event) {
  logDebug "Received humidity event: $event.value"
  currentHumidity = event.value as Double
  currentTimestamp = event.date.time
  state.humidityChange = currentHumidity - state.lastHumidity
  elapsedMinutes = (currentTimestamp - state.lastHumidityTimestamp) / 1000 / 60
  change = state.humidityChange > 0 ? "rising" : "falling"
  changeRate = Math.abs(state.humidityChange) / elapsedMinutes
  logDebug "Humidity $change: $state.humidityChange% in $elapsedMinutes min"
  state.lastHumidity = currentHumidity
  state.lastHumidityTimestamp = currentTimestamp
  sensitivityTriggered = false
  if (changeRate >= sensitivity) {
    logDebug "Sensitivity criteria met. Humidity $change at $changeRate%/min."
    sensitivityTriggered = true
  }
  if (smart) {
    if (
      state.humidityChange < 0 &&
      currentHumidity < minHumidity
    ) {
      logInfo "Humidity dropped to bottom of smart range ($minHumidity%)."
      smartModeOff()
    } else {
      logDebug "Humidity still above $minHumidity%. No action taken."
    }
  } else { // State is not (yet) smart
    if (
      sensitivityTriggered &&
      state.humidityChange > 0 &&
      currentHumidity > minHumidity
    ) {
      logInfo "Humidity passed $minHumidity% at $changeRate%/min."
      smartModeOn()
    } else if (
      excessiveChange > 0 &&
      state.humidityChange >= excessiveChange
    ) {
      logInfo "Change of $state.humidityChange% exceeds configured " +
              "excessive change threshold of $excessiveChange%."
      smartModeOn()
    } else if (currentHumidity > maxHumidity) {
      logInfo "Humidity exceeded $maxHumidity%."
      smartModeOn()
    } else {
      logDebug "Humidity change within defined thresholds. No action taken."
    }
  }
}

def switchEvent(event) {
  logDebug "Received switch event: $event.value"
  if (event.value == "off") {
    if (smart) {
      smartModeOff()
    }
  } else if (event.value == "on") {
    setAutoOff("Switch was turned on.")
    state.fanOnSince = event.date.time
  }
}

def runtimeExceeded() {
  if (state.fanOnSince > 0 && fanSwitch.currentValue("switch") == "on") {
    now = new Date()
    runtime = ((now.getTime() - state.fanOnSince) / 1000 / 60) as int
    logInfo "Auto-off: $fanSwitch.displayName has been on for $runtime minutes."
    if (state.lastHumidity > maxHumidity) {
      setAutoOff("Humidity is still too high (above $maxHumidity%).")
    } else {
      smartModeOff()
    }
  }
}

private smartModeOn() {
  state.smart = true
  smartSwitch.on()
  logInfo "Smart mode triggered on $fanSwitch.displayName."
  if (disableModes && disableModes.contains(location.mode)) {
    logDebug "Will not turn on $fanSwitch.displayName in $location.mode mode."
  } else {
    setAutoOff("Refreshing auto-off timer due to smart mode trigger.")
    fanSwitch.on()
    if (extraSwitches.size() > 0) {
      logInfo "Turning on additional switches: ${extraSwitches.join(", ")}"
      extraSwitches.each { sw -> sw.on() }
    }
  }
}

private smartModeOff() {
  logInfo "Turning off $fanSwitch.displayName."
  state.fanOnSince = 0
  unschedule("runtimeExceeded")
  fanSwitch.off()
  if (smart && extraSwitches.size() > 0) {
    logInfo "Smart mode was on. Turning off additional switches: ${extraSwitches.join(", ")}"
    extraSwitches.each { sw -> sw.off() }
  }
  smartSwitch.off()
  state.smart = false
}

private setAutoOff(message) {
  if (maxRuntime > 0) {
    logInfo "$message Auto-off in $maxRuntime minutes."
    runIn(maxRuntime * 60, "runtimeExceeded")
  } else {
    logDebug "Auto-off is disabled in preferences."
  }
}

private getExtraSwitches() {
  settings.extraSwitches ?: []
}

private getSmart() {
  state.smart
}

private getSmartSwitch() {
  def smartSwitch = getChildDevice(smartSwitchId)
  if (!smartSwitch) {
    smartSwitch = addChildDevice(
      "hubitat", "Virtual Switch", smartSwitchId,
      [label: "${app.label} Smart Mode", isComponent: true]
    )
  }
  smartSwitch
}

private getSmartSwitchId() {
  "smarter-humidity-fan-${app.id}-switch"
}

private logInfo(message) {
  if (logEnabled) {
    log.info "$app.label: $message"
  }
}

private logDebug(message) {
  if (logEnabled && debugLogEnabled) {
    log.debug "$app.label: $message"
  }
}
