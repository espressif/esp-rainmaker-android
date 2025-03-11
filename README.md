## ESP RainMaker Android App

This is the official Android app for [ESP RainMaker](https://github.com/espressif/esp-rainmaker), an end-to-end solution offered by Espressif to enable remote control and monitoring for ESP32-S2 and ESP32 based products without any configuration required in the Cloud. 

For more details :
 - Please check the ESP RainMaker documentation [here](http://rainmaker.espressif.com/docs/get-started.html) to get started.
 - Try out this app in [Play Store](https://play.google.com/store/apps/details?id=com.espressif.rainmaker).
 
## Setup
 
To build this app, you will need a development machine, with Android Studio installed.

To get this app please clone this repository using the below command and open this project in Android Studio:
```
 git clone https://github.com/espressif/esp-rainmaker-android.git
```
You are now ready to run this demo.

## Features

### User Management

- Signup/Signin using email id.
- Third party login includes GitHub and Google.
- Forgot/reset password support.
- Signing out.
- Delete user.

### Provisioning

- Uses [Provisioning library](https://github.com/espressif/esp-idf-provisioning-android/) for provisioning.
- Automatically connects to device using QR code.
- Can choose manual flow if QR code is not present.
- Shows list of available Wi-Fi networks.
- Supports SoftAP based Wi-Fi Provisioning.
- Performs the User-Node association workflow.

### Manage 

- List all devices associated with a user.
- Shows node and device details.
- Capability to remove node of a user.
- Shows online/offline status of nodes.

### Control

- Shows all static and configurable parameters of a device.
- Adapt UI according to the parameter type like toggle for power, slider for brightness.
- Allow user to change and monitor parameters of devices.

### Local Control

- App uses ESP Local Control(esp_local_ctrl) component in ESP-IDF and Network Service Discovery APIs to search and manage user devices on local network.
- This feature allows user to control their ESP devices over local network by communicating over Wi-Fi + HTTP.
- Local Control ensures your devices are reachable even when your internet connection is poor or there is no internet over connected Wi-Fi.

This feature can be enabled/disabled by setting true/false value of `isLocalControlSupported` field in `local.properties`.
This feature is optional but enabled by default.
Add `isLocalControlSupported=false` in `local.properties` file to disable this feature.

### Scheduling

Schedules allow you to automate a device by setting it to trigger an action at a given time on a specified day or days of the week.
List of operations that are supported for scheduling :
 
 - Add.
 - Edit.
 - Remove.
 - Enable/disable.

Schedule can be enabled/disabled by setting true/false value of `isScheduleSupported` field in `local.properties`.
Schedule feature is optional but enabled by default.
Add `isScheduleSupported=false` in `local.properties` file to disable this feature.

### Scene

Scene is a group of parameters with specific values, for one or more devices (optionally) spanning across multiple nodes. As an example, an "Evening" scene may turn on all the lights and set them to a warm colour. A "Night" scene may turn off all the lights, turn on a bedside lamp set to minimal brightness and turn on the fan/ac.
List of operations that are supported for scene :

- Add.
- Edit.
- Remove.
- Activate.

Scene can be enabled/disabled by setting true/false value of `isSceneSupported` field in `local.properties`.
Scene feature is optional but enabled by default.
Add `isSceneSupported=false` in `local.properties` file to disable this feature.

### Node Grouping

Node Grouping allows you to create abstract or logical groups of devices like lights, switches, fans etc.
List of operations that are supported in node grouping :

 - Create groups.
 - Edit groups (rename or add/remove device).
 - Remove groups.
 - List groups.

Grouping can be enabled/disabled by setting true/false value of `isNodeGroupingSupported` field in `local.properties`.
Grouping feature is optional but enabled by default.
Add `isNodeGroupingSupported=false` in `local.properties` file to disable this feature.

### Node Sharing

Node Sharing allows a user to share nodes with other registered users and allow them to monitor and control these nodes.
List of operations that are supported in node sharing :

For primary users:
- Register requests to share nodes.
- View pending requests.
- Cancel a pending request, if required.
- Remove node sharing.

For secondary users:
- View pending requests.
- Accept/decline pending requests.

Sharing can be enabled/disabled by setting true/false value of `isNodeSharingSupported` field in `local.properties`.
Sharing feature is optional but enabled by default.
Add `isNodeSharingSupported=false` in `local.properties` file to disable this feature.

### Group Sharing

Group sharing allows a user to share groups with other registered users and allow them to monitor and control nodes of the shared group.
List of operations that are supported in group sharing :

For primary users:
- Register requests to share groups.
- Share a group with full access.
- View pending requests.
- Cancel a pending request, if required.
- Remove group sharing.

For secondary users:
- View pending requests.
- Accept/decline pending requests.
- Control nodes which are in shared group.
- Leave a group.

### Device Automation

Device Automation is a set of actions that will be triggered based on the completion of certain events. For example, the user can set an event as the Temperature sensor equals 35 degrees celsius. Then based on this event user can trigger different actions like Switching on AC or Setting the AC temperature to 20 degrees celsius or a combination of both.

Users will be allowed to perform different automation operations in the app as mentioned below:

1. Adding new automation.
2. Updating existing automation.
3. Enabling/disabling automation triggers.
4. Deleting automation.
5. Receiving notifications related to triggered automation.

Device automation can be enabled/disabled by setting true/false value of `isAutomationSupported` field in `local.properties`.
Device automation is optional but enabled by default.
Add `isAutomationSupported=false` in `local.properties` file to disable this feature.

### Push Notifications

ESPRainMaker app supports remote notifications in order to notify app in realtime for any updates.

User will receive push notifications for below events :

- A new node is added to the user.
- Existing node is removed from the user account.
- Node is shared to secondary user.
- Node sharing request is accepted/declined by secondary user.
- Node gets connected to cloud.
- Node losses cloud connection.
- Alerts triggered from the node.

User can also enable / disable specific type of notification by enabling / disabling specific channel from notification settings available in app info.
User will also receive silent notification for any device param changes. It will update the app with latest param value when app is in foreground.

### Time Series

- Time series allows a user to see historical values of parameters plotted as a bar or line chart.
- Users can select the different time duration to see reported parameter values like 1 day, 7 days, 4 weeks and 1 year.
- Users can see graph for raw data and can also select from different aggregate types like avg, min, max, count and latest.

Note : Time series feature requires support in firmware. It will be available only for the parameters who have "time_series" property.

### System service

System service allows a primary user of the node to perform node operations like:
- Reboot
- Wi-Fi reset
- Factory reset

Note : System service feature requires support in firmware. It will be available for nodes that has "esp.service.system" configured.

### OTA Update

- Checks if firmware update is available for nodes that requires user approval.
- Push firmware update to nodes remotely when user provide approval using the app.

OTA update is optional and disabled by default. It can be enabled/disabled by setting true/false value of `isOtaSupported` field in `local.properties`.
Add `isOtaSupported=true` in `local.properties` file to enable this feature.

### Alexa App to App Linking

This account linking flow enables users to link their Alexa user identity with their RainMaker identity by starting from Rainmaker app. When they start the account linking flow from the app, users can:
- Discover their Alexa skill through the app.
- Initiate skill enablement and account linking from within the app.
- Link their account without entering Alexa account credentials if already logged into Alexa app. They will have to login to Rainmaker once, when trying to link accounts.
- Link their account from your RainMaker using [Login with Amazon (LWA)](https://developer.amazon.com/docs/login-with-amazon/documentation-overview.html), when the Alexa app isn't installed on their device.

### Command Response

Command Response allows users to send commands to nodes and receive responses back asynchronously. This provides a more robust way of communicating with nodes and also allows nodes to provide access control based on primary/secondary role.
More information about this can be found [here](https://rainmaker.espressif.com/docs/cmd-resp)

This feature is optional and disabled by default.
Add `isCommandResponseSupported=true` in `local.properties` file to enable this feature.

## Matter support

### What is Matter?

Matter is a unifying standard that provides reliable, secure connectivity across smart home devices. It is being developed by Matter Working Group within the Connectivity Standards Alliance (CSA) as a new, royalty-free connectivity standard to increase compatibility among smart home products, with security as a fundamental design tenet.
The project is built around a shared belief that smart home devices should be secure, reliable, and seamless to use. By building upon Internet Protocol (IP), the project aims to enable communication across smart home devices, mobile apps, and cloud services and to define a specific set of IP-based networking technologies for device certification.

### Capabilities

- Commission matter only and matter+rainmaker devices to custom fabric.
- Control matter & matter+rainmaker devices locally using Matter clusters.
- Control matter+rainmaker devices remotely using the Rainmaker cloud.
- Control matter-only devices remotely via controller.
- Commission matter device using thread networks. 

### Build app for Matter fabric

Matter support is optional and disabled by default.
To build app with Matter support do the following :

- Add `isMatterSupported=true` in `local.properties` file.
- Enable `matterBuild` variable to `true` in `app/build.gradle` to add matter libs in app.

### Add your cluster support in app for Matter device

Assumption here is that you are already aware of the concept of clusters in matter terminology. For details you can refer to [here](
https://blog.espressif.com/matter-clusters-attributes-commands-82b8ec1640a0).

You can add support for your own custom cluster(s) in addition to the existing supported  clusters in your Android Application.
A few additional steps are required to add this support.

[Here](Matter_CustomCluster.md) is a guide to add support for custom cluster.

## Additional Settings:

Settings associated with provisioning a device can be modified in the `local.properties` file.

Add below lines in `local.properties` and customize as per your requirement.
 
 ```
 transport=Both
 security=2
 POP=abcd1234
 deviceNamePrefix=PROV_
 isFilterPrefixEditable=true
 isQRCodeSupported=true
 isScheduleSupported=true
 isSceneSupported=true
 isLocalControlSupported=true
 isNodeGroupingSupported=true
 isNodeSharingSupported=true
 ```  

Description of each key can be found below.

| Key                    	| Type    	| Description                                                                                                                                                                                                     	 |
|------------------------	|---------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| transport              	| String  	| Possible values:   <br> **Both** (Default) : Supports both BLE and SoftAP device provisioning. <br> **SoftAP** : supports only SoftAP device provisioning. <br> **BLE**: supports only BLE device provisioning. 	 |
| security               	| int 	   | It can be 0, 1 or 2 int value. (Default value is 2).  <br> **0**: for unencrypted communication between device and app. <br> **1/2** : for secure/encrypted communication between device and app.                   |
| POP                    	| String  	| Proof of Possession. It's default value is **empty string**.                                                                                                                                                    	 |
| deviceNamePrefix       	| String  	| Search for BLE devices with this prefix in scanning. It's default value is "**PROV_**".                                                                                                                         	 |
| isFilterPrefixEditable 	| boolean 	| Allow users to edit the prefix used for filtering BLE devices. It's default value is **true**.                                                                                                                  	 |
| isQRCodeSupported      	| boolean 	| Allow users to connect with the device and start provisioning using QR code which has device information. It's default value is **true**.                                                                       	 |


## Supports

- Supports Android 8.1 (API level 27) and above.  

## License

  

    Copyright 2020 Espressif Systems (Shanghai) PTE LTD  
     
    Licensed under the Apache License, Version 2.0 (the "License");  
    you may not use this file except in compliance with the License.  
    You may obtain a copy of the License at  
     
        http://www.apache.org/licenses/LICENSE-2.0  
     
    Unless required by applicable law or agreed to in writing, software  
    distributed under the License is distributed on an "AS IS" BASIS,  
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
    See the License for the specific language governing permissions and  
    limitations under the License.
