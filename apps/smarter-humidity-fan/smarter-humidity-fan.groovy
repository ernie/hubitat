/**
*  Smarter Humidity Fan
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
  state.smart = false
  state.fanOnSince = 0
  state.humidityChange = 0.0
  state.lastHumidity = humiditySensor.currentValue("humidity")
  state.lastHumidityTimestamp = 0
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
  if (
    maxRuntime > 0 && state.fanOnSince > 0 &&
   ( (currentTimestamp - state.fanOnSince) > (maxRuntime * 60 * 1000) )
  ) {
    logInfo "${fanSwitch.label} has been on over $maxRuntime minutes."
    fanOff()
  } else if (state.smart) {
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
    if (state.smart) {
      logInfo "Switch was turned off. Disabling smart mode."
      state.smart = false
    }
  } else if (event.value == "on") {
    if (maxRuntime > 0) {
      logInfo "Switch was turned on. Will turn off in $maxRuntime minutes."
    }
    state.fanOnSince = event.date.time
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
