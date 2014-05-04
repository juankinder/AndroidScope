AndroidScope
============

A open source scope for Android, with Microchip PIC or Arduino compatible hardware. 

![Screenshot](https://raw.githubusercontent.com/juankinder/AndroidScope/master/Documentation/img/Screenshot_1.png)


* Multi-touch support to modify visualization parameters.
* Ability to pause signal acquisition.
* Real-time FFT using [JTransforms](https://sites.google.com/site/piotrwendykier/software/jtransforms) library:

![Screenshot FFT](https://raw.githubusercontent.com/juankinder/AndroidScope/master/Documentation/img/Screenshot_FFT.png)



Signal sources:
* Internal generator
* Microphone
* PIC based hardware
* Arduino based hardware


Software
----
Software is available at [Google Play](https://play.google.com/store/apps/details?id=com.devs.android.scope) for any Android 4.0.3 device or above with USB host module enabled.

Was tested on the following devices:
* Nexus 7 (2012)
* Samsung Galaxy Note II
* Samsung Galaxy S4
* Sony Xperia Z
* Android TV dongle MK919


Hardware
----

Hardware can be divided in two sections:
* analog and signal adapter interface;
* ADC and microcontroller.

Two different microcontrollers can be used, one based on a Microchip PIC 18F or an Arduino Nano (Atmega328). Integrated ADC is used to convert analog signals, but a external and more powerfull ADC could be used to achive higher sample rates.


Signal adapter schematic
---

Thanks to this interface, Androidscope can measure:

* AC/DC signals
* Frecuency between 10Hz and 20kHz
* Max amplitude: 40 Volts peak to peak
* Protection: 100 Volts peak to peak

![Analog input](https://raw.githubusercontent.com/juankinder/AndroidScope/master/Documentation/img/Schematic_Analog_input.png)

Frecuency response was measured on the Lab, and can be improved tweaking capacitor values:

![Frecuency response](https://raw.githubusercontent.com/juankinder/AndroidScope/master/Documentation/img/Frecuency_response.png)




PIC board schematic
---

![PIC](https://raw.githubusercontent.com/juankinder/AndroidScope/master/Documentation/img/Schematic_PIC.png)



This is a comparision between an oscilloscope and Androidscope using the Arduino based board, created on the Lab measuring a 1kHz, 20 volts square signal:

![Scope comparison](https://raw.githubusercontent.com/juankinder/AndroidScope/master/Documentation/img/Lab.jpg)

