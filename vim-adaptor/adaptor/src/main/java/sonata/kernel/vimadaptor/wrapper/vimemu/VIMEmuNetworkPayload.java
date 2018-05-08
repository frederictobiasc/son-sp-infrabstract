package sonata.kernel.vimadaptor.wrapper.vimemu;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VIMEmuNetworkPayload {
    /* vnf_src_name: VNF name of the source of the link
vnf_dst_name: VNF name of the destination of the link
vnf_src_interface: VNF interface name of the source of the link
vnf_dst_interface: VNF interface name of the destination of the li*/

    private String sourceVnfName;
    private String sourceVnfInterface;
    @JsonProperty("vnf_src_name")
    private String sourceVduId;
    @JsonProperty("vnf_src_interface")
    private String sourceVduInterface;
    private String destinationVnfName;
    private String destinationVnfInterface;
    @JsonProperty("vnf_dst_name")
    private String destinationVduId;
    @JsonProperty("vnf_dst_interface")
    private String destinationVduInterface;
    @JsonProperty("weight")
    private int weight;
    @JsonProperty("match")
    private String match;
    @JsonProperty("bidirectional")
    private boolean bidirectional;
    @JsonProperty("cookie")
    private int cookie;
    @JsonProperty("priority")
    private int priority;
    @JsonProperty("skip_vlan_tag")
    private boolean skip_vlan_tag;
    @JsonProperty("monitor")
    private boolean monitor;
    @JsonProperty("monitor placement")
    private String monitor_placement;

    public String getSourceVnfName() {
        return sourceVnfName;
    }

    public void setSourceVnfName(String sourceVnfName) {
        this.sourceVnfName = sourceVnfName;
    }

    public String getSourceVduId() {
        return sourceVduId;
    }

    public void setSourceVduId(String sourceVduId) {
        this.sourceVduId = sourceVduId;
    }

    public String getSourceVnfInterface() {
        return sourceVnfInterface;
    }

    public void setSourceVnfInterface(String sourceVnfInterface) {
        this.sourceVnfInterface = sourceVnfInterface;
    }

    public String getDestinationVnfName() {
        return destinationVnfName;
    }

    public void setDestinationVnfName(String destinationVnfName) {
        this.destinationVnfName = destinationVnfName;
    }

    public String getDestinationVduId() {
        return destinationVduId;
    }

    public void setDestinationVduId(String destinationVduId) {
        this.destinationVduId = destinationVduId;
    }

    public String getDestinationVnfInterface() {
        return destinationVnfInterface;
    }

    public void setDestinationVnfInterface(String destinationVnfInterface) {
        this.destinationVnfInterface = destinationVnfInterface;
    }

    public String getSourceVduInterface() {
        return sourceVduInterface;
    }

    public void setSourceVduInterface(String sourceVduInterface) {
        this.sourceVduInterface = sourceVduInterface;
    }

    public String getDestinationVduInterface() {
        return destinationVduInterface;
    }

    public void setDestinationVduInterface(String destinationVduInterface) {
        this.destinationVduInterface = destinationVduInterface;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
    }

    public int getCookie() {
        return cookie;
    }

    public void setCookie(int cookie) {
        this.cookie = cookie;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isSkip_vlan_tag() {
        return skip_vlan_tag;
    }

    public void setSkip_vlan_tag(boolean skip_vlan_tag) {
        this.skip_vlan_tag = skip_vlan_tag;
    }

    public boolean isMonitor() {
        return monitor;
    }

    public void setMonitor(boolean monitor) {
        this.monitor = monitor;
    }

    public String getMonitor_placement() {
        return monitor_placement;
    }

    public void setMonitor_placement(String monitor_placement) {
        this.monitor_placement = monitor_placement;
    }
}
