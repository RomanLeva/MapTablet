# MapTablet
Map tablet application for paintball game, emergency services and even artillery or mortar. The main idea that users can
create theirs teams and watch synchronously theirs positions, exchange points, lines, arrows, commands and so on. 
A commander can simply pin a target point on the map, application will select the nearest unit from your team and calculate for him coordinates, direction and distance to that target. Also, commander can select units and send them to any mark point on the map (like in StarCraft),
and so, for units there will apear a mark point and arrow. 
JavaFX platform was choosed for offline working and connecting to some equipment by USB. Application uses auto cached or downloaded OpenStreetMap tiles. All messages are encrypted.
Application uses localhost and port 8007. Launch one or more apps one machine, push the HEAD button to work as Head, and push the SPOT button for connecting MapTablet to work as Spotter. Normal networking will be implemented some later.
Tile rendering by Gluonmaps open source project, all the other functionallity developed by me.
