/**
* Companion Cube Status Driver
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
  definition (name: "Companion Cube Status", namespace: "ernie", author: "Ernie Miller") {
    capability "Sensor"

    attribute "event", "string"
    attribute "mode", "string"
    attribute "group", "number"
    attribute "currentDevice", "com.hubitat.app.DeviceWrapper"
    attribute "tile", "string"
  }
}

def update(Map options) {
  def status = cubeStatus
  def tileOptions = [:]
  if (options.event != null && options.event != event) {
    tileOptions.event = options.event
    sendEvent(name: "event", value: options.event, descriptionText: "Current event is now $options.event")
  }
  if (options.mode != null && options.mode != mode) {
    tileOptions.mode = options.mode
    sendEvent(name: "mode", value: options.mode, descriptionText: "Current mode is now $options.mode")
  }
  if (options.group != null && options.group + 1 != group) {
    tileOptions.group = options.group + 1
    sendEvent(name: "group", value: options.group + 1, descriptionText: "Current group is now $options.group")
  }
  if (options.device != null && options.device != currentDevice) {
    tileOptions.device = options.device
    sendEvent(name: "currentDevice", value: options.device, descriptionText: "Current device is now $options.device")
  }

  // There's a race condition if we rely on the events above having taken effect
  if (tileOptions.size() > 0) {
    sendEvent(name: "tile", value: renderTile(tileOptions), descriptionText: "Tile was updated")
    if (tileOptions.event != "idle") { runIn(6, "revertToIdle") }
  }
}

private renderTile(Map options) {
  """
  |<table width="100%" align="center">
  |  <tr>
  |    <td align="center" width="50%">
  |      Group ${options.group ?: group} ${(options.mode ?: mode).capitalize()}
  |    </td>
  |    <td align="center" width="50%">
  |      ${(options.event ?: event)}
  |    </td>
  |  </tr>
  |  <tr>
  |    <td align="center" colspan="2">${options.device ?: currentDevice}</td>
  |  </tr>
  |</table>
  |""".stripMargin()
}

private getGroup() {
  device.currentValue("group")
}

private getMode() {
  device.currentValue("mode")
}

private getCurrentDevice() {
  device.currentValue("currentDevice")
}

private revertToIdle() {
  update(event: "idle")
}
