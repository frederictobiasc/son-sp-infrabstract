package sonata.kernel.vimadaptor.wrapper.vimemu;

import java.util.List;
import java.util.zip.CRC32;

public class Common {
    static String translateToVimVduId(String vnfName, String vduId){
        return vnfName +"_"+ vduId;
    }

    /**
     * Caution: generated identifiers are not necessarily unique.
     * @param vimVduId
     * @param vduInterface
     * @return identifier for the virtual network adapter
     */
    static String translateToVimVduNetworkInterface(String vimVduId, String vduInterface){
        CRC32 crc = new CRC32();
        crc.update((vimVduId+vduInterface).getBytes());
        return Long.toHexString(crc.getValue());
    }

    /**
     * @param vimVduId must contain the vimVduId that were originally passed to {@link #translateToVimVduNetworkInterface(String, String)}
     * @param connectionPoints must contain the connectionPoints which where originally passed to
     * {@link #translateToVimVduNetworkInterface(String, String)}
     * @param vimVduNetworkInterface is an identifier build by
     * {@link #translateToVimVduNetworkInterface(String, String)}
     * @return Original connectionPoint that was hashed to vimVduNetworkInterface
     */
    static String mapVimVduNetworkInterfaceToConnectionPoint(String vimVduId, List<String> connectionPoints, String vimVduNetworkInterface){
        for(String connectionPoint:connectionPoints){
            if(translateToVimVduNetworkInterface(vimVduId, connectionPoint).equals(vimVduNetworkInterface)){
                return connectionPoint;
            }
        }
        throw new IllegalArgumentException("Since connectionPoints of a vdu are hashed to vimVduId, here should be at least one match.");
    }
}
