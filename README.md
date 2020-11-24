## ESP RainMaker Android App

This is the official Android app for [ESP RainMaker](https://github.com/espressif/esp-rainmaker), an end-to-end solution offered by Espressif to enable remote control and monitoring for ESP32-S2 and ESP32 based products without any configuration required in the Cloud. 

For more details :
 - Please check the ESP RainMaker documentation [here](http://rainmaker.espressif.com/docs/get-started.html) to get started.
 - Try out this app in [Play Store](https://play.google.com/store/apps/details?id=com.espressif.rainmaker).
 
## Setup
 
To build this app, you will need a development machine, with Android Studio installed.
App will also require Amazon Cognito user pool details.
Following are the configuration fields available in `app/build.gradle`.

  - `aws_region` - AWS Cognito user pool region. Default value is "us-east-1" if this field is empty or not configured.
  - `user_pool_id` - Your user pool id.
  - `client_id` - Your app client id.

After configuring these fields, you are now ready to run this demo.


## Features

### User Management

- Signup/Signin using email id.
- Third party login includes Apple login, GitHub and Google.
- Forgot/reset password support.
- Signing out.

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

This feature is optional but enabled by default.
This feature can be enabled/disabled by setting true/false value of `isLocalControlSupported` field available in `app/build.gradle`.

### Scheduling

Schedules allow you to automate a device by setting it to trigger an action at a given time on a specified day or days of the week.
List of operations that are supported for scheduling :
 
 - Add.
 - Edit.
 - Remove.
 - Enable/disable.

Schedule feature is optional but enabled by default.
Schedule can be enabled/disabled by setting true/false value of `isScheduleSupported` field available in `app/build.gradle`.

## Supports

- Supports Android 7.0 (API level 24) and above.  

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
