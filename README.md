## ESP RainMaker Android App

This is the official Android app for [ESP RainMaker](https://github.com/espressif/esp-rainmaker), an end-to-end solution offered by Espressif to enable remote control and monitoring for ESP32-S2 and ESP32 based products without any configuration required in the Cloud. 

For more details :
 - Please check the ESP RainMaker documentation [here](http://rainmaker.espressif.com/docs/get-started.html) to get started.
 - Try out this app in [Play Store](https://play.google.com/store/apps/details?id=com.espressif.rainmaker).

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
