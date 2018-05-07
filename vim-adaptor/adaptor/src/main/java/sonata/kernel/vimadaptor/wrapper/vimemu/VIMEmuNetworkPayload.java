package sonata.kernel.vimadaptor.wrapper.vimemu;

public class VIMEmuNetworkPayload {
    /* vnf_src_name: VNF name of the source of the link
vnf_dst_name: VNF name of the destination of the link
vnf_src_interface: VNF interface name of the source of the link
vnf_dst_interface: VNF interface name of the destination of the li*/
    private String sourceVnfName;
    private String sourceVnfInterface;
    private String sourceVduId;
    private String sourceVduInterface;
    private String destinationVnfName;
    private String destinationVnfInterface;
    private String destinationVduId;
    private String destinationVduInterface;


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
}
