package invasivezombies;

import net.minecraft.util.Identifier;

public class VersionHelper {
	
	    public static Identifier CustomIdentifier(String id) {
        String namespace = "minecraft"; // default namespace
        String path = id;

        // If the id contains a ":", split it into namespace and path
        if (id.contains(":")) {
            String[] parts = id.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }

        // Use tryParse method to create an Identifier
        return Identifier.tryParse(namespace + ":" + path);
    }
  
}