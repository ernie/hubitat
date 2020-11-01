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
    page(name: "pageConfig")
}

def pageConfig() {
	dynamicPage(
    name: "", title: "", install: true, uninstall: true, refreshInterval:0
  ) {
	  section("Devices") {
			input "humiditySensor",
        "capability.relativeHumidityMeasurement", title: "Humidity Sensor:",
        required: true
			input "fanSwitch",
        "capability.switch", title: "Fan Switch:", required: true
		}
		section("Fan Behavior") {
      paragraph "Fan behavior is controlled based on how quickly humidity " +
        "is changing. A change of X% in Y minutes, configured below, will " +
        "trigger the app to evaluate actions. A rapid rise in humidity will " +
        "trigger your fan to turn on. A rapid fall in humidity will trigger " +
        "your fan to turn off, provided humidity has dropped below the target."
      paragraph "Slower humidity changes can also turn the fan on or off, " +
        "if they rise 5% above or below the target humidity, respectively."
      paragraph "If the fan was manually turned on, but the smart threshold " +
        "is reached, Smarter Humidity Fan will turn the fan off for you later."
      input "sensitivity",
        "number", title: "Sensitivity %", required: true, defaultValue: 2
      input "sensitivityPeriod",
        "number", title: "Sensitivity Minutes", required: true, defaultValue: 10
			input "targetHumidity",
        "number", title: "Target Humidity %", required: true, defaultValue: 65
      input "maxRuntime",
        "number", title: "Auto-off (minutes, 0 to disable)", required: true,
        defaultValue: 120
			input "disableModes",
        "mode", title: "Disable fan activation in modes", multiple: true
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
  state.lastHumidityTimestamp = state.lastHumidityTimestamp ?: 0
  subscribe(humiditySensor, "humidity", humidityEvent)
  subscribe(fanSwitch, "switch", switchEvent)
}

def humidityEvent(event) {
  logDebug "Received humidity event: ${event.value}"
  currentHumidity = event.value as Double
  currentTimestamp = event.date.time
  state.humidityChange = currentHumidity - state.lastHumidity
  elapsedMinutes = (currentTimestamp - state.lastHumidityTimestamp) / 1000 / 60
  logDebug "Humidity changed ${state.humidityChange}% in ${elapsedMinutes} min"
  state.lastHumidity = currentHumidity
  state.lastHumidityTimestamp = currentTimestamp
  sensitivityTriggered = false
  if (Math.abs(state.humidityChange) >= sensitivity &&
      elapsedMinutes < sensitivityPeriod) {
    change = state.humidityChange > 0 ? "rising" : "dropping"
    logInfo "Sensitivity criteria met. Humidity $change quickly."
    sensitivityTriggered = true
  }
  if (state.smart) {
    if (
      sensitivityTriggered &&
      state.humidityChange < 0 &&
      currentHumidity < targetHumidity
    ) {
      logInfo "Humidity dropped below target humidity."
      fanOff()
    } else if ( // In case we drop slowly, but quite low
      state.humidityChange < 0 &&
      currentHumidity < targetHumidity - 5
    ) {
      logInfo "Humidity dropped over 5% below target humidity."
      fanOff()
    } else {
      logDebug "No action taken. Smart mode remains triggered."
    }
  } else { // State is not (yet) smart
    if (sensitivityTriggered && state.humidityChange > 0) {
      fanOn()
    } else if (currentHumidity > targetHumidity + 5) { // Rose slowly, but high
      logInfo "Humidity exceeded target by 5%."
      fanOn()
    } else {
      logDebug "No action taken. Smart mode remains untriggered."
    }
  }
}

def switchEvent(event) {
  logDebug "Received switch event: ${event.value}"
  if (event.value == "off") {
    state.fanOnSince = 0
    unschedule("runtimeExceeded")
    if (state.smart) {
      logInfo "Switch was turned off. Disabling smart mode."
      state.smart = false
    }
  } else if (event.value == "on") {
    if (maxRuntime > 0) {
      logInfo "Switch was turned on. Will turn off in $maxRuntime minutes."
      runIn(maxRuntime * 60, "runtimeExceeded")
    }
    state.fanOnSince = event.date.time
  }
}

def runtimeExceeded() {
  if (state.fanOnSince > 0 && fanSwitch.currentValue("switch") == "on") {
    now = new Date()
    runtime = ((now.getTime() - state.fanOnSince) / 1000 / 60) as int
    logInfo "${fanSwitch.label} has been on for $runtime minutes."
    fanOff()
  }
}

private def fanOn() {
  state.smart = true
  if (disableModes && disableModes.contains(location.mode)) {
    logDebug "Will not turn on ${fanSwitch.label} in ${location.mode} mode."
  } else {
    logInfo "Turning on ${fanSwitch.label}."
    fanSwitch.on()
  }
}

private def fanOff() {
  logInfo "Turning off ${fanSwitch.label}."
  state.smart = false
  state.fanOnSince = 0
  fanSwitch.off()
}

private def logInfo(message) {
  if (logEnabled) {
    log.info message
  }
}

private def logDebug(message) {
  if (logEnabled && debugLogEnabled) {
    log.debug message
  }
}
