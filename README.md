# Cyclino
DIY NFC temperature Logger/thermometer out of TI's Rf430frl153h IC soldered onto a worn out "Freestyle Libre"/"Freestyle Libre pro Sensor" from Abbott laboratories. Alternatively, TI's NFC/RFID Temperature Sensing Patch with attatched battery can be used.
The pin configuration of Abbott's ASIC varies from the Rf430frl153h. Therefore, two traces on the PCB need to be cut, a resistor has to be taken off, and only the ICs internal temperature sensor is used. Therefore, (at least) a 1-Point calibration is necessary. The Calibration value is written to memory.

Thanks to Dr. Andreas Heertsch and Pavan Shetty for their preworks!
