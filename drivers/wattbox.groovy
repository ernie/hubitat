/**
* WattBox Telnet Driver
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

import java.util.regex.Matcher

metadata {
  definition (name: "WattBox", namespace: "ernie", author: "Ernie Miller") {
    capability "Refresh"

    attribute "firmware", "string"
    attribute "hostname", "string"
    attribute "model", "string"

    command "resetConnection"
    command "sendMessage", ["string"]
  }
}

preferences {
  input "ip", "text", title: "IP / Hostname", required: true
  input "port", "number", title: "Port (23)", required: true, defaultValue: 23
  input "username", "text", title: "Username", required: true
  input "password", "password", title: "Password", required: true
  input "logEnabled",
    "bool", title: "Enable Logging", required: false, defaultValue: false
  input "debugLogEnabled",
    "bool", title: "Enable Additional Debug Logging", required: false,
    defaultValue: false
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def initialize() {
  if (ip && port && username && password) {
    logDebug "Connecting to $ip on port $port"
    telnetConnect([termChars: [10]], ip, port as int, null, null)
    sendMessage(username)
    sendMessage(password)
    sendMessage("?Model")
    sendMessage("?Firmware")
    sendMessage("?Hostname")
    refresh()
  } else {
    log.error "IP/hostname, port, username, and password are required."
  }
}

def parse(String message) {
  logDebug "Parsing: $message"
  switch (message) {
    case ~/[~\?]OutletStatus=(.*)/:
      outletStatus = Matcher.lastMatcher[0][1]
      outletStatus.split(",").eachWithIndex { status, index ->
        outletNumber = index + 1
        outlet = getOutlet(outletNumber)
        newState = status == "1" ? "on" : "off"
        outlet.parse([
          [name: "switch", value: newState,
            descriptionText: "$outlet.displayName is $newState"]
        ])
      }
      break
    case ~/\?Firmware=(.*)/:
      sendEvent(name: "firmware", value: Matcher.lastMatcher[0][1])
      break
    case ~/\?Hostname=(.*)/:
      sendEvent(name: "hostname", value: Matcher.lastMatcher[0][1])
      break
    case ~/\?Model=(.*)/:
      sendEvent(name: "model", value: Matcher.lastMatcher[0][1])
      break
    case "OK":
      logDebug "Previous command successful."
      break
    default:
      logDebug "Parse: no match."
  }
}

def telnetStatus(String message) {
	logDebug "telnetStatus: $message"
	if (message == "receive error: Stream is closed") {
		log.error "Telnet connection dropped. Reconnecting..."
		initialize()
	} else {
    log.error "Unknown telnetStatus message: $message"
	}
}

def resetConnection() {
  logDebug "Resetting telnet session"
  telnetClose()
  initialize()
}

def sendMessage(String message) {
  logDebug "Sending: $message"
  sendHubCommand(
    new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET)
  )
}

def componentOff(outlet) {
  logInfo "received off request from ${outlet.displayName}"
  sendMessage("!OutletSet=${getNumberFromOutlet(outlet)},OFF")
}

def componentOn(outlet) {
  logInfo "received on request from ${outlet.displayName}"
  sendMessage("!OutletSet=${getNumberFromOutlet(outlet)},ON")
}

def componentRefresh(outlet) {
  logInfo "received refresh request from ${outlet.displayName}"
  refresh()
}

def refresh() {
  sendMessage("?OutletStatus")
}

private def getNumberFromOutlet(outlet) {
  outlet.deviceNetworkId.split("-").last()
}

private def getOutlet(number) {
  String thisId = device.id
  def outlet = getChildDevice("$thisId-$number")
  if (!outlet) {
    outlet = addChildDevice(
      "hubitat", "Generic Component Switch", "$thisId-$number",
      [name: "$device.displayName Outlet $number", isComponent: true]
    )
  }
  outlet
}

private def logInfo(message) {
  if (logEnabled) {
    log.info "$device.displayName: $message"
  }
}

private def logDebug(message) {
  if (logEnabled && debugLogEnabled) {
    log.debug "$device.displayName: $message"
  }
}
