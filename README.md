# MapTablet
Map tablet application for any emergency services like Fire Resque, Police and even Artillery / Mortar. The main idea that users can
create theirs teams and watch synchronously theirs positions, exchange points, lines, arrows, commands and so on. 
A commander can simply pin a target point on the map, application will select the nearest unit from your team and calculate for him coordinates, direction and distance to that target. Also, commander can select units and send them to any mark point on the map (like in StarCraft),
and so, for units there will apear a mark point and arrow. 
JavaFX platform was choosed for offline working and connecting to some equipment by USB. Application uses auto cached or downloaded OpenStreetMap tiles. All messages are encrypted.
<<<<Use it in pair with OSM-based-app from my repository, it uses localhost and port 8007. Push the CONN button for connecting MapTablet to the working HeadQuarters application.>>>>
Tile representation by Gluonmaps open source project, all the other functionallity as points, loading tiles, calculations direction and coordinates developed by me.
