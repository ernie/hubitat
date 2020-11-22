/**
* Virtual Motion Sensor with Switch
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

metadata {
  definition(name: "Virtual Motion Sensor with Switch", namespace: "ernie",
    author: "Ernie Miller") {
    capability "Actuator"
    capability "Motion Sensor"
    capability "Sensor"
    capability "Switch"
  }

  preferences {
    input name: "inactiveSeconds", type: "number",
      title: "Inactive Delay",
      description: "Seconds until switch/motion toggles off (10 - 120)",
      range: "10..120", defaultValue: 10, required: true
    input name: "inactiveEnable", type: "bool", title: "Enable automatic inactivity",
      defaultValue: true
    input name: "logEnable", type: "bool", title: "Enable Logging",
      defaultValue: false
  }
}

def installed() {
  off()
}

def on() {
  if (logEnable) { log.info "$device.displayName active" }
  sendEvent(name: "motion", value: "active")
  sendEvent(name: "switch", value: "on")
  if (inactiveEnable) { runIn(inactiveSeconds, "off") }
}

def off() {
  if (logEnable) { log.info "$device.displayName inactive" }
  sendEvent(name: "motion", value: "inactive")
  sendEvent(name: "switch", value: "off")
}
