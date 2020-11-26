/**
* Companion Cube
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
  name: "Companion Cube",
  namespace: "ernie",
  author: "Ernie Miller",
  description: "Improve your quality of life with a faithful Companion (Aqara) Cube!",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: ""
)

preferences {
  page(name: "mainPage", title: "Companion Cube")
}

def mainPage() {
  dynamicPage(
    name: "mainPage", title: "<h1>Companion Cube</h1>",
    install: true, uninstall: true, refreshInterval: 0
  ) {
    section("Instructions", hideable: true, hidden: true) {
      paragraph "Companion Cube supports two control modes: switches and " +
        "music players. To toggle between modes, flip the cube 90&#xb0;. It " +
        "also allows toggling between two different groups with a 180&#xb0; " +
        "flip."
      paragraph "To discover which device you're currently controlling, just " +
        "shake the cube. To select the next device in the current group/mode " +
        "slide the cube. You can turn a switch on/off or play/pause a music " +
        "player using the cube's knock gesture. Lastly, you can control " +
        "volume or dimmer level with a rotation to the left (down) or right " +
        "(up)."
      paragraph "Companion cubes can be a bit finicky at times, so please " +
        "consider this app more of a novelty than something to base critical " +
        "functionality on."
    }
    section("<h2>Devices</h2>") {
      paragraph "Please configure the driver to simple (7 buttons) mode."
      input "cube",
        "capability.threeAxis", title: "Cube",
        required: true
      paragraph "As you probably know, companion cubes are very sensitive. " +
        "Here, we can ignore tiny rotations, to prevent detecting rotate " +
        "when sliding."
      input "sensitivity", "number",
        title: "Sensitivity (degrees rotation to equal 100% - default 180)",
        defaultValue: 180, range: "90..360"
      input "minRotation",
        "number", title: "Minimum rotation in degrees",
        defaultValue: 10
    }
    section("<h3>Group 1</h3>") {
      input "switches0",
        "capability.switch", title: "Switches", required: false, multiple: true
      input "players0",
        "capability.musicPlayer", title: "Music Players", required: false,
        multiple: true
    }
    section("<h3>Group 2</h3>") {
      input "switches1",
        "capability.switch", title: "Switches", required: false, multiple: true
      input "players1",
        "capability.musicPlayer", title: "Music Players", required: false,
        multiple: true
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
  if (cube) {
    subscribe(cube, "pushed.1", shake)
    subscribe(cube, "pushed.2", flip90)
    subscribe(cube, "pushed.3", flip180)
    subscribe(cube, "pushed.4", slide)
    subscribe(cube, "pushed.5", knock)
    subscribe(cube, "angle", rotate)
  }
  state.lastRotation = 0
  state.mode = state.mode ?: "switches"
  state.currentGroup = state.currentGroup ?: 0
  state.currentSwitchId = state.currentSwitchId ?: groupSwitches[0].id
  state.currentPlayerId = state.currentPlayerId ?: groupPlayers[0].id
  def device = currentDevice ?: nextDevice
  cubeStatus.update(mode: mode, group: group, device: device)
}

def shake(event) {
  def device = currentDevice
  logDebug "shake: $device"
  if (device?.hasCapability("MusicPlayer")) {
    device.playTextAndRestore("Hello!", 50)
  } else {
    flash(device)
  }
}

def flip90(event) {
  toggleMode()
  def device = currentDevice ?: nextDevice
  cubeStatus.update(mode: mode, group: group, device: device)
  logDebug "flip90: mode is now $mode (controlling $device)"
}

def flip180(event) {
  toggleGroup()
  def device = currentDevice ?: nextDevice
  cubeStatus.update(mode: mode, group: group, device: device)
  logDebug "flip180: device group ${group + 1}, controlling $device"
}

def slide(event) {
  def device = nextDevice()
  cubeStatus.update(mode: mode, group: group, device: device)
  logDebug "slide: $device"
}

def knock(event) {
  def device = currentDevice
  logDebug "knock: $device"
  if (device?.hasCapability("MusicPlayer")) {
    if (device?.currentValue("status") == "playing") {
      device.pause()
    } else {
      device.play()
    }
  } else {
    if (device?.currentValue("switch") == "off") {
      device.on()
    } else {
      device.off()
    }
  }
}

def rotate(event) {
  state.lastRotation = (state.lastRotation ?: 0) + Integer.parseInt(event.value)
  runInMillis(500, "applyAdjustment")
}

def applyAdjustment() {
  def rotation = state.lastRotation
  state.lastRotation = 0
  if (Math.abs((int)rotation) >= (int)minRotation) {
    logDebug "Rotation of $rotation exceeded $minRotation degrees. Applying."
    adjustLevel(currentDevice, (int)rotation)
  } else {
    logDebug "Rotation of $rotation less than $minRotation degrees. Ignoring."
  }
}

private adjustLevel(device, int rotation) {
  if (device?.hasAttribute("level")) {
    def currentLevel = (int)device.currentValue("level")
    def adjustment = (int)(100 * (rotation / (int)sensitivity))
    def newLevel = Math.min(100, Math.max(0, currentLevel + adjustment))
    logDebug "Adjustment is $adjustment. Setting $device to $newLevel."
    device.setLevel(newLevel)
  }
}

private getCurrentSwitch() {
  findSwitchById(state.currentSwitchId) ?: groupSwitches[0]
}

private getCurrentPlayer() {
  findPlayerById(state.currentPlayerId) ?: groupPlayers[0]
}

private getCurrentDevice() {
  def device
  switch (mode) {
    case "players":
      device = currentPlayer
      if (!device) {
        device = currentSwitch
        if (device) { state.mode = "switches" }
      }
      break
    case "switches":
      device = currentSwitch
      if (!device) {
        device = currentPlayer
        if (device) { state.mode = "players" }
      }
      break
  }
  device
}

private nextDevice() {
  def nextDev
  switch (mode) {
    case "players":
      nextDev = nextPlayer
      if (nextDev) {
        state.currentPlayerId = nextDev.id
      } else {
        nextDev = nextSwitch
        if (nextDev) {
          state.mode = "switches"
          state.currentSwitchId = nextDev.id
        }
      }
      break
    case "switches":
      nextDev = nextSwitch
      if (nextDev) {
        state.currentSwitchId = nextDev.id
      } else {
        nextDev = nextPlayer
        if (nextDev) {
          state.mode = "players"
          state.currentPlayerId = nextDev.id
        }
      }
      break
  }
  nextDev
}

private findSwitchById(id) {
  logDebug "Finding switch with id: $id"
  groupSwitches.find { sw ->
    sw.id == id
  } ?: groupSwitches[0]
}

private findPlayerById(id) {
  logDebug "Finding player with id: $id"
  groupPlayers.find { pl ->
    pl.id == id
  } ?: groupPlayers[0]
}

private getNextSwitch() {
  def index = groupSwitches.findIndexOf { sw ->
    sw.id == currentSwitch.id
  } + 1
  groupSwitches[index] ?: groupSwitches[0]
}

private getNextPlayer() {
  def index = groupPlayers.findIndexOf { sw ->
    sw.id == currentPlayer.id
  } + 1
  groupPlayers[index] ?: groupPlayers[0]
}

private getGroupSwitches() {
  settings."switches$group" ?: []
}

private getGroupPlayers() {
  settings."players$group" ?: []
}

private getGroup() {
  state.currentGroup ?: 0
}

private toggleGroup() {
  state.currentGroup = group ^ 1
}

private getMode() {
  state.mode
}

private toggleMode() {
  if (mode == "switches") {
    state.mode = "players"
  } else {
    state.mode = "switches"
  }
}

private flash(sw) {
  logDebug "Flashing $sw"
  if (sw.currentValue("switch") == "off") {
    switchOn(sw)
    pause(1000)
    switchOff(sw)
  } else {
    def prevLevel = sw.currentValue("level")
    switchOff(sw)
    pause(1000)
    switchOn(sw, prevLevel)
  }
}

private switchOff(sw) {
  if (sw.hasCapability("SwitchLevel")) {
    sw.setLevel(0, 0)
  } else {
    sw.off()
  }
}

private switchOn(sw, level = 100) {
  if (sw.hasCapability("SwitchLevel")) {
    sw.setLevel(level, 0)
  } else {
    sw.on()
  }
}

private getCubeStatus() {
  def cubeStatus = getChildDevice(cubeStatusId)
  if (!cubeStatus) {
    cubeStatus = addChildDevice(
      "ernie", "Companion Cube Status", cubeStatusId,
      [label: "${app.label} Status", isComponent: true]
    )
  }
  cubeStatus
}

private getCubeStatusId() {
  "companion-cube-${app.id}-status"
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
