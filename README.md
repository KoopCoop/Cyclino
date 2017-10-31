# Cyclino
DIY high resolution NFC temperature Logger/thermometer out of TI's Rf430frl153h IC soldered onto a wornout "Freestyle Libre"/"Freestyle Libre pro Sensor" from Abbott laboratories. Alternatively, TI's NFC/RFID Temperature Sensing Patch with attatched battery can be used.
The pin configuration of Abbott's ASIC varies from the Rf430frl153h. Therefore, only the internal temperature sensor of the IC is used. The internal sensor requires (at least) a 1-Point calibration, which is also handled by the app (calibration value is written to IC's memory).

Thanks to Dr. Andreas Heertsch and Pavan Shetty for their preworks!
