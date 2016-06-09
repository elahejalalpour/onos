package org.onosproject.vpls;

import com.fasterxml.jackson.databind.JsonNode;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

/**
 * Created by elahe on 6/9/2016 AD.
 */
public class TaggingConfig extends Config<ApplicationId> {
    public VplsTag tagging() {
        JsonNode tagging = object.get("tagging");
        if (tagging == null) {
            return VplsTag.VLAN;
        }

        if (tagging.asText().equals("mpls")) {
            return VplsTag.MPLS;
        } else if (tagging.asText().equals("vlan")) {
            return VplsTag.VLAN;
        } else {
            return VplsTag.VLAN;
        }
    }
}
