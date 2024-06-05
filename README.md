# Spider
## Known issues in Version 1
- I used a hard-coded world id. The plugin expects a world folder named `world` and crashes if it is not present.

## Installation
1. Download the JAR from the [releases page](https://github.com/TheCymaera/minecraft-spider/releases/).
2. Set up a [Paper](https://papermc.io/downloads) or [Spigot](https://getbukkit.org/download/spigot) server. (Instructions below)
3. Add the JAR to the `plugins` folder.
4. Download the world folder from [Planet Minecraft](https://www.planetminecraft.com/project/spider-garden/).
5. Place the world folder in the server directory. Name it `world`.

## Running a Server
1. Download a 1.20.4 server JAR from [Paper](https://papermc.io/downloads) or [Spigot](https://getbukkit.org/download/spigot).
2. Run the following command `java -Xmx1024M -Xms1024M -jar server.jar nogui`.
3. I typically use the Java runtime bundled with my Minecraft installation so as to avoid version conflicts.
   - Windows: `C:\Users\%USERNAME%\AppData\Local\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma\bin\javaw.exe`
   - MacOS: `~/Library/Application\ Support/minecraft/runtime/java-runtime-gamma/mac-os/java-runtime-gamma/jre.bundle/Contents/Home/bin/java`
   - In Modrinth, you can find the Java runtime location inside the profile options menu.
4. Accept the EULA by changing `eula=false` to `eula=true` in the `eula.txt` file.
5. Join the server with `localhost` as the IP address.


## Commands
Change gait. Autocomplete will show available options.
```
/gait <option:string> <value:double>
```

Change body plan (Version 2 Beta only). Autocomplete will show available options. 

```
/body_plan <plan:string>
```

## Development
1. Clone or download the repo.
2. Run Maven `package` to build the plugin. The resulting JAR will be in the `target` folder.
3. For convenience, set up a symlink and add the link to the server `plugins` folder.
   - Windows: mklink /D newFile.jar originalFile.jar
   - Mac/Linux: ln -s originalFile.jar newFile.jar 

## License
You may use the plugin and source code for both commercial or non-commercial purposes.

Attribution is appreciated but not due.

Do not resell.